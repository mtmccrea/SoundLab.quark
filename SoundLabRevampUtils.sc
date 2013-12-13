+ SoundLab {

	prInitRigDimensions {
		// TODO consider adding sat and sub keys to compDict
		compDict = IdentityDictionary.new(know: true).putPairs([
				\gains, IdentityDictionary.new(know: true),
				\distances, IdentityDictionary.new(know: true),
				\delays, IdentityDictionary.new(know: true)
		]);

		/* distances (m): */
		compDict.distances.put( \default, [
			1.733, 1.774, 1.682,
			2.699, 2.755, 2.593, 2.568, 2.701, 2.553,
			2.324, 2.763, 2.784, 2.101, 2.774, 2.790,
			2.869, 2.940, 3.210, 2.950, 2.915, 3.051,
			2.244, 2.294, 2.296,
			3.048, 3.038, 3.046, 3.049 // subs
			]
		);

		/* calculate delays (sec): */
		compDict.delays.put( \default,
			(compDict.distances.default.maxItem - compDict.distances.default) / 343;
		);

		/* gains (dB) */
		compDict.gains.put( \default, [
			2.9, 3, 4.3,
			2.1, 1.5, 2.1, 1.9, 2, 2.5,
			3.2, 0.9, 0, 0.9, 0.3, 0.8,
			1.2, 0.9, 0.4, 0.3, 0, 0.6,
			1.9, 1.2, 1.3,
			0, 6, 6, 5 // subs
		].neg );

		/* parse distance delay and gain files from kernel folders */
		kernelsDirPath.entries.do({ |sr_pn|
			var sr, nm, knm, result;

			(sr_pn.isFolder && (sr_pn.folderName.asInt !=0)).if{
				sr = sr_pn.folderName.asInt;
				sr_pn.entries.do({ |kern_pn|
					kern_pn.isFolder.if{
						knm = kern_pn.folderName;
						kern_pn.entries.do{ |file_pn|
							if(file_pn.isFile, {
								nm = file_pn.fileName;
								case
								{nm.contains("delays")}{
									debug.if{postf("parsing delays for %, % \n", knm, sr)};
									compDict.delays.put(
										(knm++"_"++sr).asSymbol, this.prParseFile(file_pn)
									);
								}{nm.contains("distances")}{
									debug.if{postf("parsing distances for %, % \n", knm, sr)};
									compDict.distances.put(
										(knm++"_"++sr).asSymbol, this.prParseFile(file_pn)
									);
								}{nm.contains("gains")}{
									debug.if{postf("parsing gins for %, % \n", knm, sr)};
									compDict.gains.put(
										(knm++"_"++sr).asSymbol, this.prParseFile(file_pn)
									);
								};
							});
						}
					}
				})
			}
		});

		/* azimuth angles (smoothed): */
		spkrAzims =[
			60, 300, 180, 				// floor
			37, 324, 270, 220, 141, 90,	// lower
			0, 300, 240, 180, 120, 60,	// median
			40, 321, 271, 217, 144, 91,	// upper
			0, 240, 120,				// ceiling
			0, 270, 180, 90           	// subs
		].degrad;

		/* elevation angles (smoothed): */
		spkrElevs =[
			// [-51.9, -50.9, -54.8], // to LF driver
			-53.7, -53.7, -53.7,
			// [-28.7, -28.5, -29.2, -30.7, -29.2, -30.2],
			-30.0, -30.0, -30.0, -30.0, -30.0, -30.0,
			0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
			// [ 31.3,  30.7,  30.2,  31.2,  31.4,  30.6],
			30.0, 30.0, 30.0, 30.0, 30.0, 30.0,
			// [ 53.8, 52.7, 52.7],
			53.7, 53.7, 53.7,
			// [-26.7, -26.9, -26.8, -26.4]
			0.0, 0.0, 0.0, 0.0;	// subs
		].degrad;

		// pack azims and elevs into directions [[az0, el0],[az1, el1],..]
		spkrDirs = [ spkrAzims, spkrElevs ].lace( spkrAzims.size + spkrElevs.size ).clump(2);
		// this is for the diametric decoder of satellites, so drop the subs
		// spkrDirs = spkrDirs.keep(numSatChans);

		// for diametric decoders
		spkrOppDict = IdentityDictionary.new(know:true).putPairs([
			// each speaker channel and it's opposing channel, alternating
			0, 22, 22, 0,
			1, 23, 23, 1,
			2, 21, 21, 2,
			3, 18, 18, 3,
			4, 19, 19, 4,
			5, 20, 20, 5,
			6, 15, 15, 6,
			7, 16, 16, 7,
			8, 17, 17, 8,
			9, 12, 12, 9,
			10, 13, 13, 10,
			11, 14, 14, 11,
			// subs
			24, 26, 26, 24,
			25, 27, 27, 25
		]);

		" ************* rig coordinates initialized **************** ".postln;
	}

	initDecoderAttributes {
		decAttributeList = [
			// decoder attributes:
			// decoderName, kind, order, k, dimensions, arrayOutIndices, numInChannels;
			/* --ambisonic decoders-- */
			// arrayOutIndices for diametric: specify only the first half of out indexes
			[\Sphere_24ch,	\diametric,	\first,	'dual',	3,	(0..11),	4	],
			[\Sphere_18ch,	\diametric,	\first,	'dual',	3,	(3..11),	4	],
			[\Sphere_12ch,	\diametric,	\first,	'dual',	3,	(3..8),		4	],
			[\Sphere_6ch,	\diametric,	\first,	'dual',	3,	[8, 9, 20],	4	],
			[\Hex,			\diametric,	\first,	'dual',	2,	(9..11),	4	],
			/* --discrete channel routing-- */
			[\Thru_All,		\discrete,	nil,	nil,	2,	(0..11),	24	],
			[\Thru_Hex,		\discrete,	nil,	nil,	2,	(9..11),	6	]
		];

		// build an Array from the above attributes
		decAttributes = decAttributeList.collect({ |attributes|
			IdentityDictionary(know: true).putPairs([
				\decName, attributes[0],
				\kind, attributes[1]
				\ambiOrder, attributes[2], // don't use "order" as a keyword, it's a Dictionary method!
				\k, attributes[3],
				\dimensions, attributes[4],
				\arrayOutIndices, attributes[5],
				\numInputChans, attributes[6],
				\synthdefName,
					attributes[0] ++ '_' ++ attributes[2].asSymbol ++ '_' ++ attributes[3].asSymbol
			])
		});
	}

	/*	load speaker delays, distances, gains here because
	in the case of using kernels, it can be samplerate
	dependent, and so needs to happen after server has
	been initialized. */
	prLoadDelDistGain { |kernelName|
		var sr, key;
		sr = this.sampleRate;
		key = (kernelName++"_"++sr).asSymbol;
		("setting del dist gains to key: "++key).postln;

		if(
			usingKernels and:
			compDict.distances.includesKey(key) and:
			compDict.delays.includesKey(key) and:
			compDict.gains.includesKey(key),
			{
				spkrDists = compDict.distances.at(key);
				spkrDels = compDict.delays.at(key);
				spkrGains = compDict.gains.at(key);
			},{
				this.setNoKernel;
				spkrDists = compDict.distances.at(\default);
				spkrDels = compDict.delays.at(\default);
				spkrGains = compDict.gains.at(\default);
			}
		);
		debug.if{ this.prCheckArrayData };
		"delays, gains, distances loaded".postln;
	}

	prLoadDiametricDecoderSynth { |decSpecs|
		var arrayOutIndices, satOutbusNums, subOutbusNums, satDirections, subDirections;
		var matrix_dec, matrix_dec_sub, decSynthDef;

		/* --satellites-- */
		arrayOutIndices = decSpecs.arrayOutIndices;
		// get the other half of array indices for diametric opposites
		satOutbusNums = arrayOutIndices ++ arrayOutIndices.collect({|spkdex| spkrOppDict[spkdex]});
		// only need to provide 1/2 of the directions for diametric decoder
		satDirections = arrayOutIndices.collect({|busnum|
			switch(decSpecs.dimensions,
				2, spkrDirs[busnum][0], // 2D
				3, spkrDirs[busnum]     // 3D
			);
		});
		matrix_dec_sat = FoaDecoderMatrix.newDiametric(satDirections, decSpecs.k);

		/* --subs-- */
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1)); // always use all the subs
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep(subOutbusNums/2).collect({|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			matrix_dec_sub = FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k);
		});

		// build the synth
		decSynthDef =
		SynthDef( decSpecs.synthdefName, {
			arg out_bus, in_bus, fadeTime = 0.2, subgain = 0, gate = 1;
			var in, env, sat_out, sub_out;
			// TODO infer numInputChans from decSpecs.ambiOrder
			in = In.ar(in_bus, decSpecs.numInputChans); // B-Format signal
			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);
			// include shelf filter?
			if( matrix_dec_sat.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					matrix_dec_sat.shelfFreq,
					matrix_dec_sat.shelfK.at(0),
					matrix_dec_sat.shelfK.at(1)
				)
			});
			// near-field compensate, decode, remap to rig
			satOutbusNums.do({ arg spkdex, i;
				Out.ar(out_bus + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex) ),
						matrix_dec_sat.matrix.fromRow(i)
					)
				)
			});
			if(numSubChans.even, {
				subOutbusNums.do({ arg spkdex, i;
					Out.ar(out_bus + spkdex, // remap decoder channels to rig channels
						AtkMatrixMix.ar(
							FoaNFC.ar( in, spkrDists.at(spkdex) ),
							matrix_dec_sub.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				},{	// quick fix for non-even/diametric sub layout
					subOutbusNums.do({ arg spkdex, i;
						Out.ar(out_bus + spkdex,
							in[0] * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					})
				}
			)
		});

		decoderLib.add( decSynthDef ); // add the synth to the decoder library
	}

	prLoadDiscreteRoutingSynth { |decSpecs|
		decoderLib.add(
			SynthDef( decSpecs.synthdefName, {
				arg out_bus, in_bus, fadeTime = 0.3, subgain = 0, gate = 1;
				var in, env, out;
				in = In.ar(in_bus, decSpecs.numInputChans);
				env = EnvGen.ar(
					Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
					gate, doneAction: 2 );
				/* TODO no crossover added to discrete routing yet
				- see commented-out code below for crossover scheme to be added */
				Out.ar( decSpecs.arrayOutIndices.first, in * env );
			})
		)
	}

	// load every possible decoding SynthDef based on decAttList
	prLoadSynthDefs {
		decoderLib = CtkProtoNotes();

		/* build and load decoders */
		decAttributes.do{ |decSpecs|
			switch( decSpecs.kind,
				\diametric,	{ this.prLoadDiametricDecoderSynth(decSpecs)	},
				\discrete,	{ this.prLoadDiscreteRoutingSynth(decSpecs)	}
			);
		};

		/* library of synths other than decoders */
		// one delay_gain_comp for every speaker output
		// signal order to comp stage is assumed to be satellites, subs, stereo
		synthLib = CtkProtoNotes(

			SynthDef(\delay_gain_comp, { arg in_busnum=0, out_busnum=0, masterAmp = 1.0;
				var in_sig, sat_sig, stereo_sig, sub_sig, subs_delayed, sats_delayed, outs;

				sat_sig = In.ar(in_busnum, numSatChans) * spkrGains.keep(numSatChans).dbamp;
				sub_sig = In.ar(in_busnum+numSatChans, numSubChans)
				* spkrGains[numSatChans..(numSatChans+numSubChans-1)].dbamp;
				stereo_sig = In.ar(in_busnum+totalArrayChans);

				// TODO remove
				// in_sig = In.ar(in_bus, totalArrayChans); // totalArrayChans global
				// sat_sig = in_sig[0..23];
				// sub_sig = in_sig[24..27];
				// stereo_sig = in_sig[28..29];

				sats_delayed = DelayN.ar( sat_sig,
					spkrDels.maxItem, spkrDels[0..(numSatChans-1)] );
				subs_delayed = DelayN.ar( sub_sig,
					spkrDels.maxItem, spkrDels[numSatChans..(numSatChans+numSubChans-1)] );
				// TODO no stereo delay/gain comp atm
				outs = sats_delayed ++ subs_delayed ++ stereo_sig;
				ReplaceOut.ar(out_busnum, outs * masterAmp);
			}),
			// TODO remove
/*			SynthDef(\masterAmp, { arg in_bus=0, out_bus=0, masterAmp = 1.0;
				Out.ar(out_bus,
					In.ar(in_bus, 32) * Lag.kr(masterAmp, 0.25)
				)
			}),*/

			SynthDef(\patcher, { arg in_bus=0, out_bus=0;
				Out.ar(out_bus,
					In.ar(in_bus, 1)
				)
			}),

			SynthDef(\clipMonitor, { arg in_bus=0, clipThresh = 0.977;
				var sig, peak;
				sig = In.ar(in_bus, 1);
				peak = Peak.ar(sig, Impulse.kr(10));
				SendReply.ar( peak > clipThresh, '/clip', [in_bus, peak] );
			})
		);

		" *********** Sound Lab synths loaded ************ ".postln;
		loadCond.test_(true).signal;
	}

	prInitSLHW { |initSR|
		// TODO take care of the number of ins and outs (HWIns/HWOuts*3) in SLHW
		slhw = SoundLabHardware(useSupernova:false); // false to for SC, true for SN
		debug.if{"SLHW initiated".postln};
		slhw.startAudio(initSR, periodSize: 256);
		debug.if{"SLHW audio started".postln};
		slhw.addDependant(this);
	}

	prInitDefaultHW { |initSR|
		var so;
		server = server ?? Server.default;
		server !? { server.serverRunning.if{server.quit} };
		"REBOOTING".postln;

		so = server.options;
		so.sampleRate = initSR ?? 96000;
		so.memSize = 8192 * 16;
		// numHardwareOuts*3 to allow fading between settings,
		// routed to different JACK busses
		so.numOutputBusChannels = numHardwareOuts * 3;
		so.numInputBusChannels = numHardwareIns;

		// TODO encapsulate this
		// the following will otherwise be called from update: \audioIsRunning
		server.waitForBoot({
			rbtTryCnt = rbtTryCnt+1;
			// in case sample rate isn't set correctly the first time (SC bug)
			if( server.sampleRate == initSR,
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
					debug.if{("valid float on line: "++i++", ").post; val.asFloat.postln;};
					data = data.add(val.asFloat);
				})
			});
			^data;
		});
	}

		// diagnostics for testing
	prCheckArrayData {
		if (
			spkrAzims.size == spkrElevs.size and:
			spkrElevs.size == spkrDists.size and:
			spkrDists.size == spkrDels.size and:
			spkrDels.size == spkrGains.size and:
			spkrGains.size == totalArrayChans and:
			spkrOppDict.size == spkrDirs.size,
			{
				"OK: array sizes of rig dimensions match".postln;
			},{
				"mismatch in rig dimension array sizes!".warn;
				[
					"these should equal numSatChans + numSubChans: "++ (numSatChans+numSubChans),
					spkrAzims.size,
					spkrElevs.size,
					spkrDists.size,
					spkrDels.size,
					spkrGains.size,
					spkrDirs.size,
					spkrOppDict.size,
				].do(_.postln);
			}
		);

		"\nSpeaker Gains, Distances, Delays".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("chan: %\t| gain: %\t| dist: %\t| del: %\n",
				i, spkrGains[i], spkrDists[i], spkrDels[i]
			)
		});

		"\nSpeaker Directions:".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("chan: %\t| dir: %\n", i, spkrDirs[i].raddeg)
		});
	}
}