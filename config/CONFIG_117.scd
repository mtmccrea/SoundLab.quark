/* Config for SoundLab */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\labName,				"Sound Lab 117",
	\numHardwareOuts, 		32,
	\numHardwareIns, 		32,
	\numSatChans,			24,
	\numSubChans, 			4,
	\defaultDecoderName,	\Sph_24ch_all, // must match a name in \decAttributeList below
	\defaultKernel,			'1.0-open-no_desk-reference_ø',
	\stereoChanIndex,		[28, 29],
	\rotateDegree,			-90, // option to rotate the listening position to this angle
	\xOverHPF,				60,
	\xOverLPF,				60,
	\shelfFreq,				60,		// takes predecdence over listening Diameter
	\listeningDiameter,		1.5,	// sweet spot size in meters

	/* SoundLabHardware settings */

	\useFireface, 			true,
	\midiPortName,			"External MIDI- MIDI 1",
	// \midiPortName,			nil, // nil for no MIDI
	\cardNameIncludes,		"RME",
	// \cardNameIncludes,		nil, // for OSX
	\jackPath,				"/usr/bin/jackd",
	// \jackPath,				"/usr/local/bin/jackdmp", // for osx
	\fixAudioInputGoingToTheDecoder, true,
	\hwPeriodSize, 			256,
	\hwPeriodNum,			1,
	\firefaceID,            "000a35009caf3c69",
	\whichMadiInput,        nil,
	\whichMadiOutput,       nil,

	/* file paths */
	\kernelsPath,           "../../SoundLabSupport/SoundLabKernelsNew/",
	\decoderMatricesPath,	"../../SoundLabSupport/DecoderMatrices/"
])

/*
Speaker order is assumed to be satellites, subs, stereo (optional).
Stereo not included in these arrays, as there isn't correction for them.
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	[
		// DRC/sweep measured
		1.424964375, 1.453321875, 1.4214196875,
		2.3643065625, 2.3430384375, 2.275689375, 2.360761875, 2.360761875, 2.3075915625,
		2.0098378125, 2.6266134375, 2.665605, 1.9247653125, 2.6833284375, 2.6939625,
		2.800303125, 2.8534734375, 3.1228696875, 2.885375625, 2.8676521875, 2.99171625,
		2.2083403125, 2.2721446875, 2.2792340625,
		2.1941615625, 4.6896215625, 2.5238175, 5.182333125

		// // laser-measured
		// 1.425, 1.429, 1.41, // floor
		// 2.380, 2.355, 2.278, 2.367, 2.376, 2.330, // low (floor)
		// 2.034, 2.652, 2.689, 1.928, 2.704, 2.716, // mid
		// 2.830, 2.876, 3.149, 2.907, 2.894, 3.029, // upper
		// 2.227, 2.282, 2.295, // ceiling
		// 2.351, 3.546, 2.248, 3.625 //subs
	]
)
.put(\defaultSpkrGainsDB,
	// DRC/sweep measured
	[
		-6.689285260034, -6.40273282936, -7.532272924906,
		-4.281014951854, -4.220744116342, -4.295211891168, -4.392692495823, -4.177446477614, -4.307417173991,
		-5.795618767379, -2.368121235155, 0, -6.184681984072, -2.539437576843, -2.204020013528,
		-1.913758414869, -1.42226640109, -1.967317194443, -2.61164603202, -1.96552599135, -2.699314807101,
		-3.471111433932, -3.357144694629, -2.874821318353,
		-5.989075636287, -4.461296516644, -8.263734118956, -7.260509657314
	]

	// // db-meter measured
	// [	// update 2014.05.16
	// 	5, 5, 4,
	// 	3.5, 4, 3.9, 3.3, 3.3, 3.1,
	// 	2.9, 1.8, 0, 5.8, 2.9, 2.1,
	// 	1.2, 1, 1.8, 2.8, 2.9, 2.9,
	// 	2.8, 2.6, 1.7,
	// 	9, 10, 10.5, 10.5 // subs (0, 1, 1.5, 1.5) + 9
	// ].neg

	// BAK
	// 2.9, 3, 4.3,
	// 2.1, 1.5, 2.1, 1.9, 2, 2.5,
	// 3.2, 0.9, 0, 0.9, 0.3, 0.8,
	// 1.2, 0.9, 0.4, 0.3, 0, 0.6,
	// 1.9, 1.2, 1.3,
	// 13, 8, 14, 10 //subs, updated 2014.04.23
)
.put(\spkrAzimuthsRad,
	[
		58, 302, 180, 				// floor
		36, 324, 270, 220, 140, 90,	// lower
		0, 300, 240, 180, 120, 60,	// median
		40, 320, 270, 216, 144, 90,	// upper
		0, 238, 122,				// ceiling
		0, 270, 180, 90           	// subs
	].degrad
)
.put(\spkrElevationsRad,
	[
		-53, -53, -55,
		-32, -32, -32, -32, -32, -32,
		0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
		32, 32, 32, 32, 32, 32,
		55, 53, 53,
		0, 0, 0, 0	// subs
	].degrad
)
/*
each speaker channel and it's opposing channel, alternating
Array size must equal numDiametricPairsIncludingSubs * 2
*/
.put(\spkrOppDict,
	IdentityDictionary.new(know:true).putPairs([
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
		for dome: specify only the first half of out indexes for horizontal plane, followed by all upper dome out indices
		for discrete: specify all out indexes
numInChannels: number of input channels into the decoder/router (Int)
*/
.put(\decAttributeList,
	[
		/* --ambisonic decoders-- */
		// spheres - specify half of the array
		[\Sph_24ch_all,		\diametric,	'dual',	3,	(0..11),			4	],
		[\Sph_18ch_6x6x6,	\diametric,	'dual',	3,	(3..11),			4	],
		[\Sph_14ch_HexInBox,\diametric,	'dual',	3,	[3,4,6,7,9,10,11],	4	],
		[\Sph_12ch_HexInSquare,\diametric,'dual',	3,	[5,9,10,11,17],		4	],
		[\Sph_12ch_6x6,		\diametric,	'dual',	3,	(3..8),				4	],
		[\Sph_12ch_3x6x3,	\diametric,	'dual',	3,	(0..2)++(9..11),	4	],
		[\Sph_6ch_2x2x2,	\diametric,	'dual',	3,	[8, 9, 20],			4	],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_15ch_6x6x3,	\dome,		'dual', 3,	(9..11)++(15..23), 	4	],
		[\Dome_13ch_6x4x3,	\dome,		'dual', 3,	(9..11)++[15,16,18,19,21,22,23], 4],
		[\Dome_11ch_4x4x3,	\dome,		'dual', 3,	[10,11]++[15,16,18,19,21,22,23], 4],
		[\Dome_10ch_6x4,	\dome,		'dual', 3,	[10,11]++[15,16,18,19],4],
		[\Dome_9ch_6x3,		\dome,		'dual', 3,	(9..11)++(21..23), 	4	],

		// horizontal-only
		[\Horiz_Quad_Rect,	\diametric,	'dual',	2,	[10,11],	4	],
		[\Horiz_Hex,			\diametric,	'dual',	2,	(9..11),	4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\All,		\discrete,	\NA,	2,	(0..23),	24	],
		[\Hex,		\discrete,	\NA,	2,	(9..14),	6	]
	]
)
;

/*

Detailed satellite info:
// distance HF, MF, LF per speaker
[
[1.435, nil, 1.433],[1.437, nil, 1.435],[1.418, nil, 1.415] // floor
[2.389,2.384,2.400],[2.360,2.358,2.376],[2.285,2.285,2.307],[2.375,2.371,2.391],[2.379,2.377,2.402],[2.335,2.332,2.354], // low (floor)
[2.042,2.038,2.057],[2.660,2.656,2.673],[2.695,2.693,2.712],[1.931,1.931,1.950],[2.708,2.708,2.725],[2.722,2.720,2.738], // mid
[2.840,2.834,2.850],[2.886,2.881,2.893],[3.157,3.153,3.168],[2.915,2.912,2.927],[2.901,2.897,2.915],[3.030,3.032,3.053] // upper
[2.234,nil,2,239],[2.291,nil,2.294],[2.302,nil,2.306] // ceiling
]
// elevation HF, MF, LF per speaker
[
[-50,nil,-56],[-50,nil,-56],[-52,nil,-58] // floor
[-35,-33,-28],[-35,-33,-28],[-35,-33,-28],[-35,-33,-28],[-35,-33,-28],[-35,-33,-28], // low (floor)
[4,1,-4],[-3,-0.5,3],[3,0.5,-3],[-4,-1,4],[3,0.5,-3],[-3,-0.5,3] // mid
[35,33,30],[35,33,29],[34,32,29],[35,33,30],[35,33,30],[34,32,29] // upper
[53,nil,56],[52,nil,55],[52,nil,55] // ceiling
*/
