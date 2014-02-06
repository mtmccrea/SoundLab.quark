SoundLabDecoderPatch {
	// copyArgs
	var <soundlab, <decoderName, <inbusnum, <outbusnum, <loadCondition;
	var <server, <group, <decodersynth, <compsynth;

	*new { |soundlab, decoderSynthDefName, inbusnum, outbusnum, loadCondition|
		^super.newCopyArgs(soundlab, decoderSynthDefName, inbusnum, outbusnum, loadCondition).init;
	}

	init {
		fork {
			block { |break|
				// debug
				"initializing SoundLabDecoderPatch".postln;

				server = soundlab.server;
				group = CtkGroup.play(addAction: \before, target: soundlab.patcherGroup, server: server);
				server.sync; // TODO confirm server.sync works here (i.e. no fork in CTK group .play)

				soundlab.decoderLib[decoderName] ?? {"decoder not found in decoderLib!!!".warn; break.()};
				// debug
				("building: "++soundlab.decoderLib[decoderName].synthdefname).postln;

				// TODO it appears there's something wrong with the decoder synthDefs
				// likely in the way they're built...
				decodersynth = soundlab.decoderLib[decoderName].note(addAction: \head, target: group)
				.in_busnum_(inbusnum).out_busnum_(outbusnum);

				compsynth = soundlab.synthLib[\delay_gain_comp].note(
					addAction: \tail, target: group)
				.in_busnum_(outbusnum).out_busnum_(outbusnum).masterAmp_(soundlab.globalAmp); // uses ReplaceOut

			};
			loadCondition.test_(true).signal;
		}
	}

	play { |xfade = 0.2|
		compsynth.play;
		decodersynth.fadeTime_(xfade).play;
	}

	free { |xfade = 0.2|
		fork {
			decodersynth.fadeTime_(xfade).release;
			xfade.wait;
			group.free;
		}
	}
}