+ SoundLab {

	prInitRigDimensions {
		// TODO consider adding sat and sub keys to compDict
		compDict = IdentityDictionary.new(know: true).putPairs([
				\gains, IdentityDictionary.new(know: true),
				\distances, IdentityDictionary.new(know: true),
				\delays, IdentityDictionary.new(know: true)
		]);

		/* distances (m): */
		compDict.distances.put( \default, config.defaultSpkrDistances );

		/* calculate delays (sec): */
		compDict.delays.put( \default,
			config.defaultSpkrDelays ??
			// calculate from distance
			(compDict.distances.default.maxItem - compDict.distances.default) / 343;
		);

		/* gains (dB) */
		compDict.gains.put( \default, config.defaultSpkrGainsDB );

		/* parse distance delay and gain files from kernel folders */
		// Folder structure: /sampleRate/kernelName/ holds
		// delay, dist, gain .txt files and folders for each "version"
		// of the kernel, i.e. the various settings: moderate, high correction, min/lin phase, etc
		kernelDirPathName.entries.do({ |sr_pn|
			var sr, nm, knm, result;

			(sr_pn.isFolder && (sr_pn.folderName.asInt !=0)).if{
				sr = sr_pn.folderName;
				sr_pn.entries.do({ |kern_pn|
					kern_pn.isFolder.if{
						knm = kern_pn.folderName;
						kern_pn.entries.do{ |file_pn|
							if(file_pn.isFile, {
								nm = file_pn.fileName;
								case
								{nm.contains("delays")}{
									debug.if{postf("\nParsing delays for %, %: ", knm, sr)};
									compDict.delays.put(
										(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
									);
								}{nm.contains("distances")}{
									debug.if{postf("\nParsing distances for %, %:", knm, sr)};
									compDict.distances.put(
										(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
									);
								}{nm.contains("gains")}{
									debug.if{postf("\nParsing gains for %, %:", knm, sr)};
									compDict.gains.put(
										(sr++"/"++knm).asSymbol, this.prParseFile(file_pn)
									);
								};
							});
						}
					}
				})
			}
		});

		/* azimuth angles */
		spkrAzims = config.spkrAzimuthsRad;
		/* elevation angles */
		spkrElevs = config.spkrElevationsRad;
		// pack azims and elevs into directions [[az0, el0],[az1, el1],..]
		spkrDirs = [ spkrAzims, spkrElevs ].lace( spkrAzims.size + spkrElevs.size ).clump(2);
		// this is for the diametric decoder of satellites, so drop the subs
		// spkrDirs = spkrDirs.keep(numSatChans);
		// for diametric decoders
		spkrOppDict = config.spkrOppDict;

		"\n**** rig coordinates initialized ****".postln;
	}

	prInitDecoderAttributes {
		decAttributeList = config.decAttributeList;

		// build an Array from the above attributes
		decAttributes = decAttributeList.collect({ |attributes|
			IdentityDictionary(know: true).putPairs([
				\decName, attributes[0],
				\kind, attributes[1],
				\k, attributes[2],
				\dimensions, attributes[3],
				\arrayOutIndices, attributes[4],
				\numInputChans, attributes[5],
				\synthdefName, (attributes[0]).asSymbol
			])
		});

		"\n**** decoder attributes initialized **** ".postln;
	}

	/*	load speaker delays, distances, gains here because
		in the case of using kernels, it can be samplerate
		dependent, and so needs to happen after server has
		been initialized.
	*/
	prLoadDelDistGain { |delDistGainKey, completeCondition|
		fork {
			var key;

			// test that the kernel key returns a result
			key = if( usingKernels, {

				("trying del dist gains key: "++delDistGainKey).postln; // debug

				if( compDict.distances.includesKey(delDistGainKey) and:
					compDict.delays.includesKey(delDistGainKey) and:
					compDict.gains.includesKey(delDistGainKey),
					{
						delDistGainKey
					},{
						warn(format("Did not find a matching value in the compDict for the key %\nLoading default delays, distances and gains.\n", delDistGainKey));

						\default;
					}
				);
			},{ \default });

			spkrDists =	compDict.distances.at(key);
			spkrDels =	compDict.delays.at(key);
			spkrGains = compDict.gains.at(key);

			this.prCheckArrayData;

			postf("\n*** Delays, gains, distances loaded for:\t% ***", key);
			loadedDelDistGain = key;
			completeCondition !? {completeCondition.test_(true).signal};
		}
	}

	checkKernelSpecAtSR { |relativePath|
		var result;
		result = File.exists(config.kernelsPath ++ this.sampleRate ++ "/" ++ relativePath);
		result.not.if{ warn(format("kernel spec entry % not found at this sample rate (%)", relativePath, this.sampleRate)) };
		^result
	}

	collectKernelCheckBoxAttributes {
		var attributes, sRate_pn;
		attributes = [];
		sRate_pn = PathName( config.kernelsPath ++ this.sampleRate);
		config.kernelSpec.do{|k_attributes|

			// check that the kernel spec exists at this SR
			if( this.checkKernelSpecAtSR(k_attributes[0]), {

				// drop the kernel path and correction degree leaving only user-defined attributes
				k_attributes[1].do{ |att|
					if( attributes.includes(att).not, {attributes = attributes.add(att)} )
				};
			})
		};
		^attributes
	}

	collectKernelPopUpAttributes {
		var popups;
		popups = [[]];

		config.kernelSpec.do{|k_attributes|

			if( this.checkKernelSpecAtSR(k_attributes[0]), {
				var numPopUps = k_attributes[2].size;

				// grow popups array if needed
				if( popups.size < numPopUps, {
					(numPopUps - popups.size).do{popups = popups.add([])}
				});

				k_attributes[2].do{ |att, i|

					if( popups[i].includes(att).not, {
						popups[i] = popups[i].add(att);
					} )
				};
			});
		};
		^popups
	}

	getKernelAttributesMatch { |selectedAttributes|
		var results, numMatches;
		// gather bools for each kernel spec whether all selected
		// attributes match, should only be one match
		results = config.kernelSpec.collect{ |k_attributes|
			var collAttributes, test1, test2;

			// collect menu and check box attributes for this kernel spec
			collAttributes = (k_attributes[1] ++ k_attributes[2]);

			// return true if all attributes match, false if not
			test1 = selectedAttributes.collect({ |att|
				collAttributes.includes(att)
			}).includes(false).not;
			test2 = collAttributes.collect({ |att|
				selectedAttributes.includes(att)
			}).includes(false).not;

			(test1 and: test2)
		};

		// postf("selectedAttributes:%\n", selectedAttributes);
		// postf("kernel matching results:\n%\n", results);

		numMatches = results.occurrencesOf(true);

		^case
		{numMatches == 1} { config.kernelSpec[results.indexOf(true)][0] }
		{numMatches == 0} { 0 }		// return 0 for no matches
		{numMatches > 1 } { -1 };	// return -1 for more than one match
	}

	formatKernelStatePost { |kPath, short=false|
		var rtn;
		^if( kPath != \basic_balance,
			{ var pn, category, attributes;
				pn = PathName(kPath.asString);
				category = pn.allFolders[pn.allFolders.size-2];
				attributes = config.kernelSpec.select({ |me|
					me.at(0) == (category ++ "/" ++ pn.allFolders.last ++ "/")

				}).at(0).drop(1).flat;

				short.if(
					{ format("%", attributes)},
					{ format("%\n%", category, attributes)}
				);
			},{
				\basic_balance.asString
			}
	);
	}

	prLoadDiametricDecoderSynth { |decSpecs|
		var arrayOutIndices, satOutbusNums, subOutbusNums, satDirections, subDirections;
		var matrix_dec_sat, matrix_dec_sub, decSynthDef;


		/* --satellites matrix-- */

		arrayOutIndices = decSpecs.arrayOutIndices;

		// get the other half of array indices for diametric opposites
		satOutbusNums = arrayOutIndices
		++ arrayOutIndices.collect({ |spkdex| spkrOppDict[spkdex] });

		// only need to provide 1/2 of the directions for diametric decoder
		satDirections = arrayOutIndices.collect({|busnum|
			switch(decSpecs.dimensions,
				2, spkrDirs[busnum][0], // 2D
				3, spkrDirs[busnum]     // 3D
			);
		});

		matrix_dec_sat = FoaDecoderMatrix.newDiametric(satDirections, decSpecs.k).shelfFreq_(shelfFreq);


		/* --subs matrix-- */

		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo decoder for subs or diammetric if there's an even number of them > 2
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});

			matrix_dec_sub = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k).shelfFreq_(shelfFreq);
				},
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});


		/* --build the synthdef-- */

		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);
			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal
			in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation

			// include shelf filter if the satellite
			// matrix has a shelf freq specified
			if( matrix_dec_sat.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					//matrix_dec_sat.shelfFreq,
					\shelfFreq.kr(matrix_dec_sat.shelfFreq), // see Control.names
					matrix_dec_sat.shelfK.at(0),
					matrix_dec_sat.shelfK.at(1)
				)
			});

			/* -- sat decode --*/

			// near-field compensate, decode, remap to rig
			satOutbusNums.do({ | spkdex, i |
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex) ),
						matrix_dec_sat.matrix.fromRow(i)
					)
				)
			});

			/* -- sub decode --*/

			if( numSubChans.even,
				{
					subOutbusNums.do({ | spkdex, i |
						Out.ar(
							out_busnum + spkdex,
							AtkMatrixMix.ar(
								FoaNFC.ar( in, spkrDists.at(spkdex) ),
								matrix_dec_sub.matrix.fromRow(i)
							) * subgain.dbamp
						)
					})
				},
				// TODO:	this is a quick fix for non-even/non-diametric sub layout
				// 			Note this likely hasn't been used/tested because 113 specifies
				//			a false 2nd sub (i.e. always even)- note for single sub receiving W, boost by 3dB
				{
					subOutbusNums.do({ | spkdex, i |
						var nfc;
						nfc = FoaNFC.ar( in, spkrDists.at(spkdex) );
						Out.ar(
							out_busnum + spkdex,
							// send W to subs, scaled by 3db, div by num of subs
							nfc[0] * 2.sqrt * numSubChans.reciprocal
						) * subgain.dbamp
					})
				}
			);
		});

		decoderLib.add( decSynthDef ); // add the synth to the decoder library
		postf("% (diametric) added.\n", decSpecs.synthdefName);
	}

	// NOTE: arrayOutIndices is [half of horiz] ++ [all elevation dome] spkr indices
	prLoadDiametricDomeDecoderSynth { |decSpecs|

		var domeOutbusNums, domeOutbusNumsFullHoriz, partialDomeDirections, subOutbusNums, subDirections;
		var halfHorizDirections, posElevDirections, halfSphereDirections, lowerStartDex, domeEndDex, domeDecoderMatrix;
		var sphereDecoderMatrix, subDecoderMatrix, decSynthDef;
		var lowerMatrix, lowerSum, lowerComp;
		var lowerK = -8.0.dbamp;

		/* --dome satellites-- */

		domeOutbusNums = decSpecs.arrayOutIndices; // half horiz & full dome spkr indices

		// append other half of horiz outbus nums for collecting matrix outputs below
		// select busnums with 0 elevation then collect their opposite's busnum
		domeOutbusNumsFullHoriz = domeOutbusNums
		++ domeOutbusNums.select({|busnum| spkrDirs[busnum][1]==0 }).collect({
			|spkdex| spkrOppDict[spkdex]
		});

		partialDomeDirections = domeOutbusNums.collect({|busnum| spkrDirs[busnum] });
		halfHorizDirections = partialDomeDirections.select{|item| item[1]==0 };
		posElevDirections = partialDomeDirections.select{|item| item[1]>0 };
		halfSphereDirections = halfHorizDirections ++ posElevDirections;

		// model full diametric decoder, and matrix
		sphereDecoderMatrix = FoaDecoderMatrix.newDiametric(halfSphereDirections, decSpecs.k).shelfFreq_(shelfFreq);

		// truncate to just lower speakers to calculate compensation matrix...
		lowerStartDex = (halfHorizDirections.size*2) + posElevDirections.size;

		lowerMatrix = Matrix.with(sphereDecoderMatrix.matrix.asArray[lowerStartDex..]);
		lowerSum = (lowerK / posElevDirections.size) * lowerMatrix.sumCols;
		lowerComp = Matrix.with(
			Array.fill(halfHorizDirections.size,{lowerSum})		// add to first half of horiz
			++ Array.fill2D(posElevDirections.size,4,{0})		// add 0 to elevation spkrs
			++ Array.fill(halfHorizDirections.size,{lowerSum})	// add to second half of horiz
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

		/*----------*/
		/* --subs-- */
		/*----------*/
		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections, decSpecs.k).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});

		/*------------------------*/
		/* --build the synthdef-- */
		/*------------------------*/
		decSynthDef = SynthDef( decSpecs.synthdefName, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, gate=1;
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			in = In.ar(in_busnum, decSpecs.numInputChans) * env; // B-Format signal
			in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation

			// include shelf filter on inpput or not. inferred from sphere FoaDecoderMatrix
			// because domeDecoderMatrix is actually just a Matrix object (no .shelfFreq)
			if( sphereDecoderMatrix.shelfFreq.isNumber, {
				in = FoaPsychoShelf.ar(
					in,
					//sphereDecoderMatrix.shelfFreq,
					\shelfFreq.kr(sphereDecoderMatrix.shelfFreq),
					sphereDecoderMatrix.shelfK.at(0),
					sphereDecoderMatrix.shelfK.at(1)
				)
			});

			// near-field compensate, decode, remap to rig
			domeOutbusNumsFullHoriz.do({ |spkdex, i|
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						FoaNFC.ar( in, spkrDists.at(spkdex) ),
						domeDecoderMatrix.fromRow(i)
					)
				)
			});

			// sub decode
			if( numSubChans.even, {
				subOutbusNums.do({ |spkdex, i|
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							FoaNFC.ar( in, spkrDists.at(spkdex) ),
							subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				// quick fix for non-even/non-diametric sub layout
				},{
					subOutbusNums.do({ |spkdex, i|
						var nfc;
						nfc = FoaNFC.ar( in, spkrDists.at(spkdex) );
						Out.ar(
							out_busnum + spkdex,
							nfc[0] * 2.sqrt * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					})
				}
			);
		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		postf("% (dome) added.\n", decSpecs.synthdefName);
	}


	prLoadSingleMatrixDecoder { |matrixPN|
		var subOutbusNums, subDirections, subDecoderMatrix;
		var path, name, matrix, ambiOrder, decSynthDef;


		/* --load decoder coefficient matrix-- */

		path = matrixPN.fullPath;
		name = matrixPN.fileNameWithoutExtension.asSymbol;

		matrix = Matrix.with(FileReader.read(path).asFloat);
		// determine order from matrix (must be 'full' order)
		ambiOrder = matrix.cols.sqrt.asInteger - 1;

		postf("Loading matrix decoder:\t\t\t%, order %\n", name, ambiOrder);


		/* --subs-- */

		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		// assume the layout is regular in this case
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});


		/* --build the synthdef-- */

		decSynthDef = SynthDef( name, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, freq=400, gate=1,
			shelfFreq = 400;  // shelfFreq defined but not used in single matrix decoder
			var in, env, sat_out, sub_out;

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			in = In.ar(in_busnum, (ambiOrder + 1).squared) * env;
			(ambiOrder == 1).if{ // transform only supported at first order atm
				in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation
			};

			// near-field compensate, decode, remap to rig
			// it's expected that the matrix has outputs for all speakers in the rig,
			// even if some are zeroed out in the matrix
			numSatChans.do({ | spkdex, i |
				var nfc;
						(ambiOrder == 1).if( // nfc only supported at first order atm
							{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
							{ nfc = in }
						);
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						nfc, matrix.fromRow(i)
					)
				)
			});

			// sub decode
			if( numSubChans.even, {
				subOutbusNums.do({ |spkdex, i|
					var nfc;
						(ambiOrder == 1).if( // nfc only supported at first order atm
							{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
							{ nfc = in }
						);
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							nfc, subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					)
				})
				// quick fix for non-even/non-diametric sub layout
				},{
					subOutbusNums.do({ |spkdex, i|
						var nfc;
						(ambiOrder == 1).if( // nfc only supported at first order atm
							{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
							{ nfc = in }
						);
						Out.ar(
							out_busnum + spkdex,
							nfc[0] * 2.sqrt * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					})
				}
			);

		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		matrixDecoderNames = matrixDecoderNames.add(name);
	}

	prLoadDualMatrixDecoder { |decName, matrixPN_LF, matrixPN_HF|
		var subOutbusNums, subDirections, subDecoderMatrix;
		var lf_array, hf_array;
		var path_lf, path_hf, name, matrix_lf, matrix_hf, ambiOrder, decSynthDef;

		/*-------------------------------------*/
		/* --load decoder coefficient matrix-- */
		/*-------------------------------------*/
		path_lf = matrixPN_LF.fullPath;
		path_hf = matrixPN_HF.fullPath;
		name = decName.asSymbol;

		lf_array = FileReader.read(path_lf).asFloat;
		hf_array = FileReader.read(path_hf).asFloat;

		// load decoder coefficient matrix
		matrix_lf = Matrix.with(lf_array);
		matrix_hf = Matrix.with(hf_array);

		// determine order from matrix (must be 'full' order)
		// NOTE: addition of matricies is a quick way to check whether they are the same
		ambiOrder = (matrix_lf + matrix_hf).cols.sqrt.asInteger - 1;

		postf("Loading dual matrix decoder:\t%, order %\n", name, ambiOrder);

		/*----------*/
		/* --subs-- */
		/*----------*/
		// always use all the subs
		subOutbusNums = (numSatChans..(numSatChans+numSubChans-1));

		// prepare stereo or diammetric decoder for subs if there's an even number of them
		// assume the layout is regular in this case
		if(numSubChans.even, {
			// only need to provide 1/2 of the directions for diametric decoder
			subDirections = subOutbusNums.keep( (subOutbusNums.size/2).asInt ).collect({
				|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});
			// note subDirections is only half of the subs
			subDecoderMatrix = (subDirections.size > 1).if(
				{ FoaDecoderMatrix.newDiametric(subDirections).shelfFreq_(shelfFreq) },
				// stereo decoder for 2 subs, symmetrical across x axis, cardioid decode
				{ FoaDecoderMatrix.newStereo(subDirections[0], 0.5).shelfFreq_(shelfFreq) }
			)
		});

		/*------------------------*/
		/* --build the synthdef-- */
		/*------------------------*/
		decSynthDef = SynthDef( name, {
			arg out_busnum=0, in_busnum, fadeTime=0.2, subgain=0, rotate=0, shelfFreq=400, gate=1;
			var in, env, sat_out, sub_out;
			var k = -180.dbamp; // RM-shelf gain (for cross-over)

			env = EnvGen.ar(
				Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
				gate, doneAction: 2
			);

			// read in (ambisonic 'b-format')
			in = In.ar(in_busnum, (ambiOrder + 1).squared) * env;

			// transform and physcoshelf only supported at first order atm
			(ambiOrder == 1).if{
				in = FoaTransform.ar(in, 'rotate', rotate); // rotate the listening orientation
			};

			// near-field compensate, decode, remap to rig
			// it's expected that the matrix has outputs for all speakers in the rig,
			// even if some are zeroed out in the matrix
			numSatChans.do({ | spkdex, i |
				var nfc;

				(ambiOrder == 1).if( // nfc only supported at first order atm
					{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
					{ nfc = in }
				);
				// LF
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						RMShelf2.ar( nfc, shelfFreq, k ),
						matrix_lf.fromRow(i)
					)
				);
				// HF
				Out.ar(
					out_busnum + spkdex, // remap decoder channels to rig channels
					AtkMatrixMix.ar(
						RMShelf2.ar( nfc, shelfFreq, -1 * k ),
						matrix_hf.fromRow(i)
					)
				);
			});

			// sub decode
			if( numSubChans.even, {

				subOutbusNums.do({ |spkdex, i|
					var nfc;
					(ambiOrder == 1).if( // nfc only supported at first order atm
						{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
						{ nfc = in }
					);
					Out.ar(
						out_busnum + spkdex,
						AtkMatrixMix.ar(
							nfc, subDecoderMatrix.matrix.fromRow(i)
						) * subgain.dbamp
					);
				})
				// quick fix for non-even/non-diametric sub layout
				},{
					subOutbusNums.do({ |spkdex, i|
						var nfc;
						(ambiOrder == 1).if( // nfc only supported at first order atm
							{ nfc = FoaNFC.ar( in, spkrDists.at(spkdex) ) },
							{ nfc = in }
						);
						Out.ar(
							out_busnum + spkdex,
							nfc[0] * 2.sqrt * numSubChans.reciprocal // send W to subs
						) * subgain.dbamp
					});
				}
			);

		});

		// add the synth to the decoder library
		decoderLib.add( decSynthDef );
		matrixDecoderNames = matrixDecoderNames.add(name);
	}

	prLoadDiscreteRoutingSynth { |decSpecs|

		decoderLib.add(
			SynthDef( decSpecs.synthdefName, {
				arg in_busnum, fadeTime = 0.3, subgain = 0, gate = 1;
				var in, env, out;
				var azims, elevs, directions, encoder, bf, decoders, sub_decodes;

				env = EnvGen.ar(
					Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
					gate, doneAction: 2 );

				in = In.ar(in_busnum, decSpecs.numInputChans)  * env;
				decSpecs.arrayOutIndices.do{ |outbus, i| Out.ar( outbus, in[i] ) };

				// TODO: confirm this BF encode-decode approach
				// SUBS
				if( config.numSubChans > 1, {
					// send the satellite signals to the sub(s) via planewave encoding,
					// then decode the b-format to mono sub decoders
					azims = decSpecs.arrayOutIndices.collect{|outdex, i| config.spkrAzimuthsRad[outdex] };
					elevs = decSpecs.arrayOutIndices.collect{|outdex, i| config.spkrElevationsRad[outdex] };
					directions = [azims, elevs].lace(azims.size + elevs.size).clump(2);

					encoder = FoaEncoderMatrix.newDirections(directions, nil); // nil = planewave encoding
					bf = FoaEncode.ar(in, encoder);

					// Mono decode for each sub
					decoders = config.numSubChans.collect{|i|
						FoaDecoderMatrix.newMono(
							config.spkrAzimuthsRad[config.numSatChans+i], // sub azimuth
							0,  // sub elevation - always 2D
							0.5 // cardiod decode
						);
					};

					sub_decodes = decoders.collect{ |decoder| FoaDecode.ar(bf, decoder); };
					// TODO add crossover to discrete routing
					// see commented-out code below for crossover scheme to be added
					config.numSubChans.do{|i| Out.ar(config.numSatChans+i, sub_decodes[i])};

					},{
						if( config.numSubChans == 1, {
							Out.ar( config.numSatChans, Mix.ar(in) * decSpecs.numInputChans.reciprocal )
						})
					}
				);
			});
		);

		// debug
		postf("% (discrete) added.\n", decSpecs.synthdefName);
	}

	// load every possible decoding SynthDef based on decAttList
	prLoadSynthDefs { |finishLoadCondition|

		decoderLib = CtkProtoNotes();

		/* build and load decoders specified in config*/
		decAttributes.do{ |decSpecs|
			switch( decSpecs.kind,
				\diametric,	{ this.prLoadDiametricDecoderSynth(decSpecs)},
				\dome,		{ this.prLoadDiametricDomeDecoderSynth(decSpecs)},
				\discrete,	{ this.prLoadDiscreteRoutingSynth(decSpecs)	}
			);
		};

		/* build and load decoders in matrix folder, if any */
		decoderMatricesPath !? {
			decoderMatricesPath.entries.do{ |bandType|

				bandType.isFolder.if{
					switch(bandType.folderName,

						"single", {
							bandType.filesDo{ |fl|
								// postf( "Found single matrix decoder:\t%\n",
								// fl.fileNameWithoutExtension );
								this.prLoadSingleMatrixDecoder(fl); // fl is pathname to matrix file
							}
						},

						"dual", {
							bandType.folders.do{ |decNameFldr|
								var pn_lf, pn_hf;
								// postf( "Found dual matrix decoder:\t\t%\n",
								// decNameFldr.folderName);
								(decNameFldr.files.size == 2).if({
									decNameFldr.filesDo{ |fl|
										var fn;
										fn = fl.fileNameWithoutExtension;
										case
										{ fn.endsWith("LF") } { pn_lf = fl }
										{ fn.endsWith("HF") } { pn_hf = fl };
									};

									this.prLoadDualMatrixDecoder(
										decNameFldr.folderName, pn_lf, pn_hf
									);
									},{
										warn(format("found a count of files other than 2 in \n%.\nExpecting 2 files: a HF matris and LF matrix. Skipping this folder.", decNameFldr))
									}
								);
							};
						}
					);
				}
			};
		};

		/* library of synths other than decoders */
		// one delay_gain_comp for every speaker output
		// signal order to comp stage is assumed to be satellites, subs, stereo
		synthLib = CtkProtoNotes(

			SynthDef(\delay_gain_comp, { arg in_busnum=0, out_busnum=0, masterAmp = 1.0, xover_hpf = 60, xover_lpf = 60;
				var in_sig, sat_sig, stereo_sig, sub_sig, subs_xover, sats_xover, subs_delayed, sats_delayed, outs;

				sat_sig = In.ar(in_busnum, numSatChans) * spkrGains.keep(numSatChans).dbamp;
				sub_sig = In.ar(in_busnum+numSatChans, numSubChans)
				* spkrGains[numSatChans..(numSatChans+numSubChans-1)].dbamp;
				stereo_sig = In.ar(in_busnum+totalArrayChans);

				subs_xover = LPF.ar( LPF.ar(sub_sig, xover_lpf), xover_lpf);
				sats_xover = HPF.ar( HPF.ar(sat_sig, xover_hpf), xover_hpf);

				sats_delayed = DelayN.ar( sats_xover,
					spkrDels.maxItem, spkrDels[0..(numSatChans-1)] );
				subs_delayed = DelayN.ar( subs_xover,
					spkrDels.maxItem, spkrDels[numSatChans..(numSatChans+numSubChans-1)] );


				// Note: no stereo delay/gain comp atm
				outs = sats_delayed ++ subs_delayed ++ stereo_sig;
				ReplaceOut.ar(out_busnum, outs * masterAmp);
			})
		);

		finishLoadCondition.test_(true).signal;
	}

	prInitSLHW { |initSR|
		//for linux
		slhw = SoundLabHardware.new(
			false, 						//useSupernova
			config.fixAudioInputGoingToTheDecoder, //fixAudioInputGoingToTheDecoder
			config.useFireface,			//useFireface
			config.midiPortName,		//midiPortName
			config.cardNameIncludes,	//cardNameIncludes
			config.jackPath, 			//jackPath
			numHardwareIns, 			//serverIns
			numHardwareOuts * 3, 		//serverOuts
			numHardwareOuts, 			//numHwOutChToConnectTo
			numHardwareIns, 			//numHwInChToConnectTo
			config.firefaceID, 			//firefaceID
			config.whichMadiInput, 		//whichMadiInput
			config.whichMadiOutput 		//whichMadiOutput
		);
		// slhw = SoundLabHardware.new(false,true,false,nil,nil,"/usr/local/bin/jackdmp",32,128); //for osx
		slhw.postln;
		slhw.startAudio(
			initSR, 				//newSR
			config.hwPeriodSize, 	//periodSize
			config.hwPeriodNum, 	//periodNum
		);
		slhw.addDependant(this);
	}

	prInitDefaultHW { |initSR|
		var so;
		// debug
		"initializing default hardware".postln;

		server = server ?? Server.default;
		server !? { server.serverRunning.if{ server.quit} };
		"REBOOTING".postln;

		so = server.options;
		so.sampleRate = initSR ?? 48000;
		so.memSize = 8192 * 16;
		so.numWireBufs = 64*8;
		so.device = "JackRouter";
		// numHardwareOuts*3 to allow fading between settings,
		// routed to different JACK busses
		so.numOutputBusChannels = numHardwareOuts * 3;
		so.numInputBusChannels = numHardwareIns;

		// the following will otherwise be called from update: \audioIsRunning
		server.waitForBoot({
			rbtTryCnt = rbtTryCnt+1;
			// in case sample rate isn't set correctly the first time (SC bug)
			if( server.sampleRate == initSR, {
				rbtTryCnt = 0;
				this.prLoadServerSide(server);
				},{ fork{
					1.5.wait;
					"reboot sample rate doesn't match requested, retrying...".postln;
					if(rbtTryCnt < 3,
						{ this.prInitDefaultHW(initSR) }, // call self
						{ this.changed(\reportStatus, "Error trying to change the sample rate after 3 tries!".warn)}
					)
				}}
			)
		});
	}

	prFindKernelDir { |kernelName|
		var kernelDir_pn;
		kernelDirPathName.folders.do({ |sr_pn|
			if( sr_pn.folderName.asInt == server.sampleRate, {
				sr_pn.folders.do({ |kernel_pn|
					if( kernel_pn.folderName.asSymbol == kernelName, {
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
					// debug.if{postf("%, %; ", i, val.asFloat)};
					debug.if{postf("% ", i)};
					data = data.add(val.asFloat);
				})
			});
			^data;
		});
	}

	prCheckArrayData {
		postf(
			"Checking array data...\nThese should equal % (numSatChans + numSubChans)\n[%, %, %, %, %, %]\n",
			numSatChans+numSubChans, spkrAzims.size, spkrElevs.size, spkrDists.size,
			spkrDels.size, spkrGains.size, spkrDirs.size
		);

		if (
			spkrAzims.size == spkrElevs.size and:
			spkrElevs.size == spkrDists.size and:
			spkrDists.size == spkrDels.size and:
			spkrDels.size == spkrGains.size and:
			spkrGains.size == totalArrayChans,
			{ "OK: Array sizes of rig dimensions match!".postln },
			{ "Mismatch in rig dimension array sizes!".warn }
		);

		"\n**** Speaker Gains, Distances, Delays ****".postln;
		"Chan: Gain Distance Delay".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("%:\t%\t%\t%\n",
				i, spkrGains[i], spkrDists[i], spkrDels[i]
			)
		});

		"\n**** Speaker Directions ****".postln;
		"Chan: Azimuth Elevation".postln;
		(numSatChans+numSubChans).do({ |i|
			postf("%:\t %\n", i, spkrDirs[i].raddeg)
		});
	}
}
