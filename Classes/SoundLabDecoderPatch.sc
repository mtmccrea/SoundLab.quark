SoundLabDecoderPatch {
	// copyArgs
	var <soundlab, <decoderName, <order, <inbusnum, <outbusnum, <loadCondition;
	var <server, <group, <decodersynth, <compsynth, <attributes;

	*new { |soundlab, decoderName, order, inbusnum, outbusnum, loadCondition|
		^super.newCopyArgs(soundlab, decoderName, order, inbusnum, outbusnum, loadCondition).init;
	}

	init {
		fork {
			block { |break|
				var synthdefName;
				synthdefName = (decoderName ++'_order'++ order).asSymbol;
				("initializing SoundLabDecoderPatch:"+synthdefName).postln;

				soundlab.decoderLib[synthdefName] ?? {
					warn(synthdefName++" decoder not found in decoderLib!!!"); break.()
				};
				// used for introspection by GUI
				attributes = soundlab.decAttributes.select{|me| me.synthdefName == synthdefName};

				server = soundlab.server;
				group = CtkGroup.play(addAction: \before, target: soundlab.patcherGroup, server: server);
				server.sync;

				decodersynth = soundlab.decoderLib[synthdefName].note(addAction: \head, target: group)
				.in_busnum_(inbusnum);
				// discrete routing synthdefs have no out_busnum arg
				order.isInteger.if{ decodersynth.out_busnum_(outbusnum) };

				compsynth = soundlab.synthLib[\delay_gain_comp].note(
					addAction: \tail, target: group)
				.in_busnum_(outbusnum).out_busnum_(outbusnum) // uses ReplaceOut
				.masterAmp_(soundlab.globalAmp);

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