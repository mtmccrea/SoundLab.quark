SoundLabPartConvPatch {
	// copyArgs
	var <sl, <kernelPath, fftsize, inbusnum, <outbusnum, <loadCondition;
	var <server, <group, <partConvSynth, <irbuffers, bufSizes, irspectrums, <partConvSynthDef;
	var cond;

	*new { |soundlab, kernelPath, fftsize, inbusnum, outbusnum, loadCondition|
		^super.newCopyArgs(soundlab, kernelPath, fftsize, inbusnum, outbusnum, loadCondition).init;
	}

	init {
		fork {
			block { |break|
				cond = Condition.new;
				irbuffers = [];

				server = sl.server;
				group = CtkGroup.play(addAction: \tail, target: 1, server: server);
				server.sync;

				"Loading Kernels".postln;

				PathName(kernelPath).deepFiles.do{arg pathname;
					(pathname.extension == "wav").if({
						irbuffers = irbuffers.add(CtkBuffer(pathname.fullPath, server: server).load(sync: false))
					})
				};

				server.sync;

				bufSizes = irbuffers.collect{arg irbuffer;
					PartConv.calcBufSize(fftsize, irbuffer)
				};

				irspectrums = bufSizes.collect{arg bufSize;
					CtkBuffer.buffer(bufSize, 1, server).load(sync: false)
				};

				server.sync;

				irspectrums.do{arg irspectrum, k;
					irspectrum.preparePartConv(0.0, irbuffers[k], fftsize, {cond.test_(true).signal});
					// ("loading part conv buffer:" + k).postln;
					// cond.wait;
					// cond.test_(false)
				};

				server.sync;

				partConvSynthDef = CtkSynthDef('soundLabPartConv', {
					arg fftsize, in_busnum, out_busnum, gate = 1, fadeTime;
					var in, conv, env;
					env = EnvGen.ar(
						Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
						gate, doneAction: 2
					);
					in = In.ar(in_busnum, sl.numKernelChans);
					conv = irspectrums.collect{arg buffer, i;
						PartConv.ar(in[i], fftsize, buffer)
					};
					Out.ar(out_busnum, conv * env)
				});

				"Loaded partconv synth".postln;

				partConvSynth = partConvSynthDef.note(
					addAction: \head, target: group)
				.fftsize_(sl.fftsize)
				.in_busnum_(inbusnum)
				.out_busnum_(outbusnum);
				"loaded part conv note".postln;

			};
			loadCondition.test_(true).signal;
		}
	}

	play { |xfade = 0.2|
		partConvSynth.fadeTime_(xfade).play;
	}

	free { |xfade = 0.2|
		fork {
			partConvSynth.fadeTime_(xfade).release;
			xfade.wait;
			group.free;
		}
	}

	// NOTE: this changes with the stereo setting, so ask the synth
	inbusnum {^partConvSynth.in_busnum}
}