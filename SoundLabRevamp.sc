SoundLabRevamp {
	// copyArgs
	var <initSR, <loadGUI, <usingSLHW, <>usingKernels;

	var <globalAmp, <>defaultDecName, <>defaultKernel;
	var <stereoActive, <isMuted, <isAttenuated, <stateLoaded;
	var <clipMonitoring, <numSatChans, <numSubChans, <totalArrayChans;
	var <>kernelDirPath, <loadCond, rbtTryCnt;
	var <clipListener, <reloadGUIListener;

	var <comDict, <decAttributes, <decAttributeList;
	var <spkrAzims, <spkrElevs, <spkrDirs, <spkrOppDict;
	var <decoderLib, <decoderPatch;
	var <numHardwareOuts, <numHardwareIns, <hwInCount;

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
		defaultDecName = \Sphere_24ch_First_dual;  // synthDef name
		defaultKernel = \decor_700;
		// NOTE: speaker order is assumed to be satellites, subs, stereo (optional)
		// 		 see prLoadSynthDefs for how channel mappings are used
		numSatChans = 24;
		numSubChans = 4;
		totalArrayChans = numSatChans+numSubChans; // stereo not included

		globalAmp = 0.dbamp;
		stereoActive = false;
		isMuted = false;
		isAttenuated = false;
		stateLoaded = false;
		clipMonitoring = false;

		this.prInitRigDimensions;
		this.prInitDecoderAttributes;

		loadCond = Condition(false);
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
		debug.if{
			postln("Loading Server Side ...
			loading synths, intializing channel counts, groups, and busses.")
		};
		server = argServer ??  {"server defaulting because none provided".warn; Server.default};
		server.doWhenBooted({
			fork {
				hwInStart = server.options.numOutputBusChannels;

				this.prLoadDelDistGain( if(usingKernels, {curKernel ?? defaultKernel},{nil}) );
				this.prLoadSynthDefs;
				loadCond.wait;  // waiting on prLoadSynthDefs to signal
				loadCond.test_(false).signal; // reset the condition to hang when needed later

				if(usingKernels, {
					curKernel = curKernel ?? defaultKernel;
					this.loadKernel(curKernel, loadCond)
				},{ loadCond.test_(true).signal });
				loadCond.wait;
				loadCond.test_(false).signal; // reset the condition to hang when needed later
				"passed loading kernels".postln;
				jconvolver !? { jconvolver.free };
				jconvolver = new_jconvolver;



				// group for clip monitoring synths
				monitorGroup_ins = CtkGroup.new( addAction: \head, target: 1);
				monitorGroup_outs = CtkGroup.new( addAction: \tail, target: 1 );
				// group for patching synths
				patcherGroup = CtkGroup.new( addAction: \after, target: monitorGroup_ins );

				/* proper NODE TREE order
				monitorGroup_ins
				SoundLabDecoderPatch.group
					>decoder (ambi, thru, etc.)
					>delgainCompSynth
				patcherGroup
				monitorGroup_outs
				*/

				this.loadState;
			}
		});
	}

	loadState {
		debug.if{postln("loading SoundLab state")};
		// TODO remove fork
		fork {
			monitorGroup_ins.play;
			monitorGroup_outs.play; 0.15.wait;
			patcherGroup.play; 0.15.wait;

			// patcherOutBus is only satellites + stereo, NO subs
			// outputs don't change on the patcher synths, only the inputs
			// i.e. there's a patcher synth "attached" to each speaker output
			satPatcherSynths = 3.collect({|j|
				var start_in_dex;
				start_in_dex = j * numHardwareOuts;
				numSatChans.collect({ |i|
					synthLib[\patcher].note(
						target: patcherGroup)
					.in_bus_(start_in_dex+i).out_bus_(start_in_dex+i)
					.play
				})
			}).flat;

			subPatcherSynths =  3.collect({|j|
				var start_in_dex;
				start_in_dex = j * numHardwareOuts;
				numSubChans.collect({ |i|
					synthLib[\patcher].note(
						target:patcherGroup)
					.in_bus_(start_in_dex+numSatChans+i)
					.out_bus_(start_in_dex+numSatChans+i)
					.play
				})
			}).flat;

			stereoPatcherSynths = 2.collect({|i|
				synthLib[\patcher].note(
					target: patcherGroup)
				.in_bus_(hwInStart+i)
				.out_bus_(numSatChans+numSubChans+i) // atm stereo always goes to first set of outs
				.play
			});

/*			if( usingKernels, {
				if( kernelDict[curKernel].notNil, {
					// TODO: update any kernel-specific routing

					/*// includes sats + subs
					convSynth = kernelSynthDict[curKernel].note(0.2, target: convGroup)
					.in_bus_(patcherOutBus.bus)
					.out_bus_(patcherOutBus.bus) // uses ReplaceOut
					.play;*/
				},{"no current kernel specified or mismatch between curKernel name and key, conv synths not created".warn});
			});*/
			0.1.wait;
			this.startDelGainSynth;
			0.3.wait;

			0.1.wait;

			// init monitors, not played yet
			monitorSynths_ins = numHardwareIns.collect{ |i|
				synthLib[\clipMonitor].note(
					target: monitorGroup_ins)
				.in_bus_(hwInStart + i)
			};
			monitorSynths_outs = (numHardwareOuts*3).collect{ |i|
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

	startDecoder  { |newDecSynthName|
		var cur_decoutbus, new_decoutbus;

		// select which of the 3 out groups to send decoder/correction to
		new_decoutbus = if(usingKernels,
			{
				if(decoderPatch.notNil,
					{
						// this is the outbus being replaced..
						cur_decoutbus = decoderPatch.outbusnum
						// jump to next set of outputs, always numHardwareOuts or (numHardwareOuts*2)
						(cur_decoutbus + numHardwareOuts).wrap(1, numHardwareOuts*2)
					},{
						// first set of outputs routed to kernel
						numHardwareOuts
					}
				);
			},{0}	// else 0 for no kernels
		);

		decoderPatch = SoundLabDecoderPatch(this,
			newDecSynthName,
			if( stereoActive, {hwInStart+2}, {hwInStart}), // decoder inbusnum
			new_decoutbus	// decoder outbusnum
		);

		// TODO update decInfo variable with new decoder attributes
		/*result = decAttributes.select({|item| item.defname == curDecoder.synthdefname });
		decInfo = result[0];
		debug.if{ postln("Updating decInfo variable with new decoder attributes:\n"++"\t"++decInfo.defname);
			decInfo.keysValuesDo({|k,v|postln("\t\t"++k++" "++v)})
		};

		this.changed(\decoder, decInfo);*/
	}

	// -------- State Setters -------------------------------------------
	// ------------------------------------------------------------------
	// ------------------------------------------------------------------

	mute { |bool = true|
		if(bool,
			{
				decoderPatch.compsynth.masterAmp_(0);
				("amp set to " ++ 0).postln;
				isMuted = true;
				this.changed(\mute, 1);
			},{
				isMuted = false; // must set before for this.attenuate isMuted check to work
				this.changed(\mute, 0);
				if( isAttenuated,
					{this.attenuate},
					{
						decoderPatch.compsynth.masterAmp_(globalAmp);
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
					decoderPatch.compsynth.masterAmp_(att_dB.dbamp);
					("amp set to " ++ att_dB).postln;
				});
				isAttenuated = true;
				this.changed(\attenuate, 1);
			},{
				if(isMuted.not, {
					decoderPatch.compsynth.masterAmp_(globalAmp);
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
			decoderPatch.compsynth.masterAmp_(ampnorm);
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

	free { this.cleanup }

	cleanup  {
		slhw !? {slhw.removeDependant(this)};
		this.prClearServerSide;
		clipListener.free;
		reloadGUIListener.free;
		if ( gui.notNil, {gui.cleanup} );
	}
}