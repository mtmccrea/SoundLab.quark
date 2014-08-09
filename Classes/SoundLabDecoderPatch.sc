SoundLabDecoderPatch {
	// copyArgs
	var <soundlab, <decoderName, <inbusnum, <outbusnum, <loadCondition;
	var <server, <group, <decodersynth, <compsynth, <attributes, <decType;

	*new { |soundlab, decoderName, inbusnum, outbusnum, loadCondition|
		^super.newCopyArgs(soundlab, decoderName, inbusnum, outbusnum, loadCondition).init;
	}

	init {
		fork {
			block { |break|
				var synthdefName;
				synthdefName = decoderName.asSymbol;
				("initializing SoundLabDecoderPatch:"+synthdefName).postln;

				soundlab.decoderLib[synthdefName] ?? {
					break.( warn( synthdefName ++" decoder not found in decoderLib!!!" ))
				};
				// used for introspection by GUI
				attributes = soundlab.decAttributes.select{|me| me.synthdefName == synthdefName};
				case
				{attributes.size == 1}{
					attributes = attributes[0];
					decType = attributes[\kind];
				}
				{attributes.size == 0}{
					attributes = IdentityDictionary(know: true).put(\kind, \matrix);
					decType = \matrix;
				}
				{attributes.size >1} {
					break.( warn(
						"Found more than one decoder attribute list with that synthdefName: "
						++synthdefName
					))
				};

				server = soundlab.server;
				group = CtkGroup.play(addAction: \before, target: soundlab.patcherGroup, server: server);
				server.sync;

				decodersynth = soundlab.decoderLib[synthdefName].note(
					addAction: \head, target: group)
				.in_busnum_(inbusnum);
				// discrete routing synthdefs have no out_busnum or rotation arg
				(decType != \discrete).if{
					decodersynth.out_busnum_(outbusnum)
					.rotate_(if(soundlab.rotated, {soundlab.rotateDegree.degrad},{0}))
				};

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