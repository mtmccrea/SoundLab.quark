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
	\jconvHWOutChannel,		66,
	\stereoChanIndex,		[11, 1],
	\rotateDegree,			90, // option to rotate the listening position to this angle
	\xOverHPF,				60,
	\xOverLPF,				60,
	\shelfFreq,				60,	// takes predecdence over listening Diameter
	\listeningDiameter,		2,	// sweet spot size in meters
	\stereoGain,			-3,		// gain on stereo bus in dB, to balance with decoders

	/* SoundLabHardware settings */

	\useFireface, 			true, //2016.01.22 Marcin - testing
	\midiDeviceName,			"External MIDI-MADIFXtest MIDI 1", // 205
	\midiPortName,			"External MIDI-MADIFXtest MIDI 1", // 205
	// \midiPortName,			nil, // nil for no MIDI
	\cardNameIncludes,		"RME", // 205
	// \cardNameIncludes,		nil, // for OSX
	\jackPath,				"/usr/bin/jackd", // 205
	// \jackPath,				"/usr/local/bin/jackdmp", // for osx
	\fixAudioInputGoingToTheDecoder, true,
	\hwPeriodSize, 			256,
	\hwPeriodNum,			1,
	\firefaceID,            "000a35002dd2c77a", //"000a3500c1da0056",
	\whichMadiInput,        2,
	\whichMadiOutput,       2,

	/* file paths */
	\kernelsPath,			"/home/audioadmin/Documents/SoundLabSupport/SoundLabKernels/",
	\decoderMatricesPath,	"/home/audioadmin/Documents/SoundLabSupport/DecoderMatrices/",
])

// no leading '/' in relative folder path
.put(\kernelSpec, [
	// note popup array size = number of popups
	// column defines selection within one dropdown.
	// [ path relative to \kernelsPath, 				[...check box (true) attributes],	[...dropdown  attributes] ]
	["desk_curtains_screen/k_9_1.365w_18c_3b_0.77t/",	['desk', 'curtains', 'screen'],	['open',	'reference'] ],
	["desk_curtains_screen/k_24_1.365w_18c_5b_0.77t/",	['desk', 'curtains', 'screen'],	['moderate','reference'] ],
	["desk_curtains_screen/k_24_1.365w_18c_9b_0.77t/",	['desk', 'curtains', 'screen'],	['tight',	'reference'] ],
	["desk_curtains_screen/m_9_1.365w_18c_3b_0.77t/",	['desk', 'curtains', 'screen'], ['open',	'realtime'] ],
	["desk_curtains_screen/m_24_1.365w_18c_5b_0.77t/",	['desk', 'curtains', 'screen'], ['moderate','realtime'] ],
	["desk_curtains_screen/m_24_1.365w_18c_9b_0.77t/",	['desk', 'curtains', 'screen'], ['tight',	'realtime'] ],

	["desk_full_curtains/k_9_1.365w_18c_3b_0.77t/",		['desk', 'curtains'], ['open',	'reference'] ],
	["desk_full_curtains/k_24_1.365w_18c_5b_0.77t/",	['desk', 'curtains'], ['moderate','reference'] ],
	["desk_full_curtains/k_24_1.365w_18c_9b_0.77t/",	['desk', 'curtains'], ['tight',	'reference'] ],
	["desk_full_curtains/m_9_1.365w_18c_3b_0.77t/",		['desk', 'curtains'], ['open',	'realtime'] ],
	["desk_full_curtains/m_24_1.365w_18c_5b_0.77t/",	['desk', 'curtains'], ['moderate','realtime'] ],
	["desk_full_curtains/m_24_1.365w_18c_9b_0.77t/",	['desk', 'curtains'], ['tight',	'realtime'] ],

	[ "open_floor_bare_walls/k_9_1.365w_18c_3b_0.77t/",		[], ['open',	'reference'] ],
	[ "open_floor_bare_walls/k_24_1.365w_18c_5b_0.77t/",	[], ['moderate','reference'] ],
	[ "open_floor_bare_walls/k_24_1.365w_18c_9b_0.77t/",	[], ['tight',	'reference'] ],
	[ "open_floor_bare_walls/m_9_1.365w_18c_3b_0.77t/", 	[], ['open',	'realtime'] ],
	[ "open_floor_bare_walls/m_24_1.365w_18c_5b_0.77t/",	[], ['moderate','realtime'] ],
	[ "open_floor_bare_walls/m_24_1.365w_18c_9b_0.77t/",	[], ['tight',	'realtime'] ],


	[ "open_floor_curtains_screen/k_9_1.365w_18c_3b_0.77t/",	['curtains', 'screen'],	['open',	'reference'] ],
	[ "open_floor_curtains_screen/k_24_1.365w_18c_5b_0.77t/",	['curtains', 'screen'],	['moderate','reference'] ],
	[ "open_floor_curtains_screen/k_24_1.365w_18c_9b_0.77t/",	['curtains', 'screen'],	['tight',	'reference'] ],
	[ "open_floor_curtains_screen/m_9_1.365w_18c_3b_0.77t/",	['curtains', 'screen'],	['open',	'realtime'] ],
	[ "open_floor_curtains_screen/m_24_1.365w_18c_5b_0.77t/",	['curtains', 'screen'],	['moderate','realtime'] ],
	[ "open_floor_curtains_screen/m_24_1.365w_18c_9b_0.77t/",	['curtains', 'screen'],	['tight',	'realtime'] ],

	[ "open_floor_full_curtains/k_9_1.365w_18c_3b_0.77t/",		['curtains'], ['open',	'reference'] ],
	[ "open_floor_full_curtains/k_24_1.365w_18c_5b_0.77t/",		['curtains'], ['moderate','reference'] ],
	[ "open_floor_full_curtains/k_24_1.365w_18c_9b_0.77t/",		['curtains'], ['tight',	'reference'] ],
	[ "open_floor_full_curtains/m_9_1.365w_18c_3b_0.77t/",		['curtains'], ['open',	'realtime'] ],
	[ "open_floor_full_curtains/m_24_1.365w_18c_5b_0.77t/",		['curtains'], ['moderate','realtime'] ],
	[ "open_floor_full_curtains/m_24_1.365w_18c_9b_0.77t/", 	['curtains'], ['tight',	'realtime'] ],

])

/*
speaker order is assumed to be satellites, subs, stereo (optional)
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	// [
	// 	// drc-measured speaker distances - curtains fully open
	// 	3.105146250000,
	// 	3.629760000000,
	// 	3.378087187500,
	// 	2.949180000000,
	// 	3.314282812500,
	// 	3.608491875000,
	// 	3.023618437500,
	// 	3.576589687500,
	// 	3.441891562500,
	// 	2.888920312500,
	// 	3.317827500000,
	// 	3.601402500000,
	// 	4.087024687500,
	// 	3.906245625000,
	// 	3.161861250000,
	// 	3.721921875000,
	// 	3.363908437500,
	// 	3.661662187500,
	// 	2.654970937500,
	// 	2.94,
	// 	2.920
	// ]
	[
		// drc-measured speaker distances - curtains fully drawn
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
	// [
	// 	// drc-measured speaker delays - curtains fully open
	// 	0.002885416667,
	// 	0.001343750000,
	// 	0.002083333333,
	// 	0.003343750000,
	// 	0.002270833333,
	// 	0.001406250000,
	// 	0.003125000000,
	// 	0.001500000000,
	// 	0.001895833333,
	// 	0.003520833333,
	// 	0.002260416667,
	// 	0.001427083333,
	// 	0.000000000000,
	// 	0.000531250000,
	// 	0.002718750000,
	// 	0.001072916667,
	// 	0.002125000000,
	// 	0.001250000000,
	// 	0.004208333333,
	// 	0.008437500000,
	// 	0.010239583333
	// ]
	[
		// drc-measured speaker delays - curtains fully drawn
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
	// [
	// 	// drc-measured speaker gains - curtains fully open
	// 	-2.916926353663,
	// 	-1.310620951377,
	// 	-1.784970160711,
	// 	-2.865487100518,
	// 	-1.576996496960,
	// 	-0.856302204580,
	// 	-1.971458405998,
	// 	-1.670950107778,
	// 	-1.316821750950,
	// 	-2.602723157621,
	// 	-1.734256776659,
	// 	-1.926251956036,
	// 	0.000000000000,
	// 	-0.855207142058,
	// 	-1.836792942210,
	// 	-0.804194568331,
	// 	-1.211393635735,
	// 	-1.619750725390,
	// 	-3.707459480582,
	// 	-8.755954210527,
	// 	-6.167524852745
	// ]

	// drc-measured gains- curtains fully drawn
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
		[\Horiz_Dodec,			\diametric,	'dual',		2,	(0..5),		4, 6 ],
		[\Horiz_Hex_Point,		\diametric,	'dual',		2,	[0,2,4],	4, 6 ],
		[\Horiz_Hex_Flat,		\diametric,	'dual',		2,	[11,1,3],	4, 6 ],
		[\Horiz_Quad_Long,		\diametric,	'dual',		2,	[11,1],		4, 6 ],
		[\Horiz_Quad_Cross,		\diametric,	'dual',		2,	[0,3],		4, 6 ],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_19ch_12x6x1,	\dome,		'dual', 	3,	(0..5)++(12..18), 		4, 6 ],
		[\Dome_13ch_6x6x1,	\dome,		'dual', 	3,	[0, 2, 4]++(12..18), 	4, 6 ],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		// includes additional channels from converter not connected to speakers
		[\All,			\discrete,	\NA,	2,	(0..31),				32, 0 ],
		// clockwise, include sub chans
		[\Hex_Point_cw,   \discrete, \NA,	2,	Array.series(6, 0, 2) ++ [19,20],	8, 0 ],
		[\Hex_Flat_cw,	  \discrete, \NA,	2,	[11,1,3,5,7,9] ++ [19,20],			8, 0 ],
		[\Quad_Square_cw, \discrete, \NA,	2,	Array.series(4, 0, 3) ++ [19,20],	6, 0 ],
		[\Quad_Rect_cw,   \discrete, \NA,	2,	[11, 1, 5, 7] ++ [19,20],			6, 0 ],
		// counter-clockwise, include sub chans
		[\Hex_Point_ccw,  \discrete, \NA,	2,	Array.series(6, 0, 2).reverse.rotate ++ [19,20],	8, 0 ],
		[\Hex_Flat_ccw,	  \discrete, \NA,	2,	[11,1,3,5,7,9].reverse.rotate ++ [19,20],			8, 0 ],
		[\Quad_Square_ccw,\discrete, \NA,	2,	Array.series(4, 0, 3).reverse.rotate ++ [19,20],	6, 0 ],
		[\Quad_Rect_ccw,  \discrete, \NA,	2,	[11, 1, 5, 7].reverse.rotate ++ [19,20],			6, 0 ]
	]
)
;

/* Rig Measurements
// distances
d = [3.111, 3.625, 3.38, 2.94, 3.308, 3.625, 3.045, 3.610, 3.482, 2.920, 3.335, 3.619, 4.107, 3.907, 3.150, 3.726, 3.382, 3.679, 2.65]
d.size
~delays = (d - d.minItem)/344

// measured gains
g = [ 79.4, 79.7, 77.8, 79.2, 78.1, 77.4, 78.8, 78.3, 77.2, 78.8, 78.1, 78.8, 77.0, 78.6, 77.4, 76.9, 76.1, 79, 80.7].size
// target the average gain
~gains = (g - g.mean).neg // .neg for db gain applied
[ -1.121052631579, -1.421052631579, 0.47894736842105, -0.92105263157896, 0.17894736842105, 0.87894736842104, -0.52105263157895, -0.021052631578954, 1.078947368421, -0.52105263157895, 0.17894736842105, -0.52105263157895, 1.278947368421, -0.32105263157895, 0.87894736842104, 1.378947368421, 2.178947368421, -0.72105263157896, -2.421052631579 ].neg
*/