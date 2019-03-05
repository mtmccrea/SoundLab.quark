// TODO:
// ~~ create a distinction between the functionality that uses JConvolver (usingKernels boolean)
// and simply doing \basic_balance correction (which doesn't use convolution), so the \basic_balance
// isn't labeled as a "kernel", i.e. curKernel ~~

SoundLab {
	// copyArgs
	var <configFileName, <>usingKernels, <loadGUI, <usingSLHW;

	var <>xfade = 0.2,  <>debug=true, <kernels;
	var <globalAmp, <numSatChans, <numSubChans, <totalArrayChans, <numKernelChans, <>rotateDegree, <>xOverHPF, <>xOverLPF, <>shelfFreq;
	var <hwInCount, <hwInStart;
	var <config, <labName, <numHardwareOuts, <numHardwareIns, <stereoChanIndex, <>defaultDecoderName, <>kernelDirPathName, <>decoderMatricesPath, <configRelativePathName = "../config/", <initSR;

	var <server, <gui, <curKernel, <stereoActive, <isMuted, <isAttenuated, <stateLoaded, <rotated;
	var <clipMonitoring, <curDecoderPatch, rbtTryCnt;
	var <clipListener, <reloadGUIListener, <clipMonDef, <patcherDef, <sterPatcherDef, <stereoSubPatcherDef;
	var <patcherGroup, <stereoPatcherSynths, <satPatcherSynths, <subPatcherSynths;
	var <monitorGroup_ins, <monitorGroup_outs, <monitorSynths_outs, <monitorSynths_ins;
	var <jconvolver, <nextjconvolver, <jconvinbus, <nextjconvinbus, <jconvHWOutChannel, <stereoGain;

	// SoundLabUtils
	var <compDict, <decAttributes, <decAttributeList, <matrixDecoderNames;
	var <spkrAzims, <spkrElevs, <spkrDirs, <spkrOppDict, <spkrDels, <spkrGains, <spkrDists;
	var <decoderLib, <synthLib, <loadedDelDistGain;
	var <slhw;
	var <forceCleanupFunc, <recompileWindow;

	*new { |configFileName="CONFIG_205.scd", useKernels=true, loadGUI=true, useSLHW=true|
		^super.newCopyArgs(configFileName, useKernels, loadGUI, useSLHW).init;
	}

	// NOTE: Jack will create numHarwareOuts * 3 for routing to
	// system hardware (0..numHardwareOuts-1)
	// jconvolver 1 (numHardwareOuts..numHardwareOuts*2-1)
	// jconvolver 2 (numHardwareOuts..numHardwareOuts*3-1)
	// Thus SC will boot with s.option.numOutputBusChannels = numHarwareOuts * 3.
	init {
		var filePath;

		forceCleanupFunc = {this.cleanup(true)};
		ShutDown.add(forceCleanupFunc);

		if(PathName(configFileName).isAbsolutePath, {
			filePath = configFileName;
		}, {
			filePath = File.realpath(this.class.filenameSymbol).dirname +/+ configRelativePathName ++ configFileName;
		});

		if(File.exists(filePath), {

			File.use(filePath, "r", { |f|
				config = f.readAllString.interpret;
			});

		}, {
			Error("FIle not found at" + filePath).throw;
		});


		// defaults
		labName				= config.labName ?? {""};
		numHardwareOuts		= config.numHardwareOuts;
		numHardwareIns		= config.numHardwareIns;
		defaultDecoderName	= config.defaultDecoderName;	// synthDef name
		stereoChanIndex		= config.stereoChanIndex;
		numSatChans			= config.numSatChans;
		numSubChans			= config.numSubChans;
		totalArrayChans		= numSatChans+numSubChans;		// stereo not included
		numKernelChans		= totalArrayChans;				// TODO: confirm this approach
		rotateDegree		= config.rotateDegree ?? {-90};	// default rotation to the right
		xOverHPF			= config.xOverHPF ?? {80};		// default xover 80Hz if not specified
		xOverLPF			= config.xOverLPF ?? {80};		// default xover 80Hz if not specified
		jconvHWOutChannel	= config.jconvHWOutChannel ?? {0};	// default xover 80Hz if not specified
		stereoGain			= config.stereoGain ?? 0;		// gain in dB to balance stereo with decoders
		initSR = config.initSampleRate;
		// Note: shelfFreq in config takes precedence over listeningDiameter
		// order / pi * 340 / listeningDiameter
		// default shelf 400Hz (for dual band decoders)
		shelfFreq			= config.shelfFreq ?? {
			if( config.listeningDiameter.notNil,
				{ 1 / pi * 340 / config.listeningDiameter},
				{400})
		};

		kernelDirPathName = kernelDirPathName ?? {
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

		config.jconvolverPath !? {
			Jconvolver.executablePath_(config.jconvolverPath)
		};

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

		OSCdef(\restart, {
			fork {
				this.cleanup;
				0.5.wait;
				thisProcess.recompile;
			}
		}, \restart
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
		"\n*** Loading Server Side ***\n".postln;

		server = argServer ?? {
			"server defaulting because none provided -prLoadServerSide".warn;
			Server.default
		};

		server.doWhenBooted({
			fork {
				var requestKernel;
				"waiting 2 seconds".postln;
				2.wait; // give server time to get sorted

				// get an up-to-date list of the kernels available at this sample rate
				kernels = [];
				// "kernelDirPathName: ".post; kernelDirPathName.postln;
				kernelDirPathName !? {
					kernelDirPathName.entries.do({ |sr_pn|
						var sr, nm, knm, result;

						(sr_pn.isFolder && (sr_pn.folderName.asInt == server.sampleRate)).if{

							sr_pn.entries.do({ |kern_pn|
								kern_pn.isFolder.if{
									// kernel "category name"
									knm = kern_pn.folderName;
									kern_pn.entries.do{ |entry_pn|
										// kernel folder
										if(entry_pn.isFolder, {
											// could add check here for soundfiles within
											// to confirm it's a kernel folder

											// kernel stored as String of the path relative to sample rate
											kernels = kernels.add( knm ++ "/" ++ entry_pn.folderName )
										});
									}
								}
							})
						}
					});
				};

				kernels = [\basic_balance] ++ kernels;

				// in the case of a SR change, check to make sure the curKernel
				// is still available at this sampleRate
				if( curKernel.notNil and: (curKernel != \basic_balance) ){
					var namedFolder, kernelFolder, pn;

					pn = PathName(curKernel.asString);
					namedFolder = pn.allFolders[pn.allFolders.size-2];
					kernelFolder = pn.allFolders.last;
					("testing " ++ (namedFolder ++ "/" ++ kernelFolder)).postln;

					if( kernels.collect(_.asSymbol).includes((namedFolder ++ "/" ++ kernelFolder).asSymbol), {
						var newPath;
						// update curKernel to new SR path
						newPath = format( "%%/%/%/", config.kernelsPath, server.sampleRate.asInteger, namedFolder, kernelFolder);
						File.exists(newPath).if({
							requestKernel = newPath;
						},{ this.setNoKernel });
					},{
						this.changed(\reportStatus,
							warn("Last kernel wasn't found at this sample rate. Defaulting to basic_balance.")
						);
					})
				}{ this.setNoKernel };

				// kill any running Jconvolvers
				jconvolver !? {"Stopping a running jconvolver".postln; jconvolver.free};
				nextjconvolver !? {"Stopping a running jconvolver".postln; nextjconvolver.free};

				patcherDef = CtkSynthDef(\patcher, { arg in_bus=0, out_bus=0;
					ReplaceOut.ar(out_bus, In.ar(in_bus, 1))
				});

				sterPatcherDef = CtkSynthDef(\sterpatcher, { arg in_bus=0, out_bus=0, amp=1;
					var in;
					// stereo patchers don't use ReplaceOut because
					// often stereo channels are shared with
					// satellite channels, and ReplaceOut would overwrite
					// the satellite's bus contents

					// Out.ar(out_bus, In.ar(in_bus, 1))
					in = In.ar(in_bus, 2);

					2.do{|i|
						Out.ar( stereoChanIndex[i],
							// catch if stereo channels weren't measured (like 117,
							// where they're speakers apart from the ambisonics rig)
							in[i]
							* (config.defaultSpkrGainsDB[stereoChanIndex[i]] ?? 0).dbamp
							* amp
							* stereoGain.dbamp
						)
					};
				});

				stereoSubPatcherDef = CtkSynthDef(\stersubpatcher, { arg in_bus=0, out_bus=0, amp=1;
					if( numSubChans == 1,
						{	// summing stereo into 1 sub
							Out.ar(out_bus,
								In.ar(in_bus, 2).sum
								* (2.sqrt/2)
								* config.defaultSpkrGainsDB[numSatChans].dbamp
								* stereoGain.dbamp
							)
						},{ // encoding stereo t b format then decoding to multipl subs
							var stereoBF = FoaEncode.ar(
								In.ar(in_bus, 2),
								FoaEncoderMatrix.newStereo(pi)
							);

							// simple cardioid mono decoder for each sub direction
							numSubChans.do{ |i|
								Out.ar( out_bus + i,
									FoaDecode.ar(stereoBF,
										FoaDecoderMatrix.newMono(
											config.spkrAzimuthsRad[numSatChans + i],
											0, 0.5)   // no elevation, cardioid
									)
									* (2/numSubChans) // balance the energy based on the number of subs
									* config.defaultSpkrGainsDB[numSatChans+i].dbamp
									* amp
									* stereoGain.dbamp
								)
							};
					});
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

				// stereoPatcherSynths = 2.collect({|i|
				stereoPatcherSynths = 1.collect({|i|
					// TAIL so avoid sending from stereo into satellite patcher synths (doubling the output)
					sterPatcherDef.note( addAction: \tail, target: patcherGroup )
					.in_bus_(hwInStart+i)
					.out_bus_(stereoChanIndex[i]) // this isn't used now that once synths routes both channels
					.play
				})

				// route stereo to subs as well,
				// this synth also does gain comp on sub(s)
				// add after patcher group to ensure it's the very last synth
				// so it doesn't overwrite any busses
				++ stereoSubPatcherDef.note( addAction: \tail, target: patcherGroup )
				.in_bus_(hwInStart)
				// TODO: this implies no filter correction on stereo sub send, is that OK?
				.out_bus_(numSatChans)
				.play
				;
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

				postf("Starting new signal chain\nequestKernel: %\n", requestKernel);

				this.startNewSignalChain(
					if(curDecoderPatch.notNil,
						{curDecoderPatch.decoderName}, // carried over from reboot/sr change
						{defaultDecoderName}
					),
					requestKernel ?? \basic_balance,
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

	checkKernelFolder { |pendingKernel|
		var str;
		// build the path to the kernel folder
		str = kernelDirPathName.absolutePath ++ server.sampleRate.asInteger.asString ++ "/" ++ pendingKernel;

		^File.exists(str).if({
			str
		},{
			warn("Folder for this kernel doesn't exist. Check that the folder specified in the config is available at all sample rates.");
			nil; // return
		});
	}

	// kernelPath of nil designates no kernel change
	startNewSignalChain { |deocderName, kernelPath, completeCondition|
		var cond = Condition(false);

		fork {
			var testKey;
			"StartNewSignalChain - kernelPath: \t%\n".postf(kernelPath);

			if( kernelPath.notNil, {
				if( kernelPath != \basic_balance, {
					// load jconvolver
					usingKernels = true;
					"loading new jconvolver".postln; // debug
					this.loadJconvolver(kernelPath, cond); // this sets nextjconvolver var
				},{
					// setting to basic balance
					this.setNoKernel;
					cond.test_(true).signal
				})
			},{
				// no kernel change, just move one
				"no new correction specified".postln;
				cond.test_(true).signal
			});

			cond.wait;
			cond.test_(false); // reset the condition to hang when needed later



			// Load delays, distances and gains anew if needed

			if( loadedDelDistGain.isNil					// startup
				or: nextjconvolver.notNil				// kernel change
				or: (kernelPath == \basic_balance),		// switching to basic_balance
				{
					var testKey, delDistGainKey;

					delDistGainKey = if( nextjconvolver.notNil, {
						// build the key from the kernel path (queried from nextjconvolver to be sure) and sample rate
						var kpn;
						kpn = PathName(nextjconvolver.kernelFolderPath);

						testKey = (this.sampleRate.asString ++ "/" ++ kpn.allFolders[kpn.allFolders.size-2]).asSymbol;
					},{
						"Selecting default delay/dist/gain.".postln;
						\default;
					});

					// load delays, distances and gains
					this.prLoadDelDistGain( delDistGainKey, cond );
					cond.wait;
					cond.test_(false);

					// if loading delays, distances and gains fails, it will be set to default
					// in which case nextjconvolver has to be "cancelled"
					if( nextjconvolver.notNil and: (loadedDelDistGain == \default), {
						nextjconvolver.free;
						nextjconvolver = nil;
						warn( format(
							"nextjconvolver kernel % doesn't match the key that sets the delays, distances and gains in the decoder synth\n", testKey
						));
					});

					// because delays, distances and gains have changed, need to
					// reload synthdefs
					"\n*** Loading SynthDefs ***\n".postln;
					this.prLoadSynthDefs(cond);
					cond.wait;
					cond.test_(false);
					"\n*** SynthDefs loaded ***\n".postln;
			});

			server.sync; // sync to let all the synths load

			// start new decoder if needed
			if( nextjconvolver.notNil or: 	// new jconvolver, so new outbus
				deocderName.notNil,			// requested decoder change
				{
					var newDecName;
					// if no decoderName given, create new decoder matching the current one
					newDecName = deocderName ?? {
						curDecoderPatch !? {curDecoderPatch.decoderName}
					};

					if( newDecName.notNil, {
						postf("New decoder starting: %\n", newDecName);
						this.startDecoder(newDecName, cond)
					},{
						warn( "No decoder name provided and no current decoder name found -
NO NEW DECODER STARTED");
						cond.test_(true).signal;
					});

				},{ warn("NO NEW DECODER CREATED - no nextjconvolver and/or no decoder name provided!")}
			);

			cond.wait;

			// set new state vars based on results from each above step
			nextjconvolver !? {
				jconvolver !? {jconvolver.free}; 	// free the current jconvolver
				jconvolver = nextjconvolver;		// update var with new instance
				curKernel = jconvolver.kernelFolderPath;
				jconvinbus = nextjconvinbus;
				nextjconvolver = nil;				// reset var
				this.changed(\kernel, curKernel);
			};
			"\n*** END ***\n".postln;
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

			completeCondition !? { completeCondition.test_(true).signal };
		}
	}

	// expects kernels to be located in kernelDirPath/sampleRate/kernelType/
	loadJconvolver { |newKernelPath, completeCondition, timeout = 5|
		var kernelDir_pn, partSize, k_size,
		numFoundKernels = 0, numtries = 50, trycnt=0,
		newjconvolver, scOutbusConnect, jconvHWOut;
		fork {
			block { |break|
				kernelDir_pn = PathName(newKernelPath); //this.prFindKernelDir(newKernel);
				kernelDir_pn.postln;
				kernelDir_pn ?? {
					this.changed(\reportStatus, warn("Kernel name not found: "++newKernelPath++".  No longer using kernels!"));
					jconvolver ?? {
						// if no kernel already loaded, not using kernels
						warn("No longer usingKernels");
						this.setNoKernel;
					};
					break.();
				};

				// initialize Jconvolver variables
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
					newKernelPath, partSize, k_size
				);
				// check that we have enough kernels to match all necessary speakers
				if( numFoundKernels != numKernelChans, {
					var msg;
					msg = format("Number of kernels found (%) does not match the numKernelChannels (%)!", numFoundKernels, numKernelChans);
					warn(msg);
					this.changed(\reportStatus, msg);
					break.();
				});

				"Generating jconvolver configuration file...".postln;

				config.jconvolverPath !? {
					Jconvolver.executablePath_(config.jconvolverPath);
				};

				//name guessing, for now here

				thisProcess.platform.name.switch(
					\osx, {
						if(Server.program.asString.endsWith("scsynth"), {
							"config.audioDeviceName.notNil: ".post;  config.audioDeviceName.notNil.postln;
							"config.audioDeviceName: ".post; config.audioDeviceName.postln;
							if(config.audioDeviceName.notNil, {
								Jconvolver.jackScOutNameDefault = "scsynth:out"; //assuming SC -> JackRouter
							}, {
								Jconvolver.jackScOutNameDefault = "SuperCollider:out_"; //assuming native JACK backend
							});
						}, {
							Jconvolver.jackScOutNameDefault = "supernova:out"; //not tested
						})
					},
					\linux, {
						if(Server.program.asString.endsWith("scsynth"), {
							Jconvolver.jackScOutNameDefault = "SuperCollider:out_";
						}, {
							Jconvolver.jackScOutNameDefault = "supernova:out"; //not tested
						})
					}
				);

				// osx.if{ // for osx
				// 	Jconvolver.jackScOutNameDefault = "scsynth:out";
				// 	Jconvolver.executablePath_("/usr/local/bin/jconvolver");
				// };

				nextjconvinbus = if( jconvinbus.notNil,
					{(jconvinbus + numHardwareOuts).wrap(1, numHardwareOuts*2)}, // replacing another instance
					{numHardwareOuts} // first instance
				);

				jconvHWOut = if(usingSLHW, {
					if(slhw.whichMadiOutput.isNil,
						{ jconvHWOutChannel },
						{
							var numChannelsPerMADI;
							numChannelsPerMADI = if(slhw.sampleRate <= 48000, {64},{32});
							(slhw.whichMadiOutput * numChannelsPerMADI) + 2;
						}
					)
				},{jconvHWOutChannel}
				);
				Jconvolver.createSimpleConfigFileFromFolder(
					kernelFolderPath: newKernelPath,
					partitionSize: partSize,
					maxKernelSize: k_size,
					matchFileName: "*.wav",
					autoConnectToScChannels: nextjconvinbus,
					autoConnectToSoundcardChannels: jconvHWOut
					// autoConnectToSoundcardChannels: jconvHWOutChannel
				);

				jconvinbus = nextjconvinbus;

				newjconvolver = Jconvolver.newFromFolder(newKernelPath);

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
		if (gui.isNil) {
			gui = SoundLabGUI.new(this);
		} {
			fork {
				gui.cleanup;
				1.wait;
				gui = SoundLabGUI.new(this);
			}
		}
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
					curDecoderPatch.decodersynth.in_busnum_(hwInStart+2);
					stereoPatcherSynths.do(_.run);
					stereoActive = true;
				},{
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
				isMuted = true;
				this.changed(\mute, 1);
			},{
				isMuted = false; // must set before for this.attenuate isMuted check to work
				this.changed(\mute, 0);
				if( isAttenuated,
					{this.attenuate},
					{curDecoderPatch.compsynth.masterAmp_(globalAmp)}
				);
			}
		)
	}

	attenuate { | bool = true, att_dB = -30|
		if(bool,
			{
				if( isMuted.not )
				{curDecoderPatch.compsynth.masterAmp_(att_dB.dbamp)};

				isAttenuated = true;
				this.changed(\attenuate, 1);
			},{
				if( isMuted.not )
				{curDecoderPatch.compsynth.masterAmp_(globalAmp)};

				isAttenuated = false;
				this.changed(\attenuate, 0);
			}
		)
	}

	rotate_ { |bool|
		block({ |break|
			(rotated == bool).if{break.("rotation setting already current".warn)};
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
			curDecoderPatch.compsynth.masterAmp_(ampnorm); // set decoder amp
			stereoPatcherSynths.do(_.amp_(ampnorm)); // set stereo channels amp (including subs)
		});
		globalAmp = ampnorm; // normalized, not dB
		this.changed(\amp, globalAmp);
	}

	sweetSpotDiam_ { |diam, order = 1| // diam in meters
		var freq;
		freq = order / pi * 340 / diam;
		curDecoderPatch !? { curDecoderPatch.shelfFreq_(freq) };
		shelfFreq = freq;
		this.changed(\shelfFreq, freq);
		this.changed(\sweetSpotDiam, diam);
	}

	getDiamByFreq { |freq, order = 1|
		^ order / pi * 340 / freq;
	}

	clipMonitor_{ | bool = true |
		if( bool,
			{(monitorSynths_ins ++ monitorSynths_outs).do(_.play); clipMonitoring = true;},
			{(monitorSynths_ins ++ monitorSynths_outs).do(_.free); clipMonitoring = false;}
		);
	}

	sampleRate_ { |newSR|
		var cond;
		cond = Condition(false);
		this.prClearServerSide(cond);
		cond.wait;
		if(usingSLHW,
			{slhw.startAudio(newSR)},
			{this.changed(\stoppingAudio); this.prInitDefaultHW(newSR)}
		)
	}

	sampleRate {if(usingSLHW, {^slhw.server.sampleRate.asInteger}, {^server.sampleRate.asInteger})}

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


	prInitRigDimensions {
		// TODO consider adding sat and sub keys to compDict
		compDict = IdentityDictionary.new(know: true).putPairs([
			\gains, IdentityDictionary.new(know: true),
			\distances, IdentityDictionary.new(know: true),
			\delays, IdentityDictionary.new(know: true)
		]);

		/* distances (m): */
		compDict.distances.put( \default, config.defaultSpkrDistances );

		/* calculate delays (sec): */
		compDict.delays.put( \default,
			config.defaultSpkrDelays ??
			// calculate from distance
			(compDict.distances.default.maxItem - compDict.distances.default) / 343;
		);

		/* gains (dB) */
		compDict.gains.put( \default, config.defaultSpkrGainsDB );

		/* parse distance delay and gain files from kernel folders */
		// Folder structure: /sampleRate/kernelName/ holds
		// delay, dist, gain .txt files and folders for each "version"
		// of the kernel, i.e. the various settings: moderate, high correction, min/lin phase, etc
		kernelDirPathName !? {
			kernelDirPathName.entries.do({ |sr_pn|
				var sr, nm, knm, result;

				(sr_pn.isFolder && (sr_pn.folderName.asInt !=0)).if{
					sr = sr_pn.folderName;
					sr_pn.entries.do({ |kern_pn|
						kern_pn.isFolder.if{
							knm = kern_pn.folderName;
							kern_pn.entries.do{ |file_pn|
								if(file_pn.isFile, {
									nm = file_pn.fileName;
									case
									{nm.contains("delays")}{
										debug.if{postf("\nParsing delays for %, %: ", knm, sr)};
										compDict.delays.put(
											(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
										);
									}{nm.contains("distances")}{
										debug.if{postf("\nParsing distances for %, %:", knm, sr)};
										compDict.distances.put(
											(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
										);
									}{nm.contains("gains")}{
										debug.if{postf("\nParsing gains for %, %:", knm, sr)};
										compDict.gains.put(
											(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
										);
									};
								});
							}
						}
					})
				}
			});
		};

		/* azimuth angles */
		spkrAzims = config.spkrAzimuthsRad;
		/* elevation angles */
		spkrElevs = config.spkrElevationsRad;
		// pack azims and elevs into directions [[az0, el0],[az1, el1],..]
		spkrDirs = [ spkrAzims, spkrElevs ].lace( spkrAzims.size + spkrElevs.size ).clump(2);
		// this is for the diametric decoder of satellites, so drop the subs
		// spkrDirs = spkrDirs.keep(numSatChans);
		// for diametric decoders
		spkrOppDict = config.spkrOppDict;

		"\n**** rig coordinates initialized ****".postln;
	}

	prInitDecoderAttributes {
		decAttributeList = config.decAttributeList;

		// build an Array from the above attributes
		decAttributes = decAttributeList.collect({ |attributes|
			IdentityDictionary(know: true).putPairs([
				\decName, attributes[0],
				\kind, attributes[1],
				\k, attributes[2],
				\dimensions, attributes[3],
				\arrayOutIndices, attributes[4],
				\numInputChans, attributes[5],
				\synthdefName, (attributes[0]).asSymbol,
				\decGain, attributes[6]
			])
		});

		"\n**** decoder attributes initialized **** ".postln;
	}

	/*	load speaker delays, distances, gains here because
	in the case of using kernels, it can be samplerate
	dependent, and so needs to happen after server has
	been initialized.
	*/
	prLoadDelDistGain { |delDistGainKey, completeCondition|
		fork {
			var key;

			// test that the kernel key returns a result
			key = if( usingKernels, {

				("trying del dist gains key: "++delDistGainKey).postln; // debug

				if( compDict.distances.includesKey(delDistGainKey) and:
					compDict.delays.includesKey(delDistGainKey) and:
					compDict.gains.includesKey(delDistGainKey),
					{
						delDistGainKey
					},{
						warn(format("Did not find a matching value in the compDict for the key %\nLoading default delays, distances and gains.\n", delDistGainKey));

						\default;
					}
				);
			},{ \default });

			spkrDists =	compDict.distances.at(key);
			spkrDels =	compDict.delays.at(key);
			spkrGains = compDict.gains.at(key);

			this.prCheckArrayData;

			postf("\n*** Delays, gains, distances loaded for:\t% ***", key);
			loadedDelDistGain = key;
			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

	checkKernelSpecAtSR { |relativePath|
		var result;
		result = File.exists(config.kernelsPath ++ this.sampleRate ++ "/" ++ relativePath);
		result.not.if{ warn(format("kernel spec entry % not found at this sample rate (%)", relativePath, this.sampleRate)) };
		^result
	}

	collectKernelCheckBoxAttributes {
		var attributes, sRate_pn;
		attributes = [];
		config.kernelsPath !? {
			sRate_pn = PathName( config.kernelsPath ++ this.sampleRate);
			config.kernelSpec.do{|k_attributes|

				// check that the kernel spec exists at this SR
				if( this.checkKernelSpecAtSR(k_attributes[0]), {

					// drop the kernel path and correction degree leaving only user-defined attributes
					k_attributes[1].do{ |att|
						if( attributes.includes(att).not, {attributes = attributes.add(att)} )
					};
				})
			};
		};
		^attributes
	}

	collectKernelPopUpAttributes {
		var popups;
		popups = [[]];

		config.kernelsPath !? {
			config.kernelSpec.do{|k_attributes|

				if( this.checkKernelSpecAtSR(k_attributes[0]), {
					var numPopUps = k_attributes[2].size;

					// grow popups array if needed
					if( popups.size < numPopUps, {
						(numPopUps - popups.size).do{popups = popups.add([])}
					});

					k_attributes[2].do{ |att, i|

						if( popups[i].includes(att).not, {
							popups[i] = popups[i].add(att);
						} )
					};
				});
			};
		};
		^popups
	}

	getKernelAttributesMatch { |selectedAttributes|
		var results, numMatches;
		// gather bools for each kernel spec whether all selected
		// attributes match, should only be one match
		results = config.kernelSpec.collect{ |k_attributes|
			var collAttributes, test1, test2;

			// collect menu and check box attributes for this kernel spec
			collAttributes = (k_attributes[1] ++ k_attributes[2]);

			// return true if all attributes match, false if not
			test1 = selectedAttributes.collect({ |att|
				collAttributes.includes(att)
			}).includes(false).not;
			test2 = collAttributes.collect({ |att|
				selectedAttributes.includes(att)
			}).includes(false).not;

			(test1 and: test2)
		};

		// postf("selectedAttributes:%\n", selectedAttributes);
		// postf("kernel matching results:\n%\n", results);

		numMatches = results.occurrencesOf(true);

		^case
		{numMatches == 1} { config.kernelSpec[results.indexOf(true)][0] }
		{numMatches == 0} { 0 }		// return 0 for no matches
		{numMatches > 1 } { -1 };	// return -1 for more than one match
	}

	formatKernelStatePost { |kPath, short=false|
		var rtn;
		^if( kPath != \basic_balance,
			{ var pn, category, attributes;
				pn = PathName(kPath.asString);
				category = pn.allFolders[pn.allFolders.size-2];
				attributes = config.kernelSpec.select({ |me|
					me.at(0) == (category ++ "/" ++ pn.allFolders.last ++ "/")

				}).at(0).drop(1).flat;

				short.if(
					{ format("%", attributes)},
					{ format("%\n%", category, attributes)}
				);
			},{
				\basic_balance.asString
			}
		);
	}

	prLoadDiametricDecoderSynth { |decSpecs|
		var arrayOutIndices, satOutbusNums, subOutbusNums, satDirections, subDirections;
		var matrix_dec_sat, matrix_dec_sub, decSynthDef;


		/* --satellites matrix-- */

		arrayOutIndices = decSpecs.arrayOutIndices;

		// get the other half of array indices for diametric opposites
		satOutbusNums = arrayOutIndices
		++ arrayOutIndices.collect({ |spkdex| spkrOppDict[spkdex] });

		// only need to provide 1/2 of the directions for diametric decoder
		satDirections = arrayOutIndices.collect({|busnum|
			switch(decSpecs.dimensions,
				2, spkrDirs[busnum][0], // 2D
				3, spkrDirs[busnum]     // 3D
			);
		});

		matrix_dec_sat = FoaDecoderMatrix.newDiametric(satDirections, decSpecs.k).shelfFreq_(shelfFreq);


		/* --subs matrix-- */

		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo decoder for subs or diammetric if there's an even number of them > 2
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});

			matrix_dec_sub = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k).shelfFreq_(shelfFreq);
				},
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});


		/* --build the synthdef-- */

		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);
			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal
			in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation

			// include shelf filter if the satellite
			// matrix has a shelf freq specified
			if( matrix_dec_sat.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					//matrix_dec_sat.shelfFreq,
					\shelfFreq.kr(matrix_dec_sat.shelfFreq), // see Control.names
					matrix_dec_sat.shelfK.at(0),
					matrix_dec_sat.shelfK.at(1)
				)
			});

			/* -- sat decode --*/

			// near-field compensate, decode, remap to rig
			satOutbusNums.do({ | spkdex, i |
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex).abs ), // NOTE .abs in case of negative distance
						matrix_dec_sat.matrix.fromRow(i)
					)
					* decSpecs.decGain.dbamp
				)
			});

			/* -- sub decode --*/

			if( numSubChans.even,
				{
					subOutbusNums.do({ | spkdex, i |
						Out.ar(
							out_busnum + spkdex,
							AtkMatrixMix.ar(
								FoaNFC.ar( in, spkrDists.at(spkdex).abs ),
								matrix_dec_sub.matrix.fromRow(i)
							)
							* subgain.dbamp
							* decSpecs.decGain.dbamp
						)
					})
				},
				// TODO:	this is a quick fix for non-even/non-diametric sub layout
				// 			Note this likely hasn't been used/tested because 113 specifies
				//			a false 2nd sub (i.e. always even)- note for single sub receiving W, boost by 3dB
				{
					if( numSubChans == 1,
						{   // No NFC for 1 sub
							Out.ar(
								out_busnum + subOutbusNums[0],
								// send W to subs, scaled by 3db, div by num of subs
								in[0] * 2.sqrt
								* subgain.dbamp
								* decSpecs.decGain.dbamp
							)
						},
						{
							subOutbusNums.do({ | spkdex, i |
								var nfc;
								nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs );
								Out.ar(
									out_busnum + spkdex,
									// send W to subs, div by num of subs
									nfc[0] * numSubChans.reciprocal
									* subgain.dbamp
									* decSpecs.decGain.dbamp
								)
							})
					})
				}
			);
		});

		decoderLib.add( decSynthDef ); // add the synth to the decoder library
		postf("% (diametric) added.\n", decSpecs.synthdefName);
	}

	// NOTE: arrayOutIndices is [half of horiz] ++ [all elevation dome] spkr indices
	prLoadDiametricDomeDecoderSynth { |decSpecs|
		var domeOutbusNums, domeOutbusNumsFullHoriz, partialDomeDirections, subOutbusNums, subDirections;
		var halfHorizDirections, posElevDirections, halfSphereDirections, lowerStartDex, domeEndDex, domeDecoderMatrix;
		var sphereDecoderMatrix, subDecoderMatrix, decSynthDef;
		var lowerMatrix, lowerSum, lowerComp;
		var lowerK = -8.0.dbamp;

		/* --dome satellites-- */

		domeOutbusNums = decSpecs.arrayOutIndices; // half horiz & full dome spkr indices

		// append other half of horiz outbus nums for collecting matrix outputs below
		// select busnums with 0 elevation then collect their opposite's busnum
		domeOutbusNumsFullHoriz = domeOutbusNums
		++ domeOutbusNums.select({|busnum| spkrDirs[busnum][1]==0 }).collect({
			|spkdex| spkrOppDict[spkdex]
		});

		partialDomeDirections = domeOutbusNums.collect({|busnum| spkrDirs[busnum] });
		halfHorizDirections = partialDomeDirections.select{|item| item[1]==0 };
		posElevDirections = partialDomeDirections.select{|item| item[1]>0 };
		halfSphereDirections = halfHorizDirections ++ posElevDirections;

		// model full diametric decoder, and matrix
		sphereDecoderMatrix = FoaDecoderMatrix.newDiametric(halfSphereDirections, decSpecs.k).shelfFreq_(shelfFreq);

		// truncate to just lower speakers to calculate compensation matrix...
		lowerStartDex = (halfHorizDirections.size*2) + posElevDirections.size;

		lowerMatrix = Matrix.with(sphereDecoderMatrix.matrix.asArray[lowerStartDex..]);
		lowerSum = (lowerK / posElevDirections.size) * lowerMatrix.sumCols;
		lowerComp = Matrix.with(
			Array.fill(halfHorizDirections.size,{lowerSum})		// add to first half of horiz
			++ Array.fill2D(posElevDirections.size,4,{0})		// add 0 to elevation spkrs
			++ Array.fill(halfHorizDirections.size,{lowerSum})	// add to second half of horiz
		);

		// truncate - to decoding matrix (raw matrix).. and add compensation matrix
		// note final matrix speaker order will be:
		// 		first half of horizontal speakers,
		// 		positive-elevation dome speakers,
		//		seccond half of horizontal speakers, opposites in same order of the first half
		domeEndDex = lowerStartDex - 1;
		// NOTE: this is a Matrix object, not an FoaDecoderMatrix object
		domeDecoderMatrix = Matrix.with(sphereDecoderMatrix.matrix.asArray[..domeEndDex]);
		domeDecoderMatrix = domeDecoderMatrix + lowerComp;

		/*----------*/
		/* --subs-- */
		/*----------*/
		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});

		/*------------------------*/
		/* --build the synthdef-- */
		/*------------------------*/
		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal
			in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation

			// include shelf filter on inpput or not. inferred from sphere FoaDecoderMatrix
			// because domeDecoderMatrix is actually just a Matrix object (no .shelfFreq)
			if( sphereDecoderMatrix.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					//sphereDecoderMatrix.shelfFreq,
					\shelfFreq.kr(sphereDecoderMatrix.shelfFreq),
					sphereDecoderMatrix.shelfK.at(0),
					sphereDecoderMatrix.shelfK.at(1)
				)
			});

			// near-field compensate, decode, remap to rig
			domeOutbusNumsFullHoriz.do({ |spkdex, i|
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex).abs ),
						domeDecoderMatrix.fromRow(i)
					)
					* decSpecs.decGain.dbamp
				)
			});

			// sub decode
			if( numSubChans.even, {
				subOutbusNums.do({ |spkdex, i|
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							FoaNFC.ar( in, spkrDists.at(spkdex).abs ),
							subDecoderMatrix.matrix.fromRow(i)
						)
						* subgain.dbamp
						* decSpecs.decGain.dbamp
					)
				})
				// quick fix for non-even/non-diametric sub layout
			},{
				if( numSubChans == 1,
					{   // No NFC for 1 sub
						Out.ar(
							out_busnum + subOutbusNums[0],
							// send W to subs, scaled by 3db, div by num of subs
							in[0] * 2.sqrt
							* subgain.dbamp
							* decSpecs.decGain.dbamp
						)
					},
					{
						// TODO: for odd multi-channel sub layouts
						// build panto decoder
						subOutbusNums.do({ | spkdex, i |
							var nfc;
							nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs );
							Out.ar(
								out_busnum + spkdex,
								// send W to subs, scaled by 3db, div by num of subs
								nfc[0] * numSubChans.reciprocal
								* subgain.dbamp
								* decSpecs.decGain.dbamp
							)
						})
				})
			}
			);
		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		postf("% (dome) added.\n", decSpecs.synthdefName);
	}


	prLoadSingleMatrixDecoder { |matrixPN|
		var subOutbusNums, subDirections, subDecoderMatrix;
		var path, name, matrix, ambiOrder, decSynthDef;


		/* --load decoder coefficient matrix-- */

		path = matrixPN.fullPath;
		name = matrixPN.fileNameWithoutExtension.asSymbol;

		matrix = Matrix.with(FileReader.read(path).asFloat);
		// determine order from matrix (must be 'full' order)
		ambiOrder = matrix.cols.sqrt.asInteger - 1;

		postf("Loading matrix decoder:\t\t\t%, order %\n", name, ambiOrder);


		/* --subs-- */

		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		// assume the layout is regular in this case
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});


		/* --build the synthdef-- */

		decSynthDef = SynthDef( name, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, freq=400, gate=1,
			shelfFreq = 400;  // shelfFreq defined but not used in single matrix decoder
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			in = In.ar(in_busnum, (ambiOrder + 1).squared) * env;
			(ambiOrder == 1).if{ // transform only supported at first order atm
				in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation
			};

			// near-field compensate, decode, remap to rig
			// it's expected that the matrix has outputs for all speakers in the rig,
			// even if some are zeroed out in the matrix
			numSatChans.do({ | spkdex, i |
				var nfc;
				(ambiOrder == 1).if( // nfc only supported at first order atm
					{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs ) },
					{ nfc = in }
				);
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						nfc, matrix.fromRow(i)
					)
				)
			});

			// sub decode
			if( numSubChans.even, {
				subOutbusNums.do({ |spkdex, i|
					var nfc;
					(ambiOrder == 1).if( // nfc only supported at first order atm
						{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs ) },
						{ nfc = in }
					);
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							nfc, subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				// quick fix for non-even/non-diametric sub layout
			},{
				if( numSubChans == 1,
					{   // No NFC for 1 sub
						Out.ar(
							out_busnum + subOutbusNums[0],
							// send W to subs, scaled by 3db, div by num of subs
							in[0] * 2.sqrt
						) * subgain.dbamp
					},
					{
						subOutbusNums.do({ | spkdex, i |
							var nfc;
							(ambiOrder == 1).if( // nfc only supported at first order atm
								{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs ) },
								{ nfc = in }
							);
							Out.ar(
								out_busnum + spkdex,
								// send W to subs, scaled by 3db, div by num of subs
								nfc[0] * numSubChans.reciprocal
							) * subgain.dbamp
						})
				})
			}
			);

		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		matrixDecoderNames = matrixDecoderNames.add(name);
	}

	prLoadDualMatrixDecoder { |decName, matrixPN_LF, matrixPN_HF|
		var subOutbusNums, subDirections, subDecoderMatrix;
		var lf_array, hf_array;
		var path_lf, path_hf, name, matrix_lf, matrix_hf, ambiOrder, decSynthDef;

		/*-------------------------------------*/
		/* --load decoder coefficient matrix-- */
		/*-------------------------------------*/
		path_lf = matrixPN_LF.fullPath;
		path_hf = matrixPN_HF.fullPath;
		name = decName.asSymbol;

		lf_array = FileReader.read(path_lf).asFloat;
		hf_array = FileReader.read(path_hf).asFloat;

		// load decoder coefficient matrix
		matrix_lf = Matrix.with(lf_array);
		matrix_hf = Matrix.with(hf_array);

		// determine order from matrix (must be 'full' order)
		// NOTE: addition of matricies is a quick way to check whether they are the same
		ambiOrder = (matrix_lf + matrix_hf).cols.sqrt.asInteger - 1;

		postf("Loading dual matrix decoder:\t%, order %\n", name, ambiOrder);

		/*----------*/
		/* --subs-- */
		/*----------*/
		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		// assume the layout is regular in this case
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});

		/*------------------------*/
		/* --build the synthdef-- */
		/*------------------------*/
		decSynthDef = SynthDef( name, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, shelfFreq=400, gate=1;
			var in, env, sat_out, sub_out;
			var k = -180.dbamp; // RM-shelf gain (for cross-over)

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			// read in (ambisonic 'b-format')
			in = In.ar(in_busnum, (ambiOrder + 1).squared) * env;

			// transform and physcoshelf only supported at first order atm
			(ambiOrder == 1).if{
				in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation
			};

			// near-field compensate, decode, remap to rig
			// it's expected that the matrix has outputs for all speakers in the rig,
			// even if some are zeroed out in the matrix
			numSatChans.do({ | spkdex, i |
				var nfc;

				(ambiOrder == 1).if( // nfc only supported at first order atm
					{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs ) },
					{ nfc = in }
				);
				// LF
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						RMShelf2.ar( nfc, shelfFreq, k ),
						matrix_lf.fromRow(i)
					)
				);
				// HF
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						RMShelf2.ar( nfc, shelfFreq, -1 * k ),
						matrix_hf.fromRow(i)
					)
				);
			});

			// sub decode
			if( numSubChans.even, {

				subOutbusNums.do({ |spkdex, i|
					var nfc;
					(ambiOrder == 1).if( // nfc only supported at first order atm
						{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
						{ nfc = in }
					);
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							nfc, subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					);
				})
				// quick fix for non-even/non-diametric sub layout
			},{
				if( numSubChans == 1,
					{   // No NFC for 1 sub
						Out.ar(
							out_busnum + subOutbusNums[0],
							// send W to subs, scaled by 3db, div by num of subs
							in[0] * 2.sqrt
						) * subgain.dbamp
					},
					{
						subOutbusNums.do({ | spkdex, i |
							var nfc;
							(ambiOrder == 1).if( // nfc only supported at first order atm
								{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex).abs ) },
								{ nfc = in }
							);
							Out.ar(
								out_busnum + spkdex,
								// send W to subs, scaled by 3db, div by num of subs
								nfc[0] * numSubChans.reciprocal
							) * subgain.dbamp
						})
				})
			}
			);

		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		matrixDecoderNames = matrixDecoderNames.add(name);
	}

	prLoadDiscreteRoutingSynth { |decSpecs|

		decoderLib.add(
			SynthDef( decSpecs.synthdefName, {
				arg in_busnum, out_busnum = 0, fadeTime = 0.3, subgain = 0, gate = 1;
				var in, env, out;
				var azims, elevs, directions, encoder, bf, decoders, sub_decodes;

				env = EnvGen.ar(
					Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
					gate, doneAction: 2 );

				in = In.ar(in_busnum, decSpecs.numInputChans)  * env;
				decSpecs.arrayOutIndices.do{ |outbus, i|
					Out.ar(outbus + out_busnum, in[i] * decSpecs.decGain.dbamp)
				};

				// subs were considered at one point, but decided discrete routing should be
				// direct speaker feeds only
				// // TODO: confirm this BF encode-decode approach
				// // SUBS
				// if( config.numSubChans > 1, {
				// 	// send the satellite signals to the sub(s) via planewave encoding,
				// 	// then decode the b-format to mono sub decoders
				// 	azims = decSpecs.arrayOutIndices.collect{|outdex, i| config.spkrAzimuthsRad[outdex] };
				// 	elevs = decSpecs.arrayOutIndices.collect{|outdex, i| config.spkrElevationsRad[outdex] };
				// 	directions = [azims, elevs].lace(azims.size + elevs.size).clump(2);
				//
				// 	encoder = FoaEncoderMatrix.newDirections(directions, nil); // nil = planewave encoding
				// 	bf = FoaEncode.ar(in, encoder);
				//
				// 	// Mono decode for each sub
				// 	decoders = config.numSubChans.collect{|i|
				// 		FoaDecoderMatrix.newMono(
				// 			config.spkrAzimuthsRad[config.numSatChans+i], // sub azimuth
				// 			0,  // sub elevation - always 2D
				// 			0.5 // cardiod decode
				// 		);
				// 	};
				//
				// 	sub_decodes = decoders.collect{ |decoder| FoaDecode.ar(bf, decoder); };
				// 	// TODO add crossover to discrete routing
				// 	// see commented-out code below for crossover scheme to be added
				// 	config.numSubChans.do{|i| Out.ar(config.numSatChans+i, sub_decodes[i])};
				//
				// 	},{
				// 		if( config.numSubChans == 1, {
				// 			Out.ar( config.numSatChans, Mix.ar(in) * decSpecs.numInputChans.reciprocal )
				// 		})
				// 	}
				// );
			});
		);

		// debug
		postf("% (discrete) added.\n", decSpecs.synthdefName);
	}

	// load every possible decoding SynthDef based on decAttList
	prLoadSynthDefs { |finishLoadCondition|

		decoderLib = CtkProtoNotes();

		/* build and load decoders specified in config*/
		decAttributes.do{ |decSpecs|
			switch( decSpecs.kind,
				\diametric,	{ this.prLoadDiametricDecoderSynth(decSpecs)},
				\dome,		{ this.prLoadDiametricDomeDecoderSynth(decSpecs)},
				\discrete,	{ this.prLoadDiscreteRoutingSynth(decSpecs)	}
			);
		};

		/* build and load decoders in matrix folder, if any */
		decoderMatricesPath !? {
			decoderMatricesPath.entries.do{ |bandType|

				bandType.isFolder.if{
					switch(bandType.folderName,

						"single", {
							bandType.filesDo{ |fl|
								// postf( "Found single matrix decoder:\t%\n",
								// fl.fileNameWithoutExtension );
								this.prLoadSingleMatrixDecoder(fl); // fl is pathname to matrix file
							}
						},

						"dual", {
							bandType.folders.do{ |decNameFldr|
								var pn_lf, pn_hf;
								// postf( "Found dual matrix decoder:\t\t%\n",
								// decNameFldr.folderName);
								(decNameFldr.files.size == 2).if({
									decNameFldr.filesDo{ |fl|
										var fn;
										fn = fl.fileNameWithoutExtension;
										case
										{ fn.endsWith("LF") } { pn_lf = fl }
										{ fn.endsWith("HF") } { pn_hf = fl };
									};

									this.prLoadDualMatrixDecoder(
										decNameFldr.folderName, pn_lf, pn_hf
									);
								},{
									warn(format("found a count of files other than 2 in \n%.\nExpecting 2 files: a HF matris and LF matrix. Skipping this folder.", decNameFldr))
								}
								);
							};
						}
					);
				}
			};
		};

		/* library of synths other than decoders */
		// one delay_gain_comp for every speaker output
		// signal order to comp stage is assumed to be satellites, subs, stereo
		synthLib = CtkProtoNotes(

			SynthDef(\delay_gain_comp, { arg in_busnum=0, out_busnum=0, masterAmp = 1.0, xover_hpf = 60, xover_lpf = 60;
				var in_sig, sat_sig, stereo_sig, sub_sig, subs_xover, sats_xover, subs_delayed, sats_delayed, outs;

				sat_sig = In.ar(in_busnum, numSatChans) * spkrGains.keep(numSatChans).dbamp;
				sub_sig = In.ar(in_busnum+numSatChans, numSubChans)
				* spkrGains[numSatChans..(numSatChans+numSubChans-1)].dbamp;
				stereo_sig = In.ar(in_busnum+totalArrayChans);

				subs_xover = LPF.ar( LPF.ar(sub_sig, xover_lpf), xover_lpf);
				sats_xover = HPF.ar( HPF.ar(sat_sig, xover_hpf), xover_hpf);

				sats_delayed = DelayN.ar( sats_xover,
					spkrDels.maxItem, spkrDels[0..(numSatChans-1)] );
				subs_delayed = DelayN.ar( subs_xover,
					spkrDels.maxItem, spkrDels[numSatChans..(numSatChans+numSubChans-1)] );


				// Note: no stereo delay/gain comp atm
				outs = sats_delayed ++ subs_delayed ++ stereo_sig;
				ReplaceOut.ar(out_busnum, outs * masterAmp);
			})
		);

		finishLoadCondition.test_(true).signal;
	}

	prInitSLHW { |initSR|
		slhw = SoundLabHardware.new(
			false, 						//useSupernova
			config.fixAudioInputGoingToTheDecoder, //fixAudioInputGoingToTheDecoder
			config.useFireface,			//useFireface
			config.midiDeviceName,		//midiDeviceName
			config.midiPortName,		//midiPortName
			config.cardNameIncludes,	//cardNameIncludes
			config.jackPath, 			//jackPath
			numHardwareIns, 			//serverIns
			numHardwareOuts * 3, 		//serverOuts
			numHardwareOuts, 			//numHwOutChToConnectTo
			numHardwareIns, 			//numHwInChToConnectTo
			config.firefaceID, 			//firefaceID
			config.whichMadiInput, 		//whichMadiInput
			config.whichMadiOutput, 	//whichMadiOutput
			config.audioDeviceName      //audioDeviceName
		);
		// slhw = SoundLabHardware.new(false,true,false,nil,nil,"/usr/local/bin/jackdmp",32,128); //for osx
		slhw.postln;
		slhw.startAudio(
			initSR, 				//newSR
			config.hwPeriodSize, 	//periodSize
			config.hwPeriodNum, 	//periodNum
		);
		slhw.addDependant(this);
	}

	prInitDefaultHW { |initSR|
		var so;
		// debug
		"initializing default hardware".postln;

		server = server ?? Server.default;
		server !? { server.serverRunning.if{ server.quit} };
		"REBOOTING".postln;

		so = server.options;
		so.sampleRate = initSR ?? 48000;
		so.memSize = 8192 * 16;
		so.numWireBufs = 64*8;
		so.device = "JackRouter";
		// numHardwareOuts*3 to allow fading between settings,
		// routed to different JACK busses
		so.numOutputBusChannels = numHardwareOuts * 3;
		so.numInputBusChannels = numHardwareIns;

		// the following will otherwise be called from update: \audioIsRunning
		server.waitForBoot({
			rbtTryCnt = rbtTryCnt+1;
			// in case sample rate isn't set correctly the first time (SC bug)
			if( server.sampleRate.asInteger == initSR, {
				rbtTryCnt = 0;
				this.prLoadServerSide(server);
			},{ fork{
				1.5.wait;
				"reboot sample rate doesn't match requested, retrying...".postln;
				if(rbtTryCnt < 3,
					{ this.prInitDefaultHW(initSR) }, // call self
					{ this.changed(\reportStatus, "Error trying to change the sample rate after 3 tries!".warn)}
				)
			}}
			)
		});
	}

	prFindKernelDir { |kernelName|
		var kernelDir_pn;
		kernelDirPathName.folders.do({ |sr_pn|
			if( sr_pn.folderName.asInteger == server.sampleRate.asInteger, {
				sr_pn.folders.do({ |kernel_pn|
					if( kernel_pn.folderName.asSymbol == kernelName, {
						("found kernel match"+kernel_pn).postln;
						kernelDir_pn = kernel_pn; });
				});
			})
		});
		^kernelDir_pn
	}

	// parse text file for delays, distances, gains
	// expects individual .txt files for each
	// with \n -separated float values
	prParseFile { |pathname|
		var data;
		data = [];
		File.use(pathname.fullPath, "r", { |f|
			var str, splt;
			str = f.contents;
			// divide file by newlines
			splt = str.split($\n );
			splt.do({|val, i|
				// filter out spurious newlines at the end
				if( val.contains("."), { // floats will have decimal
					// debug.if{postf("%, %; ", i, val.asFloat)};
					debug.if{postf("% ", i)};
					data = data.add(val.asFloat);
				})
			});
			^data;
		});
	}

	prCheckArrayData {
		postf(
			"Checking array data...\nThese should equal % (numSatChans + numSubChans)\n[%, %, %, %, %, %]\n",
			numSatChans+numSubChans, spkrAzims.size, spkrElevs.size, spkrDists.size,
			spkrDels.size, spkrGains.size, spkrDirs.size
		);

		if (
			spkrAzims.size == spkrElevs.size and:
			spkrElevs.size == spkrDists.size and:
			spkrDists.size == spkrDels.size and:
			spkrDels.size == spkrGains.size and:
			spkrGains.size == totalArrayChans,
			{ "OK: Array sizes of rig dimensions match!".postln },
			{ "Mismatch in rig dimension array sizes!".warn }
		);

		"\n**** Speaker Gains, Distances, Delays ****".postln;
		"Chan: Gain Distance Delay".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("%:\t%\t%\t%\n",
				i, spkrGains[i], spkrDists[i], spkrDels[i]
			)
		});

		"\n**** Speaker Directions ****".postln;
		"Chan: Azimuth Elevation".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("%:\t %\n", i, spkrDirs[i].raddeg)
		});
	}

	createRecompileWindow {|bounds|
		recompileWindow = Window.new(format("% - recompile window", this.class.name), bounds).front;
		recompileWindow.layout_(VLayout(
			nil,
			Button()
			.states_([
				["Recompile class library", Color.red(1, 0.7), Color.grey(0, 0.1)],
			])
			.font_(Font(size: 32))
			.action_({
				recompileWindow.close;
				{thisProcess.recompile}.defer(0.1); //wait for the window to close before recompiling
			}),
			nil
		));
	}


	//force = true will make the cleanup run immediately
	//that means no sound fadeout
	//but can be run from library shutdown

	cleanup  {|force = false|
		if(force, {format("%: starting immediate cleanup", this.class.name).warn});
		ShutDown.remove(forceCleanupFunc);

		[OSCdef(\clipListener), OSCdef(\reloadGUI)].do(_.free);
		gui !? {gui.cleanup};
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide; 			// frees jconvs
		slhw !? {slhw.stopAudio(force)};
		recompileWindow !? {{recompileWindow.close}.defer};
	}

	free {|force = false| this.cleanup(force)}
}

/*
------ TESTING ---------
(
s.options.numOutputBusChannels_(32);
s.options.device_("JackRouter");
s.options.numWireBufs_(64*8);
Jconvolver.jackScOutNameDefault = "scsynth:out";
Jconvolver.executablePath_("/usr/local/bin/jconvolver");
// make sure Jack has at least [3x the number of your hardware output busses] virtual ins and outs
// if using the convolution system and you intend to switch between kernel sets
// and default (no convolution) settings

// InterfaceJS.nodePath = "/usr/local/bin/node";

//configFileName="CONFIG_205.scd", useKernels=true, loadGUI=true, useSLHW=true
// l = SoundLab(configFileName:"CONFIG_TEST_205.scd", useKernels:false, loadGUI:true, useSLHW:false)
// l = SoundLab(configFileName:"CONFIG_TEST_117.scd", useKernels:false, loadGUI:true, useSLHW:false)
l = SoundLab(configFileName:"CONFIG_TEST_117.scd", useKernels:true, loadGUI:true, useSLHW:false)
// l = SoundLab(configFileName:"CONFIG_TEST_117.scd", useKernels:false, loadGUI: false, useSLHW:false)
// l = SoundLab(configFileName:"CONFIG_TEST_113.scd", useKernels:false, loadGUI:true, useSLHW:false)
)

l.buildGUI
l.gui = nil
l.gui.cleanup

Open in browser:
http://localhost:8080/

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

// test signal
s.meter
// test b-format
x = {Out.ar(l.curDecoderPatch.inbusnum, 4.collect{PinkNoise.ar * SinOsc.kr(rrand(3.0, 5.0).reciprocal).range(0.0, 0.15)})}.play
// test discrete
x = {Out.ar(l.curDecoderPatch.inbusnum, 5.collect{PinkNoise.ar * SinOsc.kr(rrand(3.0, 5.0).reciprocal).range(0.0, 0.15)})}.play
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
l = SoundLab(useKernels:false, useSLHW:false)
l.startNewSignalChain(\Sphere_12ch_first_dual)
l.startNewSignalChain(\Dodec)
l.startNewSignalChain(\Quad_Long)
l.startNewSignalChain(\Hex)
l.startNewSignalChain(\Thru_All, \NA)
l.decoderLib.dict.keys

l.sampleRate_(44100)
l.sampleRate_(96000)


// testing slhw
l = SoundLab(useSLHW:false, useKernels:false)

// testing gui
l = SoundLab(48000, loadGUI:true, useSLHW:false, useKernels:false)

x = {Out.ar(0, 4.collect{PinkNoise.ar * SinOsc.kr(rrand(3.0, 5.0).reciprocal).range(0, 0.35)})}.play


InterfaceJS.nodePath = "/usr/local/bin/node"
l = SoundLab(useSLHW:false)
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