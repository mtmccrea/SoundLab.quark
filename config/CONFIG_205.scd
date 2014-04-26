/* Config for SoundLab 205 */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\numHardwareOuts, 		32,
	\numHardwareIns, 		32,
	\numSatChans,			19,
	\numSubChans, 			2,
	\defaultDecoderName,	\Dodec,
	\defaultOrder,			1,
	\defaultKernel,			\decor_700,
	\stereoChanIndex,		[11, 1],

	/* SoundLabHardware settings */

	\useFireface, 			false,
	\midiPortName,			"External MIDI-MADIFXtest MIDI 1", // 205
	// \midiPortName,			nil, // nil for no MIDI
	\cardNameIncludes,		"RME", // 205
	// \cardNameIncludes,		nil, // for OSX
	\jackPath,				"/usr/bin/jackd", // 205
	// \jackPath,				"/usr/local/bin/jackdmp", // for osx
	\fixAudioInputGoingToTheDecoder, true,
	\hwPeriodSize, 			256,
	\hwPeriodNum,			1,
	\firefaceID,            "000a3500c1da0056",
	\whichMadiInput,        2,
	\whichMadiOutput,       2
])

/*
speaker order is assumed to be satellites, subs, stereo (optional)
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	[
		3.111, 3.625, 3.38, 2.94, 3.308, 3.625,
		3.045, 3.610, 3.482, 2.920, 3.335, 3.619,
		4.107, 3.907, 3.150, 3.726, 3.382, 3.679, 2.65,
		//subs
		2.94, 2.920
	]
)
.put(\defaultSpkrGainsDB,
	/*	[  // measured values, rescaled to target the average
	-1.121052631579, -1.421052631579, 0.47894736842105,
	-0.92105263157896, 0.17894736842105, 0.87894736842104,
	-0.52105263157895, -0.021052631578954, 1.078947368421,
	-0.52105263157895, 0.17894736842105, -0.52105263157895,
	1.278947368421, -0.32105263157895, 0.87894736842104,
	1.378947368421, 2.178947368421, -0.72105263157896,
	-2.421052631579,
	// subs
	0, 0
	]*/
	// above vals normalized to 0db max
	[	-3.3, -3.6, -1.7, -3.1, -2, -1.3,
		-2.7, -2.2, -1.1, -2.7, -2, -2.7,
		-0.9, -2.5, -1.3, -0.8, 0, -2.9,
		-4.6,
		-2.179, -2.179
	]
)
.put(\spkrAzimuthsRad, (
	Array.series(12, 360, -30)
	// upper ring
	++[39.3,323.4,278.3,204.2,159.3,84.2]
	// overhead
	++[0]
	//subs
	++[90, 270]
	).degrad
)
.put(\spkrElevationsRad, (
	12.collect({0})
	// upper ring
	++[23,42.6,23,42.6,23,42.6] //approx
	// overhead
	++[90]
	//subs
	++[0, 0]
	).degrad
)
/*
each speaker channel and it's opposing channel, alternating
Array size must equal numDiametricPairsIncludingSubs * 2
*/
.put(\spkrOppDict,
	IdentityDictionary.new(know:true).putPairs([
		0, 6, 6, 0,
		1,7,7,1,
		2,8,8,2,
		3,9,9,3,
		4,10,10,4,
		5,11,11,5,
		// subs
		19, 20, 20, 19
	])
)

/*
--decoder attributes--
decoderName, kind, order, k, dimensions, arrayOutIndices, numInChannels;
decoderName: unique name as it will show up on GUI (Symbol)
kind: diametric, discrete (Symbol)
order; 1, 2, etc.. (Int)
k: dual (Symbol)
dimensions 2 or 3 (Int),
arrayOutIndices (Array of ints):
		for diametric: specify only the first half of out indexes
		for discrete: specify all out indexes
numInChannels: number of input channels into the decoder/router (Int)
*/
.put(\decAttributeList,
	[
		/* --ambisonic decoders-- */
		[\Dodec,			\diametric,	1,	'dual',		2,	(0..5),					4	],
		[\Hex,				\diametric,	1,	'dual',		2,	[0,2,4],				4	],
		[\Quad_Long,		\diametric,	1,	'dual',		2,	[11,1],					4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\Thru_All,			\discrete,	\NA,	\NA,	2,	(0..18),				24	],
		[\Thru_Hex,			\discrete,	\NA,	\NA,	2,	Array.series(6, 0, 2),	6	],
		[\Thru_Quad_Square,	\discrete,	\NA,	\NA,	2,	Array.series(4, 0, 3),	4	],
		[\Thru_Quad_Rect,	\discrete,	\NA,	\NA,	2,	[11, 1, 4, 6],			4	]
	]
)
;

/* Rig Measurements
// distances
d = [3.111, 3.625, 3.38, 2.94, 3.308, 3.625, 3.045, 3.610, 3.482, 2.920, 3.335, 3.619, 4.107, 3.907, 3.150, 3.726, 3.382, 3.679, 2.65]
d.size
~delays = (d - d.minItem)/344

// measured gains
g = [ 79.4, 79.7, 77.8, 79.2, 78.1, 77.4, 78.8, 78.3, 77.2, 78.8, 78.1, 78.8, 77.0, 78.6, 77.4, 76.9, 76.1, 79, 80.7]
// target the average gain
~gains = (g - g.mean).neg // .neg for db gain applied
[ -1.121052631579, -1.421052631579, 0.47894736842105, -0.92105263157896, 0.17894736842105, 0.87894736842104, -0.52105263157895, -0.021052631578954, 1.078947368421, -0.52105263157895, 0.17894736842105, -0.52105263157895, 1.278947368421, -0.32105263157895, 0.87894736842104, 1.378947368421, 2.178947368421, -0.72105263157896, -2.421052631579 ].neg

*/