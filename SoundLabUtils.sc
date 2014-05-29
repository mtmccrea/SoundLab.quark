+ SoundLab {

	prInitRigDimensions {
		// TODO consider adding sat and sub keys to compDict
		compDict = IdentityDictionary.new(know: true).putPairs([
				\gains, IdentityDictionary.new(know: true),
				\distances, IdentityDictionary.new(know: true),
				\delays, IdentityDictionary.new(know: true)
		]);

		/* distances (m): */
		compDict.distances.put( \default, config.defaultSpkrDistances);

		/* calculate delays (sec): */
		compDict.delays.put( \default,
			(compDict.distances.default.maxItem - compDict.distances.default) / 343;
		);

		/* gains (dB) */
		compDict.gains.put( \default, config.defaultSpkrGainsDB );

		/* parse distance delay and gain files from kernel folders */
		kernelDirPath.entries.do({ |sr_pn|
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
									debug.if{postf("parsing gains for %, % \n", knm, sr)};
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
		spkrAzims = config.spkrAzimuthsRad;
		/* elevation angles (smoothed): */
		spkrElevs = config.spkrElevationsRad;
		// pack azims and elevs into directions [[az0, el0],[az1, el1],..]
		spkrDirs = [ spkrAzims, spkrElevs ].lace( spkrAzims.size + spkrElevs.size ).clump(2);
		// this is for the diametric decoder of satellites, so drop the subs
		// spkrDirs = spkrDirs.keep(numSatChans);
		// for diametric decoders
		spkrOppDict = config.spkrOppDict;

		" ************* rig coordinates initialized **************** ".postln;
	}

	prInitDecoderAttributes {
		decAttributeList = config.decAttributeList;

		// build an Array from the above attributes
		decAttributes = decAttributeList.collect({ |attributes|
			IdentityDictionary(know: true).putPairs([
				\decName, attributes[0],
				\kind, attributes[1],
				\ambiOrder, attributes[2], // don't use "order" as a keyword, it's a Dictionary method!
				\k, attributes[3],
				\dimensions, attributes[4],
				\arrayOutIndices, attributes[5],
				\numInputChans, attributes[6],
				\synthdefName, (attributes[0] ++ '_order' ++ attributes[2]).asSymbol
			])
		});

		" ************* decoder attributes initialized **************** ".postln;
	}

	/*	load speaker delays, distances, gains here because
	in the case of using kernels, it can be samplerate
	dependent, and so needs to happen after server has
	been initialized. */
	prLoadDelDistGain { |kernelName, completeCondition|
		fork {
			var sr, key;
			// debug
			"prLoadDelDistGain".postln;

			sr = this.sampleRate;
			key = if(kernelName != \default, {(kernelName++"_"++sr).asSymbol},{\default});

			// debug
			("setting del dist gains to key: "++key).postln;

			if( usingKernels.not or:
				compDict.distances.includesKey(key).not or:
				compDict.delays.includesKey(key).not or:
				compDict.gains.includesKey(key).not, {
					"kernel name is default or otherwise wasn't found in
					distances, delays or gains lists".postln;
					key = \default;
			});

			spkrDists = compDict.distances.at(key);
			spkrDels = compDict.delays.at(key);
			spkrGains = compDict.gains.at(key);

			this.prCheckArrayData;
			("delays, gains, distances loaded for:" ++ key).postln;
			loadedDelDistGain = key;
			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

	prLoadDiametricDecoderSynth { |decSpecs|
		var arrayOutIndices, satOutbusNums, subOutbusNums, satDirections, subDirections;
		var matrix_dec_sat, matrix_dec_sub, decSynthDef;

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
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});

			matrix_dec_sub = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5) }
			)
		});

		// build the synthdef
		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);
			// TODO infer numInputChans from decSpecs.ambiOrder
			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal

			// rotate the direction the listener should face
			in = FoaTransform.ar(in, 'rotate', rotate);

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
				Out.ar(out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex) ),
						matrix_dec_sat.matrix.fromRow(i)
					)
				)
			});

			if(numSubChans.even, {
				subOutbusNums.do({ arg spkdex, i;
					Out.ar(out_busnum + spkdex, // remap decoder channels to rig channels
						AtkMatrixMix.ar(
							FoaNFC.ar( in, spkrDists.at(spkdex) ),
							matrix_dec_sub.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				},{	// quick fix for non-even/non-diametric sub layout
					subOutbusNums.do({ arg spkdex, i;
						Out.ar(out_busnum + spkdex,
							in[0] * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					})
				}
			)
		});

		decoderLib.add( decSynthDef ); // add the synth to the decoder library

		// debug
		"added diametric decoder to the decoderLib".postln;
	}

	// NOTE: arrayOutIndices is [half of horiz] ++ [all elevation dome] spkr indices
	prLoadDiametricDomeDecoderSynth { |decSpecs|
		var domeOutbusNums, domeOutbusNumsFullHoriz, partialDomeDirections, subOutbusNums, subDirections;
		var halfHorizDirections, posElevDirections, halfSphereDirections, lowerStartDex, domeEndDex, domeDecoderMatrix;
		var sphereDecoderMatrix, subDecoderMatrix, decSynthDef;
		var lowerMatrix, lowerSum, lowerComp;
		var lowerK = -6.0.dbamp;

		// debug
		"adding DOME decoder ...".postln;

		/* --dome satellites-- */
		domeOutbusNums = decSpecs.arrayOutIndices; // half horiz & full dome spkr indices
		// append other half of horiz outbus nums for collecting matrix outputs below
		domeOutbusNumsFullHoriz = domeOutbusNums ++
		domeOutbusNums.select({|busnum| spkrDirs[busnum][1]==0 }) // select busnums with 0 elevation
		.collect({|spkdex| spkrOppDict[spkdex]}) // then collect their opposite's busnum
		;

		partialDomeDirections = domeOutbusNums.collect({|busnum| spkrDirs[busnum] });
		halfHorizDirections = partialDomeDirections.select{|item| item[1]==0 };
		posElevDirections = partialDomeDirections.select{|item| item[1]>0 };
		halfSphereDirections = halfHorizDirections ++ posElevDirections;

		// model full diametric decoder, and matrix
		sphereDecoderMatrix = FoaDecoderMatrix.newDiametric(halfSphereDirections, decSpecs.k);

		// truncate to just lower speakers to calculate compensation matrix...
		lowerStartDex = (halfHorizDirections.size*2) + posElevDirections.size;

		lowerMatrix = Matrix.with(sphereDecoderMatrix.matrix.asArray[lowerStartDex..]);
		lowerSum = (lowerK / posElevDirections.size) * lowerMatrix.sumCols;
		lowerComp = Matrix.with(
			Array.fill(halfHorizDirections.size,{lowerSum}) // add to first half of horiz
			++ Array.fill2D(posElevDirections.size,4,{0}) // add 0 to elevation spkrs
			++ Array.fill(halfHorizDirections.size,{lowerSum}) // add to second half of horiz
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

		/* --subs-- */
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1)); // always use all the subs
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5) }
			)
		});

		// build the synthdef
		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);
			// TODO infer numInputChans from decSpecs.ambiOrder
			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal

			// rotate the direction the listener should face
			in = FoaTransform.ar(in, 'rotate', rotate);

			// include shelf filter on inpput or not. inferred from sphere FoaDecoderMatrix
			// because domeDecoderMatrix is actually just a Matrix object (no .shelfFreq)
			if( sphereDecoderMatrix.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					sphereDecoderMatrix.shelfFreq,
					sphereDecoderMatrix.shelfK.at(0),
					sphereDecoderMatrix.shelfK.at(1)
				)
			});

			// near-field compensate, decode, remap to rig
			domeOutbusNumsFullHoriz.do({ arg spkdex, i;
				Out.ar(out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex) ),
						domeDecoderMatrix.fromRow(i) // CHECK
					)
				)
			});

			if(numSubChans.even, {
				subOutbusNums.do({ arg spkdex, i;
					Out.ar(out_busnum + spkdex, // remap decoder channels to rig channels
						AtkMatrixMix.ar(
							FoaNFC.ar( in, spkrDists.at(spkdex) ),
							subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				},{	// quick fix for non-even/non-diametric sub layout
					subOutbusNums.do({ arg spkdex, i;
						Out.ar(out_busnum + spkdex,
							in[0] * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					})
				}
			)
		});

		decoderLib.add( decSynthDef ); // add the synth to the decoder library

		// debug
		"added diametric dome decoder to the decoderLib".postln;
	}

	prLoadDiscreteRoutingSynth { |decSpecs|

		decoderLib.add(
			SynthDef( decSpecs.synthdefName, {
				arg in_busnum, fadeTime = 0.3, subgain = 0, gate = 1;
				var in, env, out;
				in = In.ar(in_busnum, decSpecs.numInputChans);
				env = EnvGen.ar(
					Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
					gate, doneAction: 2 );
				/* TODO no crossover added to discrete routing yet
				- see commented-out code below for crossover scheme to be added */
				// Out.ar( decSpecs.arrayOutIndices.first, in * env );
				decSpecs.arrayOutIndices.do{ |outbus, i| Out.ar( outbus, in[i] * env ) };
			})
		);

		// debug
		"added discrete router to decoderLib".postln;
	}

	// load every possible decoding SynthDef based on decAttList
	prLoadSynthDefs { |finishLoadCondition|

		decoderLib = CtkProtoNotes();

		/* build and load decoders */
		decAttributes.do{ |decSpecs|

			// debug
			"build and load decoder".postln;
			// decSpecs.postln;

			switch( decSpecs.kind,
				\diametric,	{ this.prLoadDiametricDecoderSynth(decSpecs)},
				\dome,		{ this.prLoadDiametricDomeDecoderSynth(decSpecs)},
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

				sats_delayed = DelayN.ar( sat_sig,
					spkrDels.maxItem, spkrDels[0..(numSatChans-1)] );
				subs_delayed = DelayN.ar( sub_sig,
					spkrDels.maxItem, spkrDels[numSatChans..(numSatChans+numSubChans-1)] );
				// TODO no stereo delay/gain comp atm
				outs = sats_delayed ++ subs_delayed ++ stereo_sig;
				ReplaceOut.ar(out_busnum, outs * masterAmp);
			})
		);

		" *********** Sound Lab synths loaded ************ \n".postln;
		finishLoadCondition.test_(true).signal;
	}

	prInitSLHW { |initSR|
		//for linux
		slhw = SoundLabHardware.new(
			false, //useSupernova
			config.fixAudioInputGoingToTheDecoder, //fixAudioInputGoingToTheDecoder
			config.useFireface, //useFireface
			config.midiPortName, //midiPortName
			config.cardNameIncludes, //cardNameIncludes
			config.jackPath, //jackPath
			numHardwareIns, //serverIns
			numHardwareOuts * 3, //serverOuts
			numHardwareOuts, //numHwOutChToConnectTo
			numHardwareIns, //numHwInChToConnectTo
			config.firefaceID, //firefaceID
			config.whichMadiInput, //whichMadiInput
			config.whichMadiOutput //whichMadiOutput
		);
		// slhw = SoundLabHardware.new(false,true,false,nil,nil,"/usr/local/bin/jackdmp",32,128); //for osx
		slhw.postln;
		slhw.startAudio(
			initSR, //newSR
			config.hwPeriodSize, //periodSize
			config.hwPeriodNum, //periodNum
		);
		slhw.addDependant(this);
	}

	prInitDefaultHW { |initSR|
		var so;
		// debug
		"initializing default hardware".postln;

		server = server ?? Server.default;
		server !? { server.serverRunning.if{server.quit} };
		"REBOOTING".postln;

		so = server.options;
		so.sampleRate = initSR ?? 96000;
		so.memSize = 8192 * 16;
		so.numWireBufs = 64*8;
		so.device = "JackRouter";
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

	prFindKernelDir { |kenelName|
		var kernelDir_pn;
		kernelDirPath.folders.do({ |sr_pn|
			if( sr_pn.folderName.asInt == server.sampleRate, {
				sr_pn.folders.do({ |kernel_pn|
					if( kernel_pn.folderName.asSymbol == kenelName, {
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
					debug.if{("valid float on line: "++i++", ").post; val.asFloat.postln;};
					data = data.add(val.asFloat);
				})
			});
			^data;
		});
	}

		// diagnostics for testing
	prCheckArrayData {
		// debug
		"checking array data".postln;
		[
					"these should equal numSatChans + numSubChans: "++ (numSatChans+numSubChans),
					spkrAzims.size,
					spkrElevs.size,
					spkrDists.size,
					spkrDels.size,
					spkrGains.size,
					spkrDirs.size,
					// spkrOppDict.size,
				].do(_.postln);

		if (
			spkrAzims.size == spkrElevs.size and:
			spkrElevs.size == spkrDists.size and:
			spkrDists.size == spkrDels.size and:
			spkrDels.size == spkrGains.size and:
			spkrGains.size == totalArrayChans,
			// and: spkrOppDict.size == spkrDirs.size,
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
					// spkrOppDict.size,
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