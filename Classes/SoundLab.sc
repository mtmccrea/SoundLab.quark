SoundLab {
	// defaults set from SETUP.scd
	// classvar <>numHardwareOuts, <>numHardwareIns, <>defaultDecoderName, <>defaultKernel, <>kernelDirPath;

	// copyArgs
	var <initSR, <loadGUI, <usingSLHW, <>usingKernels;

	var <>xfade = 0.2,  <>debug=true;
	var <globalAmp, <numSatChans, <numSubChans, <totalArrayChans, <numKernelChans;
	var <hwInCount, <hwInStart;
	var <config;
	var <numHardwareOuts, <numHardwareIns, <stereoChanIndex, <>defaultDecoderName, <>defaultKernel, <>kernelDirPath;

	var <server, <gui, <curKernel, <stereoActive, <isMuted, <isAttenuated, <stateLoaded;
	var <clipMonitoring, <curDecoderPatch, <nextDecoderPatch, rbtTryCnt;
	var <clipListener, <reloadGUIListener, <clipMonDef, <patcherDef;
	var <patcherGroup, <stereoPatcherSynths, <satPatcherSynths, <subPatcherSynths;
	var <monitorGroup_ins, <monitorGroup_outs, <monitorSynths_outs, <monitorSynths_ins;
	var <jconvolver, <nextjconvolver, <jconvinbus, <nextjconvinbus; //, <nextKernel;

	// SoundLabUtils (SoundLab extension)
	var <compDict, <decAttributes, <decAttributeList;
	var <spkrAzims, <spkrElevs, <spkrDirs, <spkrOppDict, <spkrDels, <spkrGains, <spkrDists;
	var <decoderLib, <synthLib, <loadedDelDistGain;
	var <slhw;

	*new { |initSR=96000, loadGUI=false, useSLHW=true, useKernels=true|
		^super.newCopyArgs(initSR, loadGUI, useSLHW, useKernels).init;
	}

	// NOTE: Jack will create numHarwareOuts * 3 for routing to
	// system hardware (0..numHardwareOuts-1)
	// jconvolver 1 (numHardwareOuts..numHardwareOuts*2-1)
	// jconvolver 2 (numHardwareOuts..numHardwareOuts*3-1)
	// Thus SC will boot with s.option.numOutputBusChannels = numHarwareOuts * 3.
	init {

		File.use( File.realpath(this.class.filenameSymbol).dirname ++ "/CONFIG.scd", "r", { |f|
			config = f.readAllString.interpret;
		});
		// config = thisProcess.interpreter.executeFile(File.realpath(this.class.filenameSymbol).dirname ++ "/CONFIG.scd");
		// defaults
		numHardwareOuts = config.numHardwareOuts;
		numHardwareIns = config.numHardwareIns;
		defaultDecoderName = config.defaultDecoderName;//\Sphere_24ch_first_dual;  // synthDef name
		defaultKernel = config.defaultKernel; //\decor_700;
		stereoChanIndex = config.stereoChanIndex;
		numSatChans = config.numSatChans;
		numSubChans = config.numSubChans;
		totalArrayChans = numSatChans+numSubChans;	// stereo not included
		numKernelChans = totalArrayChans; 	// TODO: confirm this approach

		// kernelDirPath = PathName.new(Platform.resourceDir ++ "/sounds/SoundLabKernelsNew/");
		kernelDirPath = kernelDirPath ?? {
			PathName.new(File.realpath(this.class.filenameSymbol).dirname ++ "/SoundLabKernels/") };

		globalAmp = 0.dbamp;
		stereoActive = stereoActive ?? {true};
		isMuted = false;
		isAttenuated = false;
		stateLoaded = false;
		clipMonitoring = false;

		this.prInitRigDimensions;
		this.prInitDecoderAttributes;

		rbtTryCnt = 0;

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

	// this happens after hardware is intitialized abd server is booted
	prLoadServerSide { |argServer|
		var loadCondition;
		loadCondition = Condition(false);
		// debug
		postln("Loading Server Side ... loading synths, intializing channel counts, groups, and busses.");

		server = argServer ?? {
			"server defaulting because none provided -prLoadServerSide".warn;
			Server.default
		};

		server.doWhenBooted({
			fork {
				"waiting 3 seconds".postln;
				3.wait; // give server time to get its shit together
				// kill any running Jconvolvers
				jconvolver !? {"Stopping a running jconvolver".postln; jconvolver.free};
				nextjconvolver !? {"Stopping a running jconvolver".postln; nextjconvolver.free};

				patcherDef = CtkSynthDef(\patcher, { arg in_bus=0, out_bus=0;
					Out.ar(out_bus,
						In.ar(in_bus, 1)
					)
				});
				clipMonDef = CtkSynthDef(\clipMonitor, { arg in_bus=0, clipThresh = 0.977;
					var sig, peak;
					sig = In.ar(in_bus, 1);
					peak = Peak.ar(sig, Impulse.kr(10));
					SendReply.ar( peak > clipThresh, '/clip', [in_bus, peak] );
				});
				server.sync;

				/* proper NODE TREE order
				monitorGroup_ins
				SoundLabDecoderPatch.group
				>decoder (ambi, thru, etc.)
				>delgainCompSynth
				patcherGroup
				monitorGroup_outs
				*/
				// group for clip monitoring synths
				monitorGroup_ins = CtkGroup.play( addAction: \head, target: 1, server: server);
				monitorGroup_outs = CtkGroup.play( addAction: \tail, target: 1, server: server);
				server.sync;
				// group for patching synths
				patcherGroup = CtkGroup.play( addAction: \after, target: monitorGroup_ins, server: server);
				server.sync;

				[monitorGroup_ins, monitorGroup_outs].do(_.play);
				server.sync;
				patcherGroup.play;
				server.sync;
				// debug
				// postf("\tmonitorGroup_ins group: %\n\tmonitorGroup_out group: %\n\tpatcherGroup group: %\n",
				// monitorGroup_ins.node, monitorGroup_outs.node, patcherGroup.node);

				// PATCHERS
				// patcherOutBus is only satellites + stereo, NO subs
				// outputs don't change on the patcher synths, only the inputs
				// i.e. there's a patcher synth "attached" to each speaker output
				satPatcherSynths = 3.collect({|j|
					var start_in_dex;
					start_in_dex = j * numHardwareOuts;
					numSatChans.collect({ |i|
						patcherDef.note( target: patcherGroup )
						.in_bus_(start_in_dex+i).out_bus_(start_in_dex+i)
						.play;
					});
				}).flat;
				server.sync;

				subPatcherSynths =  3.collect({|j|
					var start_in_dex;
					start_in_dex = j * numHardwareOuts;
					numSubChans.collect({ |i|
						patcherDef.note( target:patcherGroup )
						.in_bus_(start_in_dex+numSatChans+i)
						.out_bus_(start_in_dex+numSatChans+i)
						.play
					})
				}).flat;
				server.sync;

				hwInStart = server.options.numOutputBusChannels;

				stereoPatcherSynths = 2.collect({|i|
					patcherDef.note( target: patcherGroup )
					.in_bus_(hwInStart+i)
					.out_bus_(stereoChanIndex[i])
					//.out_bus_(numSatChans+numSubChans+i) // atm stereo always goes to first set of outs
					.play
				});
				server.sync;

				// debug
				// stereoPatcherSynths.do({|synth|synth.isPlaying.postln});
				// stereoPatcherSynths.do({|synth|synth.node.postln});
				while(	{stereoPatcherSynths.collect({|synth|synth.isPlaying}).includes(false)},
					{"waiting on stereo patchers".postln; 0.02.wait;}
				);
				0.2.wait; // TODO find a better solution here
				stereoActive.not.if{stereoPatcherSynths.do(_.pause)};

				// CLIP MONITORS
				// - initialized, not played yet
				monitorSynths_ins = numHardwareIns.collect{ |i|
					clipMonDef.note(target: monitorGroup_ins)
					.in_bus_(hwInStart + i)
				};
				monitorSynths_outs = (numHardwareOuts*3).collect{ |i|
					// note: in_bus index is a hardware out channel
					clipMonDef.note(target: monitorGroup_outs).in_bus_(i)
				};
				server.sync; // to make sure stereo patchers have started

				this.startNewSignalChain(
					if(curDecoderPatch.notNil,
						{curDecoderPatch.decoderName}, // carried over from reboot/sr change
						{defaultDecoderName}
					),
					if(usingKernels, {curKernel ?? {defaultKernel}},{nil}),
					loadCondition
				);
				loadCondition.wait; "New Signal Chain Loaded".postln;
				loadCondition.test_(false);

				clipMonitoring.if{this.clipMonitor_(true)};
				stateLoaded = true;
				this.changed(\stateLoaded);
				if(loadGUI, {this.buildGUI});
			}
		});
	}

	startNewSignalChain { |deocderName, kernelName, completeCondition|
		var cond;
		cond = Condition(false);
		fork {
			// LOAD JCONVOLVER
			if( kernelName.notNil, {
				usingKernels = true;
				"loading new jconvolver".postln;
				this.loadJconvolver(kernelName, cond); // this sets nextjconvolver var
			},{ cond.test_(true).signal });
			cond.wait; "1 - Passed loading kernels".postln;
			cond.test_(false); // reset the condition to hang when needed later

			if( loadedDelDistGain.isNil // startup
				or: nextjconvolver.notNil,  // kernel change
				{
					// LOAD DELAYS AND GAINS
					"loading new dels and gains".postln;
					this.prLoadDelDistGain(
						// TODO: get rid of usingKernels var?
						// nextjconvolver var set in loadJconvolver method above
						if( usingKernels and: nextjconvolver.notNil,
							{nextjconvolver.kernelName},
							{\default}
						),
						cond
					);
					cond.wait; "advancing to load synthdefs".postln;
					cond.test_(false);

					// debug
					"nextjconvolver: ".post; nextjconvolver.postln;
					// debug
					nextjconvolver.notNil.if{
						if( loadedDelDistGain != ((nextjconvolver.kernelName++"_"++server.sampleRate).asSymbol),
							{ warn("nextjconvolver kernel doesn't match the key that
								sets the delays, distances and gains in the decoder synth");
								nextjconvolver.free;
								nextjconvolver = nil;
							}
						);
					};

					// LOAD SYNTHDEFS
					/*	speaker dels, dists, gains must be written before loading synthdefs
					NOTE: this needs to happen for every new kernel being loaded */
					"loading synthdefs".postln;
					this.prLoadSynthDefs(cond);

					cond.wait; "2 - SynthDefs loaded".postln;
					cond.test_(false); // reset the condition to hang when needed later
			});

			server.sync; // sync to let all the synths load

			// START DECODER
			if( nextjconvolver.notNil or: 	// new jconvolver, so new outbus
				deocderName.notNil,			// requested decoder change
				{
					var newDecName;
					newDecName = deocderName ?? {
						// if no decoderName given, create new decoder matching the current one
						curDecoderPatch !? {curDecoderPatch.decoderName}
					};
					"new decoder name: ".post; newDecName.postln;
					if( newDecName.notNil, {
						this.startDecoder(newDecName, cond)
						},{ warn(
							"No decoder name provided and no current decoder name found -
							NO NEW DECODER STARTED");
							cond.test_(true).signal;
						}
					);
				},{ warn("NO NEW DECODER CREATED - no nextjconvolver and/or no decoder name provided!")}
			);
			cond.wait; "3 - Decoder started".postln;

			// set new state vars based on results from each above step
			nextjconvolver !? {
				jconvolver !? {jconvolver.free}; // free the current jconvolver
				jconvolver = nextjconvolver;
				curKernel = jconvolver.kernelName;
				jconvinbus = nextjconvinbus;
				nextjconvolver = nil;
			};
			nextDecoderPatch !? {
				//debug
				"setting curDecoderPatch".postln;
				nextDecoderPatch.postln;

				curDecoderPatch = nextDecoderPatch
			};

			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

		// cleanup server objects to be reloaded after reboot
	prClearServerSide {
		fork {
			curDecoderPatch.free(xfade);
			// curDecoderPatch = nil; // removed so variable remains for reload/sr change
			xfade.wait;
			[ patcherGroup, monitorGroup_ins, monitorGroup_outs ].do(_.free);

			jconvolver !? {jconvolver.free};
			nextjconvolver !? {nextjconvolver.free}; // ...just in case

			stateLoaded = false;
		}
	}

	startDecoder  { |newDecSynthName, completeCondition|
		var cond, newDecoderPatch, cur_decoutbus, new_decoutbus;
		cond = Condition(false);
		fork {
			// debug
			postf("Starting decoder: % \n", newDecSynthName);

			// select which of the 3 out groups to send decoder/correction to
			new_decoutbus = if(usingKernels, {
				if(curDecoderPatch.notNil,
					{	// this is the outbus being replaced..
						cur_decoutbus = curDecoderPatch.outbusnum;
						// if there's a new jconvolver, jump to next set of outputs,
						// always numHardwareOuts or (numHardwareOuts*2)
						nextjconvolver !? {
							cur_decoutbus = (cur_decoutbus + numHardwareOuts).wrap(1, numHardwareOuts*2)
						};
						cur_decoutbus;
					},{
						numHardwareOuts // first set of outputs routed to kernel
					}
				);
				},{0}	// 0 for no kernels
			);

			"new_decoutbus: ".post; new_decoutbus.postln;

			newDecoderPatch = SoundLabDecoderPatch(this,
				decoderSynthDefName: newDecSynthName,
				inbusnum: if( stereoActive, {hwInStart+2}, {hwInStart}), // decoder inbusnum
				outbusnum: new_decoutbus,		// decoder outbusnum
				loadCondition: cond				// finishCondition
			);
			cond.wait;
			// if initializing SoundLabDecoderPatch fails, decoderName won't be set
			newDecoderPatch.decoderName !? {
				// debug
				postf("newDecoderPatch initialized, playing: % \n", newDecoderPatch.decoderName);

				curDecoderPatch !? {curDecoderPatch.free(xfade: xfade)};
				newDecoderPatch.play(xfade: xfade);
				xfade.wait;
				nextDecoderPatch = newDecoderPatch;
				// TODO update changed \decoder message
				this.changed(\decoder,
					decAttributes.select({|attDict| attDict.synthdefName == newDecSynthName.asSymbol})
				);
			};
			completeCondition !? {completeCondition.test_(true).signal};

			// TODO update decInfo variable with new decoder attributes
			/*result = decAttributes.select({|item| item.defname == curDecoder.synthdefname });
			decInfo = result[0];
			debug.if{ postln("Updating decInfo variable with new decoder attributes:\n"++"\t"++decInfo.defname);
			decInfo.keysValuesDo({|k,v|postln("\t\t"++k++" "++v)})
			};
			*/
		}
	}

	// expects kernels to be located in kernelDirPath/sampleRate/kernelType/
	loadJconvolver { |newKernel, completeCondition, timeout = 5|
		var kernelDir_pn, k_path, partSize, k_size,
		numFoundKernels = 0, numtries = 50, trycnt=0,
		newjconvolver, scOutbusConnect;
		fork {
			block { |break|
				kernelDir_pn = this.prFindKernelDir(newKernel);
				kernelDir_pn.postln;
				kernelDir_pn ?? {
					this.changed(\reportStatus, warn("Kernel name not found: "++newKernel));
					jconvolver ?? {warn("No longer usingKernels"); usingKernels = false}; // if no kernel already loaded, not using kernels
					break.();
				};

				// initialize Jconvolver variables
				k_path = kernelDir_pn.absolutePath;
				partSize = if(usingSLHW, {slhw.jackPeriodSize},{512});
				kernelDir_pn.filesDo({ |file|
					if(file.extension == "wav", {
						SoundFile.use(file.absolutePath, {|fl|
							k_size = fl.numFrames;
							numFoundKernels = numFoundKernels + 1;
						})
					})
				});
				// debug
				postf("path to kernels: % \npartition size: % \nkernel size: %\n",
					k_path, partSize, k_size
				);
				// check that we have enough kernels to match all necessary speakers
				if( numFoundKernels != numKernelChans, {
					"Number of kernels found does not match the numKernelChannels!".warn;
					this.changed(\reportStatus, "Number of kernels found does not match the numKernelChannels!");
					break.();
				});

				"Generating jconvolver configuration file...".postln;
				// for osx
				// Jconvolver.jackScOutNameDefault = "scsynth:out";
				// Jconvolver.executablePath_("/usr/local/bin/jconvolver");

				nextjconvinbus = if( jconvinbus.notNil,
					{(jconvinbus + numHardwareOuts).wrap(1, numHardwareOuts*2)}, // replacing another instance
					{numHardwareOuts} // first instance
				);

				Jconvolver.createSimpleConfigFileFromFolder(
					kernelFolderPath: k_path, partitionSize: partSize,
					maxKernelSize: k_size, matchFileName: "*.wav",
					autoConnectToScChannels: nextjconvinbus, autoConnectToSoundcardChannels: 0
				);

				newjconvolver = Jconvolver.newFromFolder(k_path);

				while( {newjconvolver.isRunning.not and: (trycnt < numtries)}, {
					trycnt = trycnt+1;
					(timeout/numtries).wait;
				});
				newjconvolver.isRunning.not.if{
					warn("JConvolver didn't start after waiting "++timeout++" seconds.");
					nextjconvolver = nil;
					jconvolver ?? {this.setNoKernel}; // set to no kernel if no other jconv is running
					break.();
				};
				nextjconvolver = newjconvolver;
			};
			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

	// ------------------------------------------------------------------
	// -------- State Setters/Getters -----------------------------------
	// ------------------------------------------------------------------

	stereoRouting_ { |bool|
		block({ |break|
			(stereoActive == bool).if{ break.("stereo setting already current".warn) };
			if( bool,
				{
					postln("enabling stereo from stereoRouting");
					curDecoderPatch.decodersynth.in_busnum_(hwInStart+2);
					stereoPatcherSynths.do(_.run);
					stereoActive = true;
				},{
					postln("removing stereo from stereoRouting");
					curDecoderPatch.decodersynth.in_busnum_(hwInStart);
					stereoPatcherSynths.do(_.pause);
					stereoActive = false;
			});
			this.changed(\stereo, bool);
		});
	}

	mute { |bool = true|
		if(bool,
			{
				curDecoderPatch.compsynth.masterAmp_(0);
				("amp set to " ++ 0).postln;
				isMuted = true;
				this.changed(\mute, 1);
			},{
				isMuted = false; // must set before for this.attenuate isMuted check to work
				this.changed(\mute, 0);
				if( isAttenuated,
					{this.attenuate},
					{
						curDecoderPatch.compsynth.masterAmp_(globalAmp);
						("amp set to " ++ globalAmp.ampdb).postln;
					}
				);
			}
		)
	}

	attenuate { | bool = true, att_dB = -30|
		if(bool,
			{
				if(isMuted.not, {
					curDecoderPatch.compsynth.masterAmp_(att_dB.dbamp);
					("amp set to " ++ att_dB).postln;
				});
				isAttenuated = true;
				this.changed(\attenuate, 1);
			},{
				if(isMuted.not, {
					curDecoderPatch.compsynth.masterAmp_(globalAmp);
					("amp set to " ++ globalAmp.ampdb).postln;
				});
				isAttenuated = false;
				this.changed(\attenuate, 0);
			}
		)
	}

	amp_ { |amp_dB|
		var ampnorm;
		ampnorm = amp_dB.dbamp;
		// only update amp if not muted or att
		if( isAttenuated.not && isMuted.not, {
			curDecoderPatch.compsynth.masterAmp_(ampnorm);
			("amp set to " ++ ampnorm.ampdb).postln;
		});
		globalAmp = ampnorm; // normalized, not dB
		this.changed(\amp, globalAmp);
	}

	clipMonitor_{ | bool = true |
		if( bool,
			{ (monitorSynths_ins ++ monitorSynths_outs).do(_.play); clipMonitoring = true; },
			{ (monitorSynths_ins ++ monitorSynths_outs).do(_.free);	clipMonitoring = false; }
		);
	}

	sampleRate_ { |newSR|
		this.prClearServerSide;
		if(usingSLHW,
			{ slhw.startAudio(newSR) },{ this.prInitDefaultHW(newSR) }
		)
	}

	sampleRate { if(usingSLHW, {^slhw.server.sampleRate}, {^server.sampleRate}) }

	setNoKernel {
		curKernel = \no_correction; //nil; // TODO rethink this
		// nextKernel = false;
		usingKernels = false;
		this.changed(\kernel, curKernel);
		nextjconvolver !? {nextjconvolver.free; nextjconvolver = nil;}
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


	cleanup  {
		[clipListener, reloadGUIListener].do(_.free);
		gui !? {gui.cleanup};
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide; 			// frees jconvs
		slhw !? {slhw.stopAudio};
	}

	free { this.cleanup }
}

/* ------ TESTING ---------
s.options.device_("JackRouter")
s.options.numWireBufs_(64*8)
// make sure Jack has at least 96 virtual ins and outs
l = SoundLab(48000, useSLHW:false, useKernels:true)
s.scope(2)
"~~~~~~".postln
l.decoderLib.dict.keys

l.free
s.quit

x = {Out.ar(l.curDecoderPatch.inbusnum, 4.collect{PinkNoise.ar * SinOsc.kr(rrand(3.0, 5.0).reciprocal).range(0.0, 0.15)})}.play
x.free
s.scope
s.meter

// testing decoder/kernel switching
l.decoderLib.synthdefs.do{|sd|sd.name.postln}
l.startNewSignalChain(\Sphere_24ch_first_dual)
l.startNewSignalChain(\Sphere_12ch_first_dual)
l.startNewSignalChain(\Sphere_12ch_first_dual, kernelName: \decor)
l.startNewSignalChain(\Sphere_24ch_first_dual, kernelName: \decor_700)

// testing sample rate change
l = SoundLab(48000, useSLHW:false, useKernels:false)
l.startNewSignalChain(\Sphere_12ch_first_dual)
l.startNewSignalChain(\Dodec_first_dual)
l.startNewSignalChain(\Quad_Long_first_dual)
l.startNewSignalChain(\Hex_first_dual)

l.sampleRate_(44100)
l.sampleRate_(96000)


// testing slhw
l = SoundLab(48000, useSLHW:false, useKernels:false)

x = {Out.ar(0, 4.collect{PinkNoise.ar * SinOsc.kr(rrand(3.0, 5.0).reciprocal).range(0, 0.35)})}.play


InterfaceJS.nodePath = "/usr/local/bin/node"
l = SoundLab(48000, useSLHW:false)
l.cleanup
l.jconvolver
l.gui
l.jconvolver.free
l.gui.buildControls
l.kernelDict

q = SoundLabHardware.new(useFireface:false,midiPortName:nil,cardNameIncludes:nil,jackPath:"/usr/local/bin/jackdmp");
q.dump
q.startAudio(periodNum: 1)
q.stopAudio
s.scope(2,3)
"/usr/local/bin/jackdmp -R  -dcoreaudio -r96000 -p256 -n1 -D".unixCmd