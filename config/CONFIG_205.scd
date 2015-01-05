/* Config for SoundLab 205 */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\labName,				"Media Lab 205",
	\numHardwareOuts, 		32,
	\numHardwareIns, 		32,
	\numSatChans,			19,
	\numSubChans, 			2,
	\defaultDecoderName,	\Horiz_Dodec,
	\defaultKernel,			\basic_balance,
	\stereoChanIndex,		[11, 1],
	\rotateDegree,			-90, // option to rotate the listening position to this angle
	\xOverHPF,				60,
	\xOverLPF,				60,
	\shelfFreq,				60,

	/* SoundLabHardware settings */

	\useFireface, 			true,
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
	\whichMadiOutput,       2,

	/* file paths */
	\kernelsPath,			"/home/audioadmin/Documents/SoundLabSupport/SoundLabKernels",
	\decoderMatricesPath,	"/home/audioadmin/Documents/SoundLabSupport/DecoderMatrices/",
	])

/*
speaker order is assumed to be satellites, subs, stereo (optional)
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	[
		// drc-measured speaker distances
		3.101601562500,
		3.622670625000,
		3.370997812500,
		2.942090625000,
		3.310738125000,
		3.612036562500,
		3.030707812500,
		3.587223750000,
		3.456070312500,
		2.899554375000,
		3.324916875000,
		3.604947187500,
		4.083480000000,
		3.899156250000,
		3.151227187500,
		3.718377187500,
		3.370997812500,
		3.665206875000,
		2.658515625000,
		2.94,	//1.343436562500, // laser-meaured dist added back
		2.920,	//1.573841250000, // laser-meaured dist added back
	]

	// laser-measured speaker distances
	// [
	// 	3.111, 3.625, 3.38, 2.94, 3.308, 3.625,
	// 	3.045, 3.610, 3.482, 2.920, 3.335, 3.619,
	// 	4.107, 3.907, 3.150, 3.726, 3.382, 3.679, 2.65,
	// 	//subs
	// 	2.94, 2.920
	// ]
)
.put(\defaultSpkrDelays,
	[
		0.002885416667,
		0.001354166667,
		0.002093750000,
		0.003354166667,
		0.002270833333,
		0.001385416667,
		0.003093750000,
		0.001458333333,
		0.001843750000,
		0.003479166667,
		0.002229166667,
		0.001406250000,
		0.000000000000,
		0.000541666667,
		0.002739583333,
		0.001072916667,
		0.002093750000,
		0.001229166667,
		0.004187500000,
		0.008052083333,
		0.007375000000
	]
)
.put(\defaultSpkrGainsDB,

	// drc-measured gains
	[
		-2.853001364430,
		-1.525170826009,
		-2.109969009637,
		-3.377186028117,
		-1.678576603716,
		-1.030284469495,
		-2.479628956900,
		-1.604613020483,
		-1.416415263578,
		-3.022110274314,
		-1.748577562689,
		-2.107069779851,
		0.000000000000,
		-1.595358035166,
		-1.782114489222,
		-1.162173731652,
		-1.276176461716,
		-1.523877954755,
		-4.004470360335,
		-10.487857358413,
		-8.988210528668
	]

	// db meter measured gains:

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
	// [	-3.3, -3.6, -1.7, -3.1, -2, -1.3,
	// 	-2.7, -2.2, -1.1, -2.7, -2, -2.7,
	// 	-0.9, -2.5, -1.3, -0.8, 0, -2.9,
	// 	-4.6,
	// 	/*-2.179, -2.179*/
	// 	-22.179, -22.179 //subs mod by Marcin 2014.05.05 by ear
	// ]
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
decoderName, kind, k, dimensions, arrayOutIndices, numInChannels;
decoderName: unique name as it will show up on GUI (Symbol)
kind: diametric, discrete (Symbol)
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
		[\Horiz_Dodec,			\diametric,	'dual',		2,	(0..5),		4	],
		[\Horiz_Hex_Point,		\diametric,	'dual',		2,	[0,2,4],	4	],
		[\Horiz_Hex_Flat,		\diametric,	'dual',		2,	[11,1,3],	4	],
		[\Horiz_Quad_Long,		\diametric,	'dual',		2,	[11,1],		4	],
		[\Horiz_Quad_Cross,		\diametric,	'dual',		2,	[0,3],		4	],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_19ch_12x6x1,	\dome,		'dual', 	3,	(0..5)++(12..18), 		4	],
		[\Dome_13ch_6x6x1,	\dome,		'dual', 	3,	[0, 2, 4]++(12..18), 	4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\All,			\discrete,	\NA,	2,	(0..18),				24	],
		[\Hex_Point_Fwd,\discrete,	\NA,	2,	Array.series(6, 0, 2),	6	],
		[\Hex_Flat_Fwd,	\discrete,	\NA,	2,	[11,1,3,5,7,9],			6	],
		[\Quad_Square,	\discrete,	\NA,	2,	Array.series(4, 0, 3),	4	],
		[\Quad_Rect,	\discrete,	\NA,	2,	[11, 1, 5, 7],			4	]
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