+ SoundLab {
	initRigDimensions {
		gainDict = IdentityDictionary.new(know: true);
		distDict = IdentityDictionary.new(know: true);
		delDict = IdentityDictionary.new(know: true);

		/* distances (m): */
		distDict.put( \default, [
			1.733, 1.774, 1.682,
			2.699, 2.755, 2.593, 2.568, 2.701, 2.553,
			2.324, 2.763, 2.784, 2.101, 2.774, 2.790,
			2.869, 2.940, 3.210, 2.950, 2.915, 3.051,
			2.244, 2.294, 2.296,
			3.048, 3.038, 3.046, 3.049
			]
		);

		/* calculate delays (sec): */
		delDict.put( \default,
			(distDict.default.maxItem - distDict.default) / 343;
		);
/*
		defSpkrAmps =[
			2.9, 3, 4.3,
			2.1, 1.5, 2.1, 1.9, 2, 2.5,
			3.2, 0.9, 0, 0.9, 0.3, 0.8,
			1.2, 0.9, 0.4, 0.3, 0, 0.6,
			1.9, 1.2, 1.3,
		    0, 6, 6, 5
		].neg.dbamp;
*/
		// same as above, but in dB
		gainDict.put( \default, [
			2.9, 3, 4.3,
			2.1, 1.5, 2.1, 1.9, 2, 2.5,
			3.2, 0.9, 0, 0.9, 0.3, 0.8,
			1.2, 0.9, 0.4, 0.3, 0, 0.6,
			1.9, 1.2, 1.3,
			0, 6, 6, 5
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
									delDict.put((knm++"_"++sr).asSymbol, this.parseFile(file_pn));
								}{nm.contains("distances")}{
									debug.if{postf("parsing distances for %, % \n", knm, sr)};
									distDict.put((knm++"_"++sr).asSymbol, this.parseFile(file_pn));
								}{nm.contains("gains")}{
									debug.if{postf("parsing gins for %, % \n", knm, sr)};
									gainDict.put((knm++"_"++sr).asSymbol, this.parseFile(file_pn));
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

	// parse text file for delays, distances, gains
	// expects individual .txt files for each
	// with \n -separated float values
	parseFile { |pathname|
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

	initDecoderAttributes {
		decAttributeList = [
			// decoder attributes:
			// decoderName, order, k, dimensions, firstHalfOutbusNums, numInChannels;

			/* ambisonic decoders */
			[\Sphere_24ch, \First, 'dual', 3, (0..11), 4],
			[\Sphere_18ch, \First, 'dual', 3, (3..11), 4],
			[\Sphere_12ch, \First, 'dual',3, (3..8), 4],
			[\Sphere_6ch, \First, 'dual',3, [8, 9, 20], 4],
			[\Hex, \First, 'dual', 2, (9..11), 4],
			// [\Rect, \First, 'dual', 2, [10,11], 4], // need to add a rotation of spkDirections
			// [\Stereo_UHJ, \First, 'dual', 2, [10,11], 4], // need to add a rotation of spkDirections
			/* Thru routing - Discrete Channels - note must have 'Thru' in the name*/
			[\Thru_All, \Discrete, \NA, 2, (0..11), 24],
			[\Thru_Hex, \Discrete, \NA, 2, (9..11), 6]
		];

		// build an Array from the above attributes
		decAttributes = [];
		decAttributeList.do({|attset|
			var dec, ord, k, dim, halfOuts, numIns;
			#dec, ord, k, dim, halfOuts, numIns = attset;
			decAttributes = decAttributes.add(
				IdentityDictionary(know: true).putPairs([
					\decType, dec,
					\ord, ord, // don't use "order" as a keyword, it's a Dictionary method!
					\k, k,
					\dim, dim,
					\halfOuts, halfOuts,
					\numInputChans, numIns,
					\defname, dec.asSymbol ++ '_' ++ ord.asSymbol ++ '_' ++ k.asSymbol
				])
			);
		});

	}

	/*	load speaker delays, distances, gains here because
	in the case of using kernels, it can be samplerate
	dependent, and so needs to happen after server has
	been initialized. */
	loadDelDistGain { |kernelName|
		var sr, key;
		sr = this.sampleRate;
		key = (kernelName++"_"++sr).asSymbol;
		("setting del dist gains to key: "++key).postln;

		spkrDists = distDict.at(
			if(usingKernels && distDict.includesKey(key), {
				debug.if{("using distances from kernel data file at "++key).postln};
				key;
				},{ this.setNoKernel; "using default distances".postln; \default }
		));
		spkrDels = delDict.at(
			if(usingKernels && delDict.includesKey(key), {
				debug.if{("using delays from kernel data file at "++key).postln};
				key;
				},{ this.setNoKernel; "using default delays".postln; \default }
		));
		spkrGains = gainDict.at(
			if(usingKernels && delDict.includesKey(key), {
				debug.if{("using gains from kernel data file at "++key).postln};
				key;
				},{ this.setNoKernel;"using default gains".postln; \default }
		));

		// diagnostics for testing
		debug.if{
			if (
				spkrAzims.size == spkrElevs.size and:
				spkrElevs.size == spkrDists.size and:
				spkrDists.size == spkrDels.size and:
				spkrDels.size == spkrGains.size and:
				spkrGains.size == numKernelChans and:
				spkrOppDict.size == spkrDirs.size,
				{ "OK: array sizes of rig dimensions match" },
				{ "mismatch in rig dimension array sizes!".warn;
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
			"\n\tSpeaker Gains, Distances, Delays".postln;
			(numSatChans+numSubChans).do({ arg i;
				post(
					"chan: "++ i ++" | "++
					"gain: "++ spkrGains[i]++" | "++
					"dist: "++ spkrDists[i]++" | "++
					"del: "++ spkrDels[i]++ "\n"
				)
			});
			"\n\tSpeaker Directions".postln;
			(numSatChans+numSubChans).do({ arg i;
				post(
					"chan: "++ i ++" | "++
					"dir: "++ spkrDirs[i].raddeg++" | "++ "\n"
				)
			});

		};
		"delays, gains, distances loaded".postln;
	}

	loadSynths {

		decoderLib = CtkProtoNotes();

		decAttributes.select({ |item|
			item.ord != \Discrete
		}).do({ |decInfo, i|
			var halfOuts, spkrOutbusNums, subOutbusNums, directions, subDirections;
			var matrix_dec, matrix_dec_sub, decSynthDef;
			// satellites
			halfOuts = decInfo.halfOuts;
			spkrOutbusNums = halfOuts ++ halfOuts.collect({|spkdex| spkrOppDict[spkdex]});
			// NOTE: only need to provide 1/2 of the directions for diametric decoder
			directions = halfOuts.collect({|busnum|
				switch(decInfo.dim,
					2, spkrDirs[busnum][0], // 2D
					3, spkrDirs[busnum]     // 3D
				);
			});
			// subs
			subOutbusNums = (24..27); // always use all the subs
			subDirections = [24, 25].collect({|busnum|
				spkrDirs[busnum][0]  // subs always 2D
			});

			// create a diametric decoder matrix
			matrix_dec = FoaDecoderMatrix.newDiametric(directions, decInfo.k);
			matrix_dec_sub = FoaDecoderMatrix.newDiametric(subDirections, decInfo.k);

			// build the synth
			decSynthDef =
			SynthDef( decInfo.defname,
				{ arg out_bus, in_bus, fadeTime = 0.3, subgain = 0, gate = 1;
					var in, env, out;
					in = In.ar(in_bus, 4); // B-Format signal
					env = EnvGen.ar(
						Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
						gate, doneAction: 2 );

					// include shelf filter?
					if( matrix_dec.shelfFreq.isNumber, {
						in = FoaPsychoShelf.ar(
							in,
							matrix_dec.shelfFreq,
							matrix_dec.shelfK.at(0),
							matrix_dec.shelfK.at(1)
						)
					});
					// near-field compensate, decode, remap to rig
					spkrOutbusNums.do({ arg spkdex, i;
						Out.ar(out_bus + spkdex, // remap decoder channels to rig channels
							AtkMatrixMix.ar(
								FoaNFC.ar( in, spkrDists.at(spkdex) ),
								matrix_dec.matrix.fromRow(i)
							)
						)
					});
					subOutbusNums.do({ arg spkdex, i;
						Out.ar(out_bus + spkdex, // remap decoder channels to rig channels
							AtkMatrixMix.ar(
								FoaNFC.ar( in, spkrDists.at(spkdex) ),
								matrix_dec_sub.matrix.fromRow(i)
							) * subgain.dbamp
						)
					});
			});
			// add the synth to the decoder library
			decoderLib.add( decSynthDef );
		});

		// load the discrete routing synths
		decAttributes.select({|item| item.ord == \Discrete}).do({ |decInfo, i|
			var decSynthDef;
			// build the synth
			decSynthDef = SynthDef( decInfo.defname,
				{ arg out_bus, in_bus, fadeTime = 0.3, subgain = 0, gate = 1;
					var in, env, out;
					in = In.ar(in_bus, decInfo.numInputChans);
					env = EnvGen.ar(
						Env( [0,1,0],[fadeTime, fadeTime],\sin, 1),
						gate, doneAction: 2 );
					/* NOTE no crossover added to discrete routing yet
					- see commented-out code below for crossover scheme to be added */
					Out.ar( decInfo.halfOuts.first, in * env );
			});
			// add the synth to the decoder library
			decoderLib.add( decSynthDef );
		});


		/* library of synths other than decoders */
		synthLib = CtkProtoNotes(

			SynthDef(\gain_comp, { arg in_bus=0, out_bus=0;
				var in_sig, sat_sig, stereo_sig, sub_sig;

				in_sig = In.ar(in_bus, 30); // 24 satellites, 4 subs, 2 stereo
(spkrGains[0..23]).dbamp.postln;
				sat_sig = in_sig[0..23] * (spkrGains[0..23]).dbamp;
				sub_sig = in_sig[24..27] * (spkrGains[24..27]).dbamp;
				stereo_sig = in_sig[28..29]; // no stereo gain comp atm

				ReplaceOut.ar(out_bus, sat_sig ++ sub_sig ++ stereo_sig );
			}),

			SynthDef(\delay_comp, { arg in_bus=0, out_bus=0;
				var in_sig, sat_sig, stereo_sig, sub_sig, subs_delayed, sats_delayed, outs;

				in_sig = In.ar(in_bus, 30); // 24 satellites, 4 subs, 2 stereo

				sat_sig = in_sig[0..23];
				sub_sig = in_sig[24..27];
				stereo_sig = in_sig[28..29];

				sats_delayed = DelayN.ar(
					sat_sig, spkrDels.maxItem, spkrDels[0..23] );
				subs_delayed = DelayN.ar(
					sub_sig, spkrDels.maxItem, spkrDels[24..27] );
				// no stereo gain comp atm
				outs = sats_delayed ++ subs_delayed ++ stereo_sig;
				ReplaceOut.ar(out_bus, outs);
			}),

			SynthDef(\masterAmp, { arg in_bus=0, out_bus=0, masterAmp = 1.0;
				Out.ar(out_bus,
					In.ar(in_bus, 30) * Lag.kr(masterAmp, 0.25)
				)
			}),

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
}

/* SCRATCH */
/*
SynthDef(\amp_delay_comp, { arg in_bus=0, out_bus=0, masterAmp = 1.0;
var in_sig, sat_sig, sub_sig, stereo_sig, sats_delayed, subs_delayed, outs;
// 24 satellites, 4 subs, 2 stereo
in_sig = In.ar(in_bus, 30);
sat_sig = in_sig.copyRange(0, 23);
sub_sig = in_sig.copyRange(24, 27);
stereo_sig = in_sig.copyRange(28, 29);

sats_delayed = DelayN.ar( sat_sig, spkrDels.maxItem, spkrDels, spkrAmps );
subs_delayed = DelayN.ar( sub_sig, subDels.maxItem, subDels, subAmps );

outs = sats_delayed ++ subs_delayed ++ stereo_sig;
Out.ar(out_bus, outs * Lag.kr(masterAmp, 0.25) * (0.dbamp)
);
}),

SynthDef(\amp_delay_xover, { arg in_bus=0, out_bus=0, hf_xover_f=90, lf_xover_f=65,
sat_gain = 1.0, sub_gain = 0.12589254117942, masterAmp = 1.0;
var in_sig, sat_sig, stereo_sig, sats_hf, stereo_hf, sat_groups, subs_sat, subs_ster, subs;
var xed_over, balance_gains, c, spk_dist, delays, max_dist, max_delay, delayed;
// 24 satellite channels + 2 stereo (subs expanded below)
in_sig = In.ar(in_bus, 26);
sat_sig = in_sig.copyRange(0, 23);
stereo_sig = in_sig.copyRange(24, 25);

// use second order filters (12dB/oct)
// includes stereo
sats_hf = PMHPF.ar(sat_sig, hf_xover_f) * sat_gain;
stereo_hf = PMHPF.ar(stereo_sig, hf_xover_f) * sat_gain;

// subs grouped into 4 arrays
// first array within group go to single sub,
// second array channels are shared between two subs
sat_groups = [
[[0,3,14,15],[8,9,20,21]], // front left sub
[[1,4,10,16],[5,9,17,21]], // front right
[[6,11,18,22],[2,5,12,17]], // rear right
[[7,13,19,23],[2,8,12,20]]  // rear left
];
// unpack into sub groups
subs_sat = sat_groups.collect({
|sub_group, i|
var solos, shared, xed_over, sum;
solos = sub_group[0].collect({|chan| sat_sig[chan] });
solos = solos.sum * sub_group[0].size.reciprocal;
// halve amp for signals split btwn 2 adjacent subs
shared = sub_group[1].collect({|chan| sat_sig[chan]});
shared = shared.sum * sub_group[1].size.reciprocal * 0.5;
sum = solos + shared;
PMLPF.ar(sum, lf_xover_f);
});
subs_ster = PMLPF.ar(stereo_sig, hf_xover_f);
subs = ( subs_sat + [subs_ster[0],subs_ster[1],0,0] ) * sub_gain;
// satellites[24], subs[4], stereo[2]
xed_over = sats_hf[0..23] ++ subs ++ stereo_hf;
balance_gains = [
3.9, 4, 5.3, // satellites
3.1, 2.5, 3.1, 2.9, 3, 3.5,
4.2, 1.9, 1, 1.9, 1.3, 1.8,
2.2, 1.9, 1.4, 1.3, 1, 1.6,
2.9, 2.2, 2.3,
0, 6, 6, 5, // subs
3, 2 // stereo
].neg.dbamp;

spk_dist = [
// distances measured to speaker woofer
// 24 satellites, clockwise from front/front-left, bottom up
1.716, 1.734, 1.67,
2.597, 2.619, 2.476, 2.424, 2.557, 2.473,
2.204, 2.650, 2.682, 2.033, 2.691, 2.701,
2.800, 2.847, 3.122, 2.883, 2.869, 3.004,
2.131, 2.191, 2.211,
// 4 subs FR, FL, RL, RR
2.650, 2.650, 2.650, 2.650,
// two stereo L, R
2.619, 2.619
];
// calculate max. distance
max_dist = spk_dist.maxItem;
// speed of sound in m/s
c = 343.0;
// factor out max. distance and calculate delays
delays = (max_dist - spk_dist) / c;
// calculate max. delay
max_delay = delays.maxItem;
// apply delays and gains, both arrays should be size 30
delayed = DelayN.ar( xed_over, max_delay, delays, balance_gains );

Out.ar(out_bus, delayed * Lag.kr(masterAmp, 0.25) * (0.dbamp));
}),*/

/* FOR CALIBRATION ONLY
// for through routing, no delay or xover
SynthDef(\amp_delay_comp, { arg in_bus=0, out_bus=0, hf_xover_f=90, lf_xover_f=65,
sat_gain = 1.0, sub_gain = 0.12589254117942, masterAmp = 1.0;
var in_sig, sat_sig, stereo_sig, sats_hf, stereo_hf, sat_groups, subs_sat, subs_ster, subs;
var xed_over, balance_gains, c, spk_dist, delays, max_dist, max_delay, delayed;

in_sig = In.ar(in_bus, 30);
balance_gains = [
3.9, 4, 5.3, // satellites
3.1, 2.5, 3.1, 2.9, 3, 3.5,
4.2, 1.9, 1, 1.9, 1.3, 1.8,
2.2, 1.9, 1.4, 1.3, 1, 1.6,
2.9, 2.2, 2.3,
0, 6, 6, 5, // subs
3, 2 // stereo
].neg.dbamp;
// FOR CALIBRATION ONLY
// subs routed through, no delay for testing just amp comp
delayed = in_sig * balance_gains;
Out.ar(out_bus, delayed * Lag.kr(masterAmp, 0.25) * (0.dbamp));
}),
*/