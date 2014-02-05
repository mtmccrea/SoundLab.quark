// setup
"SETUP RAAAAAAAAAAAAAAAAAAAM".postln;

IdentityDictionary(know: true)
.put(\numHardwareOuts, 		32)
.put(\numHardwareIns, 		32)
.put(\numSatChans,			24)
.put(\numSubChans, 			4)
.put(\defaultDecoderName,	\Sphere_24ch_first_dual)
.put(\defaultKernel,		\decor_700)

// speaker order is assumed to be satellites, subs, stereo (optional)
// Array size must equal \numSatChans + \numSubChans
// see prLoadSynthDefs for how channel mappings are used
.put(\defaultSpkrDistances,
	[
		1.733, 1.774, 1.682,
		2.699, 2.755, 2.593, 2.568, 2.701, 2.553,
		2.324, 2.763, 2.784, 2.101, 2.774, 2.790,
		2.869, 2.940, 3.210, 2.950, 2.915, 3.051,
		2.244, 2.294, 2.296,
		3.048, 3.038, 3.046, 3.049 // subs
	]
)
.put(\defaultSpkrGainsDB,
	[
		2.9, 3, 4.3,
		2.1, 1.5, 2.1, 1.9, 2, 2.5,
		3.2, 0.9, 0, 0.9, 0.3, 0.8,
		1.2, 0.9, 0.4, 0.3, 0, 0.6,
		1.9, 1.2, 1.3,
		0, 6, 6, 5 // subs
	].neg
)
.put(\spkrAzimuthsRad,
	[
		60, 300, 180, 				// floor
		37, 324, 270, 220, 141, 90,	// lower
		0, 300, 240, 180, 120, 60,	// median
		40, 321, 271, 217, 144, 91,	// upper
		0, 240, 120,				// ceiling
		0, 270, 180, 90           	// subs
	].degrad
)
.put(\spkrElevationsRad,
	[
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
	].degrad
)
// each speaker channel and it's opposing channel, alternating
// Array size must equal (\numSatChans + \numSubChans) * 2
.put(\spkrOppDict,
	IdentityDictionary.new(know:true).putPairs([

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

/* --decoder attributes-- */
// decoderName, kind, order, k, dimensions, arrayOutIndices, numInChannels;
// decoderName: unique name as it will show up on GUI (Symbol)
// kind: diametric, discrete (Symbol)
// order; first, second, etc.. (Symbol)
// k: dual (Symbol)
// dimensions 2 or 3 (Int),
// arrayOutIndices (Array of ints):
// 		for diametric: specify only the first half of out indexes
// 		for discrete: specify all out indexes
// numInChannels: number of input channels into the decoder/router (Int)
.put(\decAttributeList,
	[
		/* --ambisonic decoders-- */
		[\Sphere_24ch,	\diametric,	\first,	'dual',	3,	(0..11),	4	],
		[\Sphere_18ch,	\diametric,	\first,	'dual',	3,	(3..11),	4	],
		[\Sphere_12ch,	\diametric,	\first,	'dual',	3,	(3..8),		4	],
		[\Sphere_6ch,	\diametric,	\first,	'dual',	3,	[8, 9, 20],	4	],
		[\Hex,			\diametric,	\first,	'dual',	2,	(9..11),	4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\Thru_All,		\discrete,	nil,	nil,	2,	(0..23),	24	],
		[\Thru_Hex,		\discrete,	nil,	nil,	2,	(9..11),	6	]
	]
)
;