SoundLabDecoderPatch {
	// copyArgs
	var <soundlab, <decoderName, <inbusnum, <outbusnum, <finishInitCondition;
	var <server, <group, <decodersynth, <compsynth;

	*new { |soundlab, decoderSynthDefName, inbusnum, outbusnum, finishInitCondition|
		^super.newCopyArgs(soundlab, decoderSynthDefName, inbusnum, outbusnum, finishInitCondition).init;
	}

	init {
		fork {
			// debug
			"initializing SoundLabDecoderPatch".postln;

			server = soundlab.server;
			group = CtkGroup.play(addAction: \before, target: soundlab.patcherGroup, server: server);
			server.sync; // TODO confirm server.sync works here (i.e. no fork in CTK group .play)

			// debug
			"server sync'd?".postln;
			("building: "++soundlab.decoderLib[decoderName].synthdefname).postln;
			group.node.postln;
			[inbusnum, outbusnum].postln;

			// TODO it appears there's something wrong with the decoder synthDefs
			// likely in the way they're built...
			decodersynth = soundlab.decoderLib[decoderName].note(
				addAction: \head, target: group)
			.in_busnum_(inbusnum).out_busnum_(outbusnum);

			// debug
			"decoder synth'd".postln;

			compsynth = soundlab.synthLib[\delay_gain_comp].note(
				addAction: \tail, target: group)
			.in_busnum_(outbusnum).out_busnum_(outbusnum).masterAmp_(soundlab.globalAmp); // uses ReplaceOut

			// debug
			"comp synth'd".postln;

			finishInitCondition.test_(true).signal;

			// debug
			"signal'd".postln;
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