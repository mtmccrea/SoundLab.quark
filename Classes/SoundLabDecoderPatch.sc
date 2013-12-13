SoundLabDecoderPatch {
	// copyArgs
	var <soundlab, <decoderName, <inbusnum, <outbusnum;
	var <server, <group, <decodersynth, <compsynth;

	*new { |soundlab, decoderSynthDefName, inbusnum, outbusnum|
		^super.newCopyArgs(soundlab, decoderSynthDefName, inbusnum, outbusnum).init;
	}

	init {
		server = soundlab.server;
		group = CtkGroup.play(addAction: \before, target: soundlab.patcherGroup);
		server.sync; // TODO confirm server.sync works here (i.e. no fork in CTK group .play)

		decodersynth = soundlab.decoderLib[decoderName].note(
			addAction: \head, target: group)
		.in_busnum_(inbusnum).out_busnum_(outbus);

		compsynth = soundlab.synthLib[\delay_gain_comp].note(
			addAction: \tail, target: group)
		.in_busnum_(outbus).out_busnum_(outbus).masterAmp_(soundlab.globalAmp); // uses ReplaceOut
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