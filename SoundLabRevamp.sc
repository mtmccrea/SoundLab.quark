SoundLabRevamp {
	// copyArgs
	var <initSR, <loadGUI, <usingSLHW, <>usingKernels;

	var <>xfade = 0.2, <>defaultDecoderName, <>defaultKernel, <>kernelDirPath, <>debug=true;
	var <globalAmp, <numSatChans, <numSubChans, <totalArrayChans, <numKernelChans;
	var <numHardwareOuts, <numHardwareIns, <hwInCount, <hwInStart;

	var <server, <gui, <curKernel, <stereoActive, <isMuted, <isAttenuated, <stateLoaded;
	var <clipMonitoring, <curDecoderPatch, rbtTryCnt;
	var <clipListener, <reloadGUIListener;
	var <patcherGroup, <stereoPatcherSynths, <satPatcherSynths, <subPatcherSynths;
	var <monitorGroup_ins, <monitorGroup_outs, <monitorSynths_outs, <monitorSynths_ins;
	var <jconvolver, <nextjconvolver, <nextKernel;

	// SoundLabUtils (SoundLab extension)
	var <compDict, <decAttributes, <decAttributeList;
	var <spkrAzims, <spkrElevs, <spkrDirs, <spkrOppDict, <spkrDels, <spkrGains, <spkrDists;
	var <decoderLib, <synthLib;
	var <slhw;

	*new { |initSR=96000, loadGUI=true, useSLHW=true, useKernels=true|
		^super.newCopyArgs(initSR, loadGUI, useSLHW, useKernels).init;
	}

	// NOTE: Jack will create numHarwareOuts * 3 for routing to
	// system hardware (0..numHardwareOuts-1)
	// jconvolver 1 (numHardwareOuts..numHardwareOuts*2-1)
	// jconvolver 2 (numHardwareOuts..numHardwareOuts*3-1)
	// Thus SC will boot with s.option.numOutputBusChannels = numHarwareOuts * 3.
	init {
		// defaults
		numHardwareOuts = 32;	// TODO import from config
		numHardwareIns = 32;	// TODO import from config
		kernelDirPath = PathName.new(Platform.resourceDir ++ "/sounds/SoundLabKernelsNew/");
		defaultDecoderName = \Sphere_24ch_first_dual;  // synthDef name // TODO infer from defaults in config file
		defaultKernel = \decor_700;
		// NOTE: speaker order is assumed to be satellites, subs, stereo (optional)
		// 		 see prLoadSynthDefs for how channel mappings are used
		numSatChans = 24;
		numSubChans = 4;
		totalArrayChans = numSatChans+numSubChans;	// stereo not included
		numKernelChans = totalArrayChans; 	// TODO: confirm this approach

		globalAmp = 0.dbamp;
		stereoActive = false;
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
			server.options.numOutputBusChannels_(numHardwareOuts*3).numInputBusChannels_(numHardwareIns);
			server.options.numWireBufs_(64*8);

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

	// this happens after hardware is intitialized abd server is booted
	prLoadServerSide { |argServer|
		var loadCondition;

		loadCondition = Condition(false);
		// debug
		postln("Loading Server Side ...
			loading synths, intializing channel counts, groups, and busses.");

		server = argServer ?? {
			"server defaulting because none provided -prLoadServerSide".warn;
			Server.default
		};

		server.doWhenBooted({
			fork {
				// LOAD JCONVOLVER
				jconvolver !? {jconvolver.free}; // kill any running Jconvolvers
				if( usingKernels, {
					nextKernel = curKernel ?? defaultKernel;
					this.loadJconvolver(nextKernel, loadCondition); // this sets nextjconvolver var
				},{ loadCondition.test_(true).signal });
				loadCondition.wait; "passed loading kernels".postln;
				loadCondition.test_(false).signal; // reset the condition to hang when needed later

				// LOAD DELAYS AND GAINS
				this.prLoadDelDistGain(
					// nextjconvolver var set in loadJconvolver method
					if( usingKernels, {nextKernel}, {\default} );
				);

				// LOAD SYNTHDEFS
				// speaker dels, dists, gains must be written before loading synthdefs
				// NOTE: this needs to happen for every new kernel being loaded
				this.prLoadSynthDefs(loadCondition);
				loadCondition.wait; // waiting on prLoadSynthDefs to signal
				"SynthDefs loaded".postln;
				loadCondition.test_(false).signal; // reset the condition to hang when needed later
				server.sync; // sync to let all the synths load

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
				patcherGroup = CtkGroup.play( addAction: \after, target: monitorGroup_ins, server: server );
				server.sync;

				[monitorGroup_ins, monitorGroup_outs].do(_.play);
				server.sync;
				patcherGroup.play;
				server.sync;
				// debug
				postf("monitorGroup_ins group: %\nmonitorGroup_out group: %\npatcherGroup group: %\n",
					monitorGroup_ins.node, monitorGroup_outs.node, patcherGroup.node);

				// PATCHERS
				// patcherOutBus is only satellites + stereo, NO subs
				// outputs don't change on the patcher synths, only the inputs
				// i.e. there's a patcher synth "attached" to each speaker output
				satPatcherSynths = 3.collect({|j|
					var start_in_dex;
					start_in_dex = j * numHardwareOuts;
					numSatChans.collect({ |i|
						synthLib[\patcher].note( target: patcherGroup )
						.in_bus_(start_in_dex+i).out_bus_(start_in_dex+i)
						.play
					})
				}).flat;
				server.sync;

				subPatcherSynths =  3.collect({|j|
					var start_in_dex;
					start_in_dex = j * numHardwareOuts;
					numSubChans.collect({ |i|
						synthLib[\patcher].note( target:patcherGroup )
						.in_bus_(start_in_dex+numSatChans+i)
						.out_bus_(start_in_dex+numSatChans+i)
						.play
					})
				}).flat;
				server.sync;

				hwInStart = server.options.numOutputBusChannels;

				stereoPatcherSynths = 2.collect({|i|
					synthLib[\patcher].note( target: patcherGroup )
					.in_bus_(hwInStart+i)
					.out_bus_(numSatChans+numSubChans+i) // atm stereo always goes to first set of outs
					.play
				});
				server.sync;

				// debug
				stereoPatcherSynths.do({|synth|synth.isPlaying.postln});
				stereoPatcherSynths.do({|synth|synth.node.postln});
				while(	{stereoPatcherSynths.collect({|synth|synth.isPlaying}).includes(false)},
					{"waiting on stereo patchers".postln; 0.02.wait;}
				);
				0.2.wait; // TODO find a better solution here
				if(stereoActive.not, {stereoPatcherSynths.do(_.pause)});

				// CLIP MONITORS
				// - initialized, not played yet
				monitorSynths_ins = numHardwareIns.collect{ |i|
					synthLib[\clipMonitor].note(target: monitorGroup_ins)
					.in_bus_(hwInStart + i)
				};
				monitorSynths_outs = (numHardwareOuts*3).collect{ |i|
					// note: in_bus index is a hardware out channel
					synthLib[\clipMonitor].note(target: monitorGroup_outs).in_bus_(i)
				};
				server.sync; // to make sure stereo patchers have started

				this.loadState(loadCondition); // start monitors, patchers, decoder
			}
		});
	}

	loadState { |loadCondition|
		// debug
		"loading SoundLab state".postln;
		fork {
			// START DECODER
			// TODO how does curDecoderPatch persist between reboots?
			this.startDecoder(
				if( curDecoderPatch.notNil,
					{curDecoderPatch.decoderName},
					{defaultDecoderName}
				),
				loadCondition
			);

			loadCondition.wait; "decoder started".postln;
			usingKernels.if{
				jconvolver !? {jconvolver.free};
				jconvolver = nextjconvolver;
				curKernel = nextKernel;
			};

			clipMonitoring.if{this.clipMonitor_(true)};
			stateLoaded = true;
			this.changed(\stateLoaded);
			if(loadGUI, {this.buildGUI});

		};
	}

		// cleanup server objects to be reloaded after reboot
	prClearServerSide {
		fork {
			curDecoderPatch.free(xfade);
			curDecoderPatch = nil;
			xfade.wait;
			[ patcherGroup, monitorGroup_ins, monitorGroup_outs ].do(_.free);

			jconvolver !? {jconvolver.free};
			nextjconvolver !? {nextjconvolver.free};

			// // TODO: can kernelDict be replaced by an array?
			// kernelDict !? {
			// 	kernelDict.keysValuesDo({ |key|
			// 		kernelDict.removeAt(key);
			// 	});
			// };
			stateLoaded = false;
		}
	}

	startDecoder  { |newDecSynthName, loadCondition|
		var cond, newDecoderPatch, cur_decoutbus, new_decoutbus;
		cond = Condition(false);
		fork {
			// debug
			"starting decoder".postln;

			// select which of the 3 out groups to send decoder/correction to
			new_decoutbus = if(usingKernels, {
				if(curDecoderPatch.notNil,
					{	// this is the outbus being replaced..
						cur_decoutbus = curDecoderPatch.outbusnum
						// jump to next set of outputs, always numHardwareOuts or (numHardwareOuts*2)
						(cur_decoutbus + numHardwareOuts).wrap(1, numHardwareOuts*2)
					},{
						numHardwareOuts // first set of outputs routed to kernel
					}
				);
				},{0}	// 0 for no kernels
			);

			// TODO: confirm decoderLib[newDecSynthName] exists

			newDecoderPatch = SoundLabDecoderPatch(this,
				newDecSynthName,
				if( stereoActive, {hwInStart+2}, {hwInStart}), // decoder inbusnum
				new_decoutbus,		// decoder outbusnum
				cond				// finishCondition
			);
			cond.wait;

			// debug
			"newDecoderPatch initialized".postln;

			curDecoderPatch !? {curDecoderPatch.free(xfade: xfade)};
			newDecoderPatch.play(xfade: xfade);

			xfade.wait;
			curDecoderPatch = newDecoderPatch;
			// TODO update changed \decoder message
			this.changed(\decoder,
				decAttributes.select({|attDict| attDict.synthdefName == newDecSynthName.asSymbol})
			);
			loadCondition.test_(true).signal;

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
		numFoundKernels = 0, numtries = 50, trycnt=0, newjconvolver;
		fork {
			block { |break|
				kernelDir_pn = this.prFindKernelDir(newKernel);
				kernelDir_pn ?? {
					this.changed(\reportStatus, warn("Kernel name not found: "++newKernel));
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
				Jconvolver.jackScOutNameDefault = "scsynth:out";
				Jconvolver.executablePath_("/usr/local/bin/jconvolver");

				// TODO: check if Jconvolver is already running, if so quit it
				// TODO: check autoConnectToScChannels: is correct
				Jconvolver.createSimpleConfigFileFromFolder(
					kernelFolderPath: k_path, partitionSize: partSize,
					maxKernelSize: k_size, matchFileName: "*.wav",
					autoConnectToScChannels: 32, autoConnectToSoundcardChannels: 0
				);

				newjconvolver = Jconvolver.newFromFolder(k_path);

				while( {newjconvolver.isRunning.not and: (trycnt < numtries)}, {
					trycnt = trycnt+1;
					(timeout/numtries).wait;
				});
				newjconvolver.isRunning.not.if{
					warn("JConvolver didn't start after waiting "++timeout++" seconds.");
					this.setNoKernel;
					break.();
				};
				nextjconvolver = newjconvolver;
			};
			completeCondition.test_(true).signal;
		}
	}

	// ------------------------------------------------------------------
	// -------- State Setters/Getters -----------------------------------
	// ------------------------------------------------------------------

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

	sampleRate { if(usingSLHW, {^slhw.server.sampleRate}, {^server.sampleRate}) }

	setNoKernel {
		curKernel = \no_correction; //nil; // TODO rethink this
		usingKernels = false;
		this.changed(\kernel, curKernel);
		nextjconvolver !? {nextjconvolver.free; nextjconvolver = nil;}
	}

	free { this.cleanup }

	cleanup  {
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide;
		clipListener.free;
		reloadGUIListener.free;
		if ( gui.notNil, {gui.cleanup} );
		// TODO: cleanup Jconvolver
	}
}
/* ------ TESTING ---------
l = SoundLabRevamp(48000, useSLHW:false, useKernels:true)
l.startDecoder(\Sphere_12ch_first_dual)
"~~~~~~".postln
l.decoderLib.dict.keys

l.free

InterfaceJS.nodePath = "/usr/local/bin/node"
l = SoundLab(48000, useSLHW:false)
l.cleanup
l.jconvolver
l.gui
l.jconvolver.free
l.gui.buildControls
l.kernelDict

s.scope(2,3)