// Routing:
// Hardware inputs > input monitor synths / decoder synth > patcher synths (re/routing) > xover synth (global gain) / output monitor synths

// TODO:  check kernel size and fft size match
//        check the routing/decoding/crossing over of subs
// make sure convolutions start syncronously

SoundLab {
	// copyArgs
	var <initSR, <loadGUI, <controllerIP, <usingSLHW, <>usingKernels;
	var <decoderLib, <synthLib, <>gAmp, <isMuted, <isAttenuated, <hwInStart, <hwInCount, <hwOutCount;
	var <server, <slhw, <defaultDecName, <decoderActive, <stereoActive;
	// busses
	var <decoderOutBus, <patcherOutBus;
	// nodes
	var <monitorGroup_ins, <monitorGroup_outs, <patcherGroup, <correctionGroup, <convGroup;
	var <monitorSynths_ins, <monitorSynths_outs,
	<satPatcherSynths,<subPatcherSynths, <stereoPatcherSynths,
	<curDecoder, <convSynth, <delCompSynth, <gainCompSynth, <masterAmpSynth; //<convSynths
	// other
	var <clipListener, <reloadGUIListener, <gui, <numSatChans, <numSubChans, <numKernelChans;
	var <fftSize, <stateLoaded, <clipMonitoring;
	var <decAttributeList, <decAttributes, rbtTryCnt, <loadCond;
	var <kernelsDirPath, <kernelDict, <curKernel, <>defaultKernel, <decInfo;
	var <jconvolver;

	// dimensions
	var <spkrAzims, <spkrElevs, <spkrDists, <spkrDirs, <spkrDels, <spkrGains, <spkrOppDict; //<spkrAmps,
	var <delDict, <gainDict, <distDict;
	// var <subAzims, <subElevs, <subDists, <subDirs, <subDels, <subGains;  // <subAmps,

	var <>debug = true;

	*new { |initSR=96000, loadGUI=true, controllerIP, useSLHW=true, useKernels=true|
		^super.newCopyArgs(initSR, loadGUI, controllerIP, useSLHW, useKernels).init;
	}

	init {
		// defaults
		gAmp = 0.dbamp;
		defaultDecName = \Sphere_24ch_First_dual;  // synthDef name
		stereoActive = false;
		isMuted = false;
		isAttenuated = false;
		stateLoaded = false;
		clipMonitoring = false;
		defaultKernel = \decor_700;
		numSatChans = 24;
		numSubChans = 4;
		numKernelChans = numSatChans+numSubChans;
		fftSize = 2**11; // 2048 - recommended

		/* gain comp in synth or in kernel variable */
		kernelsDirPath = PathName.new(Platform.resourceDir ++ "/sounds/SoundLabKernelsNew/");
		kernelDict = IdentityDictionary.new(know: true);
		// kernelSynthDict = IdentityDictionary.new(know: true);

		this.initRigDimensions;
		this.initDecoderAttributes;

		rbtTryCnt = 0;
		loadCond = Condition(false);

		clipListener = OSCFunc({
			|msg, time, addr, recvPort|
			("CLIPPED channel" ++ msg[3]).postln;
			this.changed(\clipped, msg[3]);
			}, '/clip', nil
		);

		reloadGUIListener = OSCFunc({
			|msg, time, addr, recvPort|
			"reloading gui".postln;
			this.buildGUI;
			}, '/reloadGUI', nil
		);

		if(usingSLHW,
			{ this.prInitSLHW(initSR)},
			{ this.prInitDefaultHW(initSR) }
		);
	}

	prLoadServerSide { |argServer|
		debug.if{postln("Loading Server Side ... loading synths, intializing channel counts, groups, and busses.")};
		server = argServer ??  {"server defaulting because none provided".warn; Server.default};
		server.doWhenBooted({
			{
				// "waiting 5 seconds for hardware".postln; 5.wait;
				1.wait;

				if( server.sampleRate == 44100, {
					"Note: kernel room correction only available at 48k and 96k".postln;
					this.setNoKernel;
				});

				("using kernels?" + usingKernels).postln;

				// see extSoundLab_synths.sc
				this.loadDelDistGain( if(usingKernels, {curKernel ?? defaultKernel},{nil}) );
				this.loadSynths;
				loadCond.wait;  // waiting on loadSynths to signal
				loadCond.test_(false).signal; // reset the condition to hang when needed later

				if(usingKernels, {
					curKernel = curKernel ?? defaultKernel;
					this.loadKernel(curKernel, loadCond)
				},{ loadCond.test_(true).signal });
				loadCond.wait;
				loadCond.test_(false).signal; // reset the condition to hang when needed later
				"passed loading kernels".postln;

				jconvolver !? { jconvolver.free };
				// jconvolver = new_jconvolver;

				hwInStart = server.options.numOutputBusChannels;
				hwInCount = server.options.numInputBusChannels;// used for monitoring
				hwOutCount = server.options.numOutputBusChannels;// used for monitoring

				// group for clip monitoring synths
				monitorGroup_ins = CtkGroup.new( addAction: \head, target: 1);
				monitorGroup_outs = CtkGroup.new( addAction: \tail, target: 1 );
				// group for patching synths
				patcherGroup = CtkGroup.new( addAction: \after, target: monitorGroup_ins );
				// group for amp and delay compensation and drc/decorrelation
				correctionGroup = CtkGroup.new( addAction: \after, target: patcherGroup );
				convGroup = CtkGroup.new( addAction: \head, target: correctionGroup );

				patcherOutBus = CtkAudio.new(numSatChans+numSubChans+2); // +2 = stereo chans
				decoderOutBus = CtkAudio.new(numSatChans+numSubChans);

				/* proper NODE TREE order
				monitorGroup_ins
				decoder (ambi, thru, etc.)
				patcherGroup
				correctionGroup
				>convGroup (drc/decorrelation synths)
				>gainCompSynth
				>delCompSynth
				masterAmpSynth
				monitorGroup_outs
				*/

				this.loadState;
			}.fork
		});
	}

	loadState  {
		var decoder_name;
		debug.if{postln("loading SoundLab state")};
		fork {
			patcherOutBus.play;
			decoderOutBus.play;
			monitorGroup_ins.play;
			monitorGroup_outs.play; 0.15.wait;
			patcherGroup.play; 0.15.wait;
			correctionGroup.play; 0.15.wait;
			convGroup.play; 0.15.wait;

			// patcherOutBus is only satellites + stereo, NO subs
			// outputs don't change on the patcher synths, only the inputs
			// i.e. there's a patcher synth "attached" to each speaker output
			satPatcherSynths = numSatChans.collect({ |i|
				synthLib[\patcher].note(
					target: patcherGroup)
				.in_bus_(decoderOutBus.bus+i).out_bus_(patcherOutBus.bus+i).play
			});
			subPatcherSynths = numSubChans.collect({ |i|
				synthLib[\patcher].note(
					target:patcherGroup)
				.in_bus_(decoderOutBus.bus+numSatChans+i)
				.out_bus_(patcherOutBus.bus+numSatChans+i).play
			});
			stereoPatcherSynths = 2.collect({|i|
				synthLib[\patcher].note(
					target: patcherGroup)
				.in_bus_(hwInStart+i)
				.out_bus_(patcherOutBus.bus+numSatChans+numSubChans+i).play
			});

			if( usingKernels, {
				if( kernelDict[curKernel].notNil, {
					// TODO: update any kernel-specific routing

					/*// includes sats + subs
					convSynth = kernelSynthDict[curKernel].note(0.2, target: convGroup)
					.in_bus_(patcherOutBus.bus)
					.out_bus_(patcherOutBus.bus) // uses ReplaceOut
					.play;*/
				},{"no current kernel specified or mismatch between curKernel name and key, conv synths not created".warn});
			});
			0.1.wait;
			this.startDelGainSynth;
			0.3.wait;

			masterAmpSynth = synthLib[\masterAmp].note(
				addAction: \after, target: correctionGroup)
			.in_bus_(patcherOutBus.bus).out_bus_(0)
			.masterAmp_(gAmp)
			.play;
			0.1.wait;

			// init monitors, not played yet
			monitorSynths_ins = hwInCount.collect{ |i|
				synthLib[\clipMonitor].note(
					target: monitorGroup_ins)
				.in_bus_(hwInStart + i)
			};
			monitorSynths_outs = hwOutCount.collect{ |i|
				// note: in_bus index is a hardware out channel
				synthLib[\clipMonitor].note(target: monitorGroup_outs).in_bus_(i)
			};
			0.1.wait;

			if(stereoActive.not, {stereoPatcherSynths.do(_.pause)});

			// decoder
			this.startDecoder( if(curDecoder.notNil, {curDecoder.synthdefname},{defaultDecName}) );

			if(clipMonitoring, {this.clipMonitor_(true)});

			stateLoaded = true;
			this.changed(\stateLoaded);
			if(loadGUI, {this.buildGUI});

		};
	}

	startDelGainSynth {
		"starting del and gain synths".postln;
		fork {
			gainCompSynth = synthLib[\gain_comp].note(addAction: \tail, target: correctionGroup)
			.in_bus_(patcherOutBus.bus)
			.out_bus_(patcherOutBus.bus) // uses ReplaceOut
			.play;
			server.sync;

			delCompSynth = synthLib[\delay_comp].note(addAction: \after, target: gainCompSynth)
			.in_bus_(patcherOutBus.bus)
			.out_bus_(patcherOutBus.bus) // uses ReplaceOut
			.play;
		}
	}

	// newKernel should match the folder name holding the kernels
	switchKernel { |newKernel|
		var loadCond, test;
		block {|break|
			kernelDict[newKernel] !? { "This kernel is already loaded".postln; break.(true) };
			fork {
				debug.if{postf("Switching to kernel: %\n", newKernel)};
				loadCond = Condition(false);
				this.loadKernel(newKernel, loadCond);
				loadCond.wait;
				loadCond.test_(false).signal; // reset the condition to hang when needed later
				"passed kernel loading condition".postln;

				// test that kernel bufs were loaded and conv synth defined successfully
				if (kernelDict[newKernel].notNil, {
					this.loadDelDistGain(newKernel);
					"del, dist, gains loaded".postln;
					this.loadSynths;
					loadCond.wait;
					// TODO: consider server.sync here
					"synths loaded".postln;

					// free all the synth whose values are affected by the new kernel
					curDecoder.release;
					curDecoder.fadeTime.wait;
					[gainCompSynth, delCompSynth].do(_.free);

					// TODO: stop old kernel and start new one


					// kernelDict[curKernel].do(_.free);
					kernelDict.removeAt(curKernel);
					// kernelSynthDict.removeAt(curKernel);
					curKernel = newKernel;

					jconvolver !? { jconvolver.free };	// free the old jconvolver
					// jconvolver = new_jconvolver;		// update jconvolver var

					debug.if{postln("Completed switch to kernel: " ++ curKernel)};
					this.changed(\kernel, curKernel);
					usingKernels = true;

					// restart delays, gains, and decoder now with updated
					// del, gain and dist values to match the kernel
					this.startDelGainSynth;
					server.sync;
					this.startDecoder( if(curDecoder.notNil, {curDecoder.synthdefname},{defaultDecName}) );
					},{ "no new kernels loaded".postln; }
				);
			}
		}
	}

	stopKernel {
		fork {
			// free all the synth whose values are affected by the new kernel
			// todo: start up new default routing before releasing current signal path synths

			curDecoder.release;
			curDecoder.fadeTime.wait;
			[gainCompSynth, delCompSynth].do(_.free);

			curKernel !? {
				// "freeing kernel data".postln;
				// kernelDict[curKernel].do(_.free);
				kernelDict.removeAt(curKernel);
				// kernelSynthDict.removeAt(curKernel);
			};

			this.loadDelDistGain(\default);
			this.loadSynths;
			server.sync; // todo: will this wait for forked process in loadSynths?

			// restart delays, gains, and decoder now with updated
			// del, gain and dist values to match the kernel
			this.startDelGainSynth;
			server.sync;
			this.startDecoder( if(curDecoder.notNil, {curDecoder.synthdefname},{defaultDecName}) );
		}
	}

	useKernel_ {|bool, kernelName|
		if( bool,
			{ if(usingKernels.not, { this.switchKernel((kernelName ?? defaultKernel).asSymbol) }) },
			{ if(curKernel.notNil, { this.stopKernel }) }
		)
	}

	setNoKernel {
		curKernel = \no_correction; //nil;
		usingKernels = false;
		this.changed(\kernel, curKernel);
	}

	startDecoder  { |newDecSynthName, finishCondition|
		var dInBusNum, result;
		dInBusNum = if( stereoActive, {hwInStart+2}, {hwInStart});
		this.changed(\reportStatus, "about to start final decoder");
		debug.if{
			postln("starting decoder: " ++ newDecSynthName ++ "; " ++
				if(stereoActive, {"WITH "},{"WITHOUT "}) ++
				"stereo channels, reading in on: " ++ dInBusNum
		)};
		curDecoder = decoderLib[newDecSynthName].note(
			addAction: \before, target: patcherGroup)
		.in_bus_(dInBusNum).out_bus_(decoderOutBus.bus).play;

		finishCondition !? {finishCondition.test_(true).signal};

		// update decInfo variable with new decoder attributes
		result = decAttributes.select({|item| item.defname == curDecoder.synthdefname });
		decInfo = result[0];
		debug.if{ postln("Updating decInfo variable with new decoder attributes:\n"++"\t"++decInfo.defname);
				decInfo.keysValuesDo({|k,v|postln("\t\t"++k++" "++v)})
			};

		this.changed(\decoder, decInfo);
	}

	switchDecoder { |newDecInfo, finishCondition|
			debug.if{ postln("Switching to decoder: \n"++"\t"++newDecInfo.defname);
				newDecInfo.keysValuesDo({|k,v|postln("\t\t"++k++" "++v)})
			};
			if( newDecInfo.notNil,
				{ fork {
					this.resetThruRouting; // zero out the routing matrix
					curDecoder.release;
					curDecoder.fadeTime.wait;
					this.startDecoder(newDecInfo.defname, finishCondition);
					}
				},{	"Decoder info not valid".warn }
			)

	}

	getDecoderInfo { |pendingDecType, pendingOrder, pendingK|
		var result, info;
		debug.if{ postln(
			"pendingDecType: " ++ pendingDecType ++
			" pendingOrder: " ++ pendingOrder ++
			" pendingK: " ++ pendingK
		)};

		result = decAttributes.select({ |d|
			(d.decType == pendingDecType) and:
			(d.ord == pendingOrder) and:
			(d.k == pendingK)
		});
		info = case
		{result.size==1} {result[0]}
		{result.size==0} {"No decoder found of that type and order".warn; nil}
		{result.size>1}  {
			"Multiple decoders found of that type and order. Check that the
			decoder's attributes are unique.".warn; nil;
		};

		debug.if{
			info.notNil.if{
				postln("getDecoder query result: \n" ++
					"\t" ++ info.defname);
				info.keysValuesDo({|k,v|postln("\t\t"++k++" "++v)})
			}
		};
		^info;
	}


	sampleRate_ { |newSR|
		this.prClearServerSide;
		if(usingSLHW,
			{ slhw.startAudio(newSR) },{ this.prInitDefaultHW(newSR) }
		)
	}

	sampleRate { if(usingSLHW, {^slhw.server.sampleRate}, {^server.sampleRate}) }

	mute  { |bool = true|
		if(bool,
			{
				masterAmpSynth.masterAmp_(0); //patcherGroup.set( 0.0, \gain, 0 );
				("amp set to " ++ 0).postln;
				isMuted = true;
				this.changed(\mute, 1);
			},{
				isMuted = false; // must set before for this.attenuate isMuted check to work
				this.changed(\mute, 0);
				if( isAttenuated,
					{this.attenuate},
					{
						masterAmpSynth.masterAmp_(gAmp); //patcherGroup.set( 0.0, \gain, gAmp );
						("amp set to " ++ gAmp.ampdb).postln;
					}
				);
			}
		)
	}

	attenuate  { | bool = true, att_dB = -30|
		if(bool,
			{
				if(isMuted.not, {
					masterAmpSynth.masterAmp_(att_dB.dbamp); //patcherGroup.set( 0.0, \gain, att_dB.dbamp );
					("amp set to " ++ att_dB).postln;
				});
				isAttenuated = true;
				this.changed(\attenuate, 1);
			},{
				if(isMuted.not, {
					masterAmpSynth.masterAmp_(gAmp); //patcherGroup.set( 0.0, \gain, gAmp );
					("amp set to " ++ gAmp.ampdb).postln;
				});
				isAttenuated = false;
				this.changed(\attenuate, 0);
			}
		)
	}

	amp_ { |amp_dB|
		var ampnorm;
		ampnorm = amp_dB.dbamp;
		if( amp_dB.notNil,
			{   // only update amp if not muted or att
				if( isAttenuated.not && isMuted.not, {
					masterAmpSynth.masterAmp_(ampnorm);
					("amp set to " ++ ampnorm.ampdb).postln;
			});
				gAmp = ampnorm; // normalized, not dB
				this.changed(\amp, gAmp);
			};
		);
	}

	resetThruRouting {
		// NOTE: it's expected that only satellites will be re-mapped, not subs
		// clear any extra patchers added from the routing matrix
		if( satPatcherSynths.size > numSatChans, {
			(satPatcherSynths.size - numSatChans).do({
				satPatcherSynths.last.free;
				satPatcherSynths.removeAt(satPatcherSynths.size-1);
			});
		});
		// reset ins and outs to default
		satPatcherSynths.do({|synth, i| synth.in_bus_( decoderOutBus.bus+i ).out_bus_(patcherOutBus.bus+i)});
	}

	stereoRouting_ { |bool|
		block({ |break|
			if( stereoActive == bool, { break.("stereo setting already current".warn) });
			if( bool,
				{
					debug.if{postln("enabling stereo from stereoRouting")};
					curDecoder.in_bus_(hwInStart+2);
					stereoPatcherSynths.do(_.run);
					stereoActive = true;
				},{
					debug.if{postln("removing stereo from stereoRouting")};
					curDecoder.in_bus_(hwInStart);
					stereoPatcherSynths.do(_.pause);
					stereoActive = false;
			});
			// update crossover sub levels? depends stereo only or includes decoder.
			this.changed(\stereo, bool);
		});
	}

	prInitSLHW { |initSR|
		slhw = SoundLabHardware.new(false); // false to for SC, true for SN
		debug.if{"SLHW initiated".postln};
		slhw.startAudio(initSR, periodSize: 256);
		debug.if{"SLHW audio started".postln};
		slhw.addDependant(this);
	}

	prInitDefaultHW { |initSR|
		{
			server = server ?? Server.default;
			server !? {if(server.serverRunning, {server.quit})};
			"REBOOTING".postln;
			0.5.wait;

			server.options.sampleRate = initSR ?? 96000;
			server.options.memSize = 8192 * 16;
			server.options.numOutputBusChannels_(32).numInputBusChannels_(32);

			// the following will otherwise be called from update: \audioIsRunning
			server.waitForBoot({
				rbtTryCnt = rbtTryCnt+1;
				if( server.sampleRate == initSR, // in case sample rate isn't set correctly the first time (SC bug)
					{
						rbtTryCnt = 0;
						this.prLoadServerSide(server);
					},{
						"reboot sample rate doesn't match requested, retrying...".postln;
						if(rbtTryCnt < 3,
							{ this.prInitDefaultHW(initSR) }, // call self
							{"Error trying to change the sample rate after 3 tries!".warn}
						)
					}
				)
			});
		}.fork
	}

	// expects kernels to be located in kernelsDirPath/sampleRate/kernelType/
	loadKernel { |newKernel, completeCondition|
		var kernelDir_pn, k_path, partSize, k_size, numFoundKernels = 0;
		fork {
			block { |break|
				// does the kernel folder exist?
				kernelsDirPath.folders.do({ |sr_pn|
					if( sr_pn.folderName.asInt == server.sampleRate,
						{	sr_pn.folders.do({ |kernel_pn|
							if( kernel_pn.folderName.asSymbol == newKernel, {
								("found kernel match"+kernel_pn).postln;
								kernelDir_pn = kernel_pn;
							});
							});
					})
				});

				kernelDir_pn ?? {
					this.changed(\reportStatus, "Kernel name not found.".warn);
					break.();
				};

				"Generating jconvolver configuration file...".postln;

				// for osx
				Jconvolver.jackScOutNameDefault = "scsynth:out";
				Jconvolver.executablePath_("/usr/local/bin/jconvolver");

				k_path = kernelDir_pn.absolutePath;
				partSize = if(usingSLHW, {slhw.jackPeriodSize},{512});

				kernelDir_pn.filesDo({ |file|
					if(file.extension == "wav", {
						SoundFile.use(file.absolutePath, {|fl|
							k_size = fl.numFrames;
							numFoundKernels = numFoundKernels + 1;
						})
						}
					)
				});
				postf("path to kernels: % \npartition size: % \nkernel size: %\n",
					k_path, partSize, k_size
				);

				// check that we have enough kernels to match all necessary speakers
				if( numFoundKernels != numKernelChans, {
					"Number of kernels found does not match the numKernelChannels!".warn;
					this.changed(\reportStatus, "Number of kernels found does not match the numKernelChannels!");
					break.();
				});

				// TODO: check if Jconvolver is already running, if so quit it
				Jconvolver.createSimpleConfigFileFromFolder(
					kernelFolderPath: k_path, partitionSize: partSize,
					maxKernelSize: k_size, matchFileName: "*.wav",
					autoConnectToScChannels: 32, autoConnectToSoundcardChannels: 0
				);

				// new_jconvolver = Jconvolver.newFromFolder(k_path);

				// TODO: check that Jconvolver is running, then continue or break

						/*kernelDir_pn.filesDo({ |file, i|
							var kernFile, specKernel;
							file.postln;
							if( (file.extension == "wav") || (file.extension == "aiff"), {
								(" " ++ i.asString).post;
								this.changed(\reportStatus, "Stand by... Loading kernel"+i);
								kernFile = file.fullPath;
								// allocate a padded buffer and compute spectrum
								specKernel = SoundFile.use(kernFile, {arg fl;
									var kernelBuffer, kernelSpectrum;

									// read kernel into zero padded buffer
									kernelBuffer = Buffer.alloc(server, fl.numFrames + fftSize);
									kernelBuffer.read(kernFile, bufStartFrame: fftSize/2);
									server.sync;

									// prepare spectra buffer
									kernelSpectrum = Buffer.alloc(server, PartConv.calcBufSize(fftSize, kernelBuffer));
									server.sync;

									kernelSpectrum.preparePartConv(kernelBuffer, fftSize);
									server.sync;

									kernelBuffer.free; // don't need time domain data anymore

									// return value
									kernelSpectrum;
								});
								spectra_arr = spectra_arr.add( specKernel );
							});
						});
						"\n".post;

						// store new buffers, create new conv synth
						try {
							kernelDict.put( newKernel.asSymbol, spectra_arr);

							"creating new conv synth".postln;
							kernelSynthDict.put( newKernel.asSymbol,

								CtkSynthDef( \conv_++newKernel.asSymbol, { arg in_bus, out_bus = 0, fadeIO = 0.1, gate=1;
									var env, in, out;
									env = EnvGen.kr(Env(times: [fadeIO,fadeIO], releaseNode:1), gate, doneAction:2);
									in = In.ar(in_bus, numKernelChans);
									out = numKernelChans.collect({ arg i;
										PartConv.ar(
											in.at(i),
											fftSize,
											spectra_arr.at(i))
									});
									ReplaceOut.ar(out_bus, out * env);
								});
							)
						}{
							"error preparing new conv synth".warn;
							this.changed(\reportStatus, "Error preparing new conv synth.");
							break.()
						};
						*/
			};
			completeCondition.test_(true).signal;
		}
	}

	buildGUI {
		//start gui only if the instance is nil - this won't unnecessarily recreate the class (which causes problems)
		gui ?? {gui = SoundLabGUI.new( this)};
	}

	prClearServerSide {
		// cleanup server objects to be reloaded after reboot
		[	correctionGroup, patcherGroup, monitorGroup_ins, monitorGroup_outs,
			curDecoder, delCompSynth, masterAmpSynth, gainCompSynth, patcherOutBus, decoderOutBus
		].do(_.free);
		// TODO: can kernelDict be replaced by an array?
		kernelDict !? {
			kernelDict.keysValuesDo({ |key|
				kernelDict.removeAt(key);
			});

			jconvolver !? {jconvolver.free};
			/*kernelDict.keysValuesDo({ |key, bufarr|
				"freeing a kernel buffer".postln;
				bufarr.do(_.free); // free kernels
				kernelDict.removeAt(key);
			});*/
		};
		stateLoaded = false;
	}

	cleanup  {
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide;
		clipListener.free;
		reloadGUIListener.free;
		if ( gui.notNil, {gui.cleanup} );
	}

	// responding to changes in SoundLabHardware
	update {
		| who, what ... args |
		if( who == slhw, {	// we're only paying attention to one thing, but just in case we check to see what it is
			switch ( what,
				\audioIsRunning, {
					switch(args[0],
						true, {
							server = slhw.server ?? {warn("loading default server"); Server.default;};
							if(stateLoaded.not, {this.prLoadServerSide(server)})
						},
						false, { "Audio stopped running in hardware.".postln }
					)
				},
				\stoppingAudio, {
					this.prClearServerSide;
				}
			)
		})
	}

	clipMonitor_{ | bool = true |
		if( bool,
			{ (monitorSynths_ins ++ monitorSynths_outs).do(_.play); clipMonitoring = true; },
			{ (monitorSynths_ins ++ monitorSynths_outs).do(_.free);	clipMonitoring = false; }
		);
	}
}