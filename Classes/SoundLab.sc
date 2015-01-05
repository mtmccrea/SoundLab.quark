// TODO:
// ~~ create a distinction between the functionality that uses JConvolver (usingKernels boolean)
// and simply doing \basic_balance correction (which doesn't use convolution), so the \basic_balance
// isn't labeled as a "kernel", i.e. curKernel ~~

SoundLab {
	// copyArgs
	var <initSR, <loadGUI, <usingSLHW, <>usingKernels, <configFileName, <osx;

	var <>xfade = 0.2,  <>debug=true, <kernels;
	var <globalAmp, <numSatChans, <numSubChans, <totalArrayChans, <numKernelChans, <>rotateDegree, <>xOverHPF, <>xOverLPF, <>shelfFreq;
	var <hwInCount, <hwInStart;
	var <config, <labName, <numHardwareOuts, <numHardwareIns, <stereoChanIndex, <>defaultDecoderName, <>defaultKernel, <>kernelDirPath, <>decoderMatricesPath;

	var <server, <gui, <curKernel, <stereoActive, <isMuted, <isAttenuated, <stateLoaded, <rotated;
	var <clipMonitoring, <curDecoderPatch, rbtTryCnt;
	var <clipListener, <reloadGUIListener, <clipMonDef, <patcherDef;
	var <patcherGroup, <stereoPatcherSynths, <satPatcherSynths, <subPatcherSynths;
	var <monitorGroup_ins, <monitorGroup_outs, <monitorSynths_outs, <monitorSynths_ins;
	var <jconvolver, <nextjconvolver, <jconvinbus, <nextjconvinbus; //, <nextKernel;

	// SoundLabUtils
	var <compDict, <decAttributes, <decAttributeList, <matrixDecoderNames;
	var <spkrAzims, <spkrElevs, <spkrDirs, <spkrOppDict, <spkrDels, <spkrGains, <spkrDists;
	var <decoderLib, <synthLib, <loadedDelDistGain;
	var <slhw;

	*new { |initSR=96000, loadGUI=true, useSLHW=true, useKernels=true, configFileName="CONFIG_205.scd", usingOSX=false|
		^super.newCopyArgs(initSR, loadGUI, useSLHW, useKernels, configFileName, usingOSX).init;
	}

	// NOTE: Jack will create numHarwareOuts * 3 for routing to
	// system hardware (0..numHardwareOuts-1)
	// jconvolver 1 (numHardwareOuts..numHardwareOuts*2-1)
	// jconvolver 2 (numHardwareOuts..numHardwareOuts*3-1)
	// Thus SC will boot with s.option.numOutputBusChannels = numHarwareOuts * 3.
	init {

		File.use( File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ configFileName, "r", { |f|
			config = f.readAllString.interpret;
		});
		// config = thisProcess.interpreter.executeFile(File.realpath(this.class.filenameSymbol).dirname ++ "/CONFIG.scd");
		// defaults
		labName				= config.labName ?? {""};
		numHardwareOuts		= config.numHardwareOuts;
		numHardwareIns		= config.numHardwareIns;
		defaultDecoderName	= config.defaultDecoderName;	// synthDef name
		defaultKernel		= config.defaultKernel;
		stereoChanIndex		= config.stereoChanIndex;
		numSatChans			= config.numSatChans;
		numSubChans			= config.numSubChans;
		totalArrayChans		= numSatChans+numSubChans;		// stereo not included
		numKernelChans		= totalArrayChans;				// TODO: confirm this approach
		rotateDegree		= config.rotateDegree ?? {-90};	// default rotation to the right
		xOverHPF			= config.xOverHPF ?? {80};		// default xover 80Hz if not specified
		xOverLPF			= config.xOverLPF ?? {80};		// default xover 80Hz if not specified
		shelfFreq			= config.shelfFreq ?? {400};	// default shelf 400Hz (for dual band decoders) if not specified

		// kernelDirPath = PathName.new(Platform.resourceDir ++ "/sounds/SoundLabKernelsNew/");
		kernelDirPath = kernelDirPath ?? {
			config.kernelsPath !? {
				if(config.kernelsPath[0].asSymbol == '/', {//it's absolute path
					PathName.new(config.kernelsPath);
					}, {
						if(config.kernelsPath[0] == "~", {//it's relative to home directory
							PathName.new(config.kernelsPath.standardizePath);
							}, {//it's relative to the class file
								PathName.new(File.realpath(this.class.filenameSymbol).dirname ++ "/"
									++ config.kernelsPath)
						});
				});
			};
		}; //expecting path relative the class, NOT starting with a slash

		/* Custom matrix decoder TXT files */
		// folder structure under decoderMatricesPath location should be
		// 2 folders: "dual", "single"
		// "dual" contains folders for each set of HF and LF matrix files, the name of the
		// folder becomes the name of the decoder
		// "single" contains the matrix txt files, the name of the file (without .txt)
		// becomes the name of the decoder
		decoderMatricesPath = decoderMatricesPath ?? {
			config.decoderMatricesPath !? {
				if(config.decoderMatricesPath[0].asSymbol == '/', {	//it's absolute path
					PathName.new(config.decoderMatricesPath);
					}, {
						if(config.decoderMatricesPath[0] == "~", {	//it's relative to home directory
							PathName.new(config.decoderMatricesPath.standardizePath);
							}, {//it's relative to the class file
								PathName.new(File.realpath(
									this.class.filenameSymbol).dirname ++ "/"
									++ config.decoderMatricesPath)
						});
				});
			}; //expecting path relative the class, NOT starting with a slash
		};

		globalAmp = 0.dbamp;
		stereoActive = stereoActive ?? {true};
		isMuted = isMuted ?? {false};
		isAttenuated = isAttenuated ?? {false};
		stateLoaded = stateLoaded ?? {false};
		clipMonitoring = clipMonitoring ?? {false};
		rotated = rotated ?? {false};
		matrixDecoderNames = [];

		this.prInitRigDimensions;
		this.prInitDecoderAttributes;

		rbtTryCnt = 0;

		OSCdef(\clipListener, {
			|msg, time, addr, recvPort|
			("CLIPPED channel" ++ msg[3]).postln;
			this.changed(\clipped, msg[3]);
			}, '/clip', nil
		);

		OSCdef(\reloadGUI, {
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

	// this happens after hardware is intitialized and server is booted
	prLoadServerSide { |argServer|
		var loadCondition;
		loadCondition = Condition(false);
		postln("Loading Server Side ... loading synths, intializing channel counts, groups, and busses."); // debug

		server = argServer ?? {
			"server defaulting because none provided -prLoadServerSide".warn;
			Server.default
		};

		server.doWhenBooted({
			fork {
				"waiting 3 seconds".postln;
				3.wait; // give server time to get sorted

				// if( usingKernels, {
					// get an up-to-date list of the kernels available at this sample rate
					kernels = compDict.delays.keys.select({ |name|
						name.asString.contains(server.sampleRate.asString)
					});
					// reformat to exclude SR
					kernels = kernels.collect{|key|
						var modkey;
						modkey = key.asString;
						modkey = modkey.replace("_44100","");
						modkey = modkey.replace("_48000","");
						modkey = modkey.replace("_96000","");
						modkey.asSymbol;
					};
					kernels = [\basic_balance] ++ kernels.asArray;

					// in the case of a SR change, check to make sure the curKernel is still available
					// at this sampleRate
					curKernel !? {
						if( kernels.includes(curKernel).not, {
							curKernel.postln;
							curKernel.class.postln;
							kernels.postln;
							this.changed(\reportStatus,
								warn("Last kernel wasn't found at this sample rate. Defaulting to basic_balance.")
							);
							this.setNoKernel;
						})
					};
			// });

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
					// TAIL so avoid sending from stereo into satellite patcher synths (doubling the output)
					patcherDef.note( addAction: \tail, target: patcherGroup )
					.in_bus_(hwInStart+i)
					.out_bus_(stereoChanIndex[i])
					.play
				});
				server.sync;

				while(	{stereoPatcherSynths.collect({|synth|synth.isPlaying}).includes(false)},
					{"waiting on stereo patchers".postln; 0.02.wait;}
				);
				0.2.wait; // TODO find a better solution than wait
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
				rotated.if{this.rotate_(true)};
				stateLoaded = true;
				if(loadGUI, {this.buildGUI});
				this.changed(\stateLoaded);
			}
		});
	}

	startNewSignalChain { |deocderName, kernelName, completeCondition|
		var cond;
		cond = Condition(false);
		fork {
			"in startNewSignalChain, kernelName: %\n".postf(kernelName);

			// LOAD JCONVOLVER
			if( (kernelName == \basic_balance),
				{	"changing to basic_balance, usingKernels = false".postln; // debug
					this.setNoKernel;
					cond.test_(true).signal
				},{
					if( kernelName.notNil, {
						usingKernels = true;
						"loading new jconvolver".postln; // debug
						this.loadJconvolver(kernelName, cond); // this sets nextjconvolver var
						// TODO: confirm what happens below when loadJconvolver fails and
						// nextjconvolver set to nil?
						},{
							"no new correction specified".postln;
							cond.test_(true).signal
						}
					)
				}
			);

			cond.wait;
			"1 - Passed loading jconvolver".postln;
			cond.test_(false); // reset the condition to hang when needed later

			// LOAD DELAYS AND GAINS

			if( loadedDelDistGain.isNil				// startup
				or: nextjconvolver.notNil			// kernel change
				or: (kernelName == \basic_balance),	// switching to basic_balance
				{
					//debug
					postf("\nloadedDelDistGain.isNil - startup: %\n", loadedDelDistGain.isNil);
					postf("nextjconvolver.notNil - kernel change: %\n", nextjconvolver.notNil);
					postf("kernelName == \basic_balance -  switching to basic_balance: %\n\n", kernelName == \basic_balance);

					this.prLoadDelDistGain(
						// nextjconvolver var set in loadJconvolver method above
						if( nextjconvolver.notNil, // TODO what happens below when loadJconvolver fails?
							{nextjconvolver.kernelName},
							{
								"selecting default dist/gains".postln;
								\default; // return
							}
						),
						cond
					);
					cond.wait;
					cond.test_(false);
					// debug
					"nextjconvolver: ".post; nextjconvolver.postln;

					nextjconvolver.notNil.if{
						if( loadedDelDistGain != ((nextjconvolver.kernelName++"_"++server.sampleRate).asSymbol),
							{ warn("nextjconvolver kernel doesn't match the key that
								sets the delays, distances and gains in the decoder synth");
								nextjconvolver.free;
								nextjconvolver = nil;
								// TODO what happens below when loadJconvolver fails?
							}
						);
					};

					// LOAD SYNTHDEFS
					/*	speaker dels, dists, gains must be written before loading synthdefs
					NOTE: this needs to happen for every new kernel being loaded */
					"loading synthdefs".postln;
					this.prLoadSynthDefs(cond);
					cond.wait;
					"2 - SynthDefs loaded".postln;
					cond.test_(false); // reset the condition to hang when needed later
			});

			server.sync; // sync to let all the synths load

			// START DECODER

			postf("nextjconvolver before starting decoder: %\n", nextjconvolver);
			if( nextjconvolver.notNil or: 	// new jconvolver, so new outbus
				deocderName.notNil,			// requested decoder change
				{
					var newDecName;
					newDecName = deocderName ?? {
						// if no decoderName given, create new decoder matching the current one
						curDecoderPatch !? {curDecoderPatch.decoderName}
					};
					"new decoder name: %\n".postf( newDecName ); // debug
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
				jconvolver !? {jconvolver.free}; 	// free the current jconvolver
				jconvolver = nextjconvolver;		// update var with new instance
				curKernel = jconvolver.kernelName;
				jconvinbus = nextjconvinbus;
				nextjconvolver = nil;				// reset var
				this.changed(\kernel, curKernel);
			};

			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

	// cleanup server objects to be reloaded after reboot
	prClearServerSide { |finishCondition|
		fork {
			curDecoderPatch.free(xfade);
			// curDecoderPatch = nil; // removed so variable remains for reload/sr change
			xfade.wait;
			[ patcherGroup, monitorGroup_ins, monitorGroup_outs ].do(_.free);

			jconvolver !? {jconvolver.free};
			nextjconvolver !? {nextjconvolver.free}; // ...just in case
			stateLoaded = false;
			finishCondition !? {finishCondition.test_(true).signal}
		}
	}

	startDecoder  { |newDecName, completeCondition|
		var cond, newDecoderPatch, cur_decoutbus, new_decoutbus, new_decinbus;
		cond = Condition(false);
		fork {
			postf("Starting decoder: % \n", newDecName); // debug

			// select which of the 3 out groups to send decoder/correction to
			new_decoutbus = if(usingKernels, {
				if(jconvinbus.notNil, // jconvinbus set in loadJConvolver method
					{ jconvinbus },
					{ numHardwareOuts } // startup: first set of outputs routed to kernel
				);
				},{0}	// 0 for no kernels
			);

			new_decinbus = if( stereoActive, {hwInStart+2}, {hwInStart});

			newDecoderPatch = SoundLabDecoderPatch(this,
				decoderName: newDecName,
				inbusnum: new_decinbus, 	// decoder inbusnum
				outbusnum: new_decoutbus,	// decoder outbusnum
				loadCondition: cond			// finishCondition
			);
			cond.wait;
			// if initializing SoundLabDecoderPatch fails, decoderName won't be set
			newDecoderPatch.decoderName !? {

				postf("newDecoderPatch initialized, playing: % \n", newDecoderPatch.decoderName); // debug

				curDecoderPatch !? {curDecoderPatch.free(xfade: xfade)};
				newDecoderPatch.play(xfade: xfade);
				xfade.wait;
				curDecoderPatch = newDecoderPatch;
				this.changed(\decoder, curDecoderPatch);
			};
			completeCondition !? {completeCondition.test_(true).signal};
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
					this.changed(\reportStatus, warn("Kernel name not found: "++newKernel++". No longer using kernels!"));
					jconvolver ?? {
						// if no kernel already loaded, not using kernels
						warn("No longer usingKernels");
						this.setNoKernel;
					};
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

				osx.if{ // for osx
					Jconvolver.jackScOutNameDefault = "scsynth:out";
					Jconvolver.executablePath_("/usr/local/bin/jconvolver");
				};

				nextjconvinbus = if( jconvinbus.notNil,
					{(jconvinbus + numHardwareOuts).wrap(1, numHardwareOuts*2)}, // replacing another instance
					{numHardwareOuts} // first instance
				);

				Jconvolver.createSimpleConfigFileFromFolder(
					kernelFolderPath: k_path, partitionSize: partSize,
					maxKernelSize: k_size, matchFileName: "*.wav",
					autoConnectToScChannels: nextjconvinbus, autoConnectToSoundcardChannels: 66
				);

				jconvinbus = nextjconvinbus;

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

	buildGUI {
		gui ?? {gui = SoundLabGUI.new(this)};
	}

	// ------------------------------------------------------------------
	// -------- State Setters/Getters -----------------------------------
	// ------------------------------------------------------------------

	stereoRouting_ { |bool|
		block({ |break|
			(stereoActive == bool).if{
				this.changed(\stereo, bool); // to reset the GUI
				break.("stereo setting already current".warn)
			};
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

	rotate_ { |bool|
		block({ |break|
			(rotated == bool).if{ break.("rotation setting already current".warn) };
			(curDecoderPatch.decType == \discrete).if{
				this.changed(\reportStatus, "routing is discrete, no rotation");
				break.("routing is discrete, no rotation".warn);
			};
			if( bool,
				{
					curDecoderPatch.decodersynth.rotate_(rotateDegree.degrad);
					rotated = true;
				},{
					curDecoderPatch.decodersynth.rotate_(0);
					rotated = false;
			});
			this.changed(\rotate, bool);
		});
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
		var cond;
		cond = Condition(false);
		this.prClearServerSide(cond);
		cond.wait;
		if(usingSLHW,
			{ slhw.startAudio(newSR) },
			{ this.changed(\stoppingAudio); this.prInitDefaultHW(newSR) }
		)
	}

	sampleRate { if(usingSLHW, {^slhw.server.sampleRate}, {^server.sampleRate}) }

	setNoKernel {
		curKernel = \basic_balance;
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
		[OSCdef(\clipListener), OSCdef(\reloadGUI)].do(_.free);
		gui !? {gui.cleanup};
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide; 			// frees jconvs
		slhw !? {slhw.stopAudio};
		if ( gui.notNil, {gui.cleanup} );
	}

	free { this.cleanup }
}

/* ------ TESTING ---------
(
s.options.numOutputBusChannels_(32);
s.options.device_("JackRouter");
s.options.numWireBufs_(64*8);
// make sure Jack has at least [3x the number of your hardware output busses] virtual ins and outs
// if using the convolution system and you intend to switch between kernel sets
// and default (no convolution) settings

InterfaceJS.nodePath = "/usr/local/bin/node";

//initSR=96000, loadGUI=true, useSLHW=true, useKernels=true, configFileName="CONFIG_205.scd"
l = SoundLab(48000, loadGUI:true, useSLHW:false, useKernels:false, configFileName:"CONFIG_TEST.scd")
)

l.rotated
l.rotateDegree

l.startNewSignalChain(\Dome_15ch)
l.startNewSignalChain(\Dome_9ch)
l.startNewSignalChain(\Dome_11ch)

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
l.startNewSignalChain(\Sphere_24ch)
l.startNewSignalChain(\Sphere_12ch)
l.startNewSignalChain(\Sphere_12ch, kernelName: \decor)
l.startNewSignalChain(\Sphere_24ch, kernelName: \decor_700)

// testing sample rate change
l = SoundLab(48000, useSLHW:false, useKernels:false)
l.startNewSignalChain(\Sphere_12ch_first_dual)
l.startNewSignalChain(\Dodec)
l.startNewSignalChain(\Quad_Long)
l.startNewSignalChain(\Hex)
l.startNewSignalChain(\Thru_All, \NA)
l.decoderLib.dict.keys

l.sampleRate_(44100)
l.sampleRate_(96000)


// testing slhw
l = SoundLab(48000, useSLHW:false, useKernels:false)

// testing gui
l = SoundLab(48000, loadGUI:true, useSLHW:false, useKernels:false)

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
*/