/* Config for SoundLab */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\labName,				"Sound Lab 113",
	\numHardwareOuts, 		16,
	\numHardwareIns, 		16,
	\numSatChans,			10,
	\numSubChans, 			1, //2, // TODO, this is actually 1
	\defaultDecoderName,	\Hex,
	\jconvHWOutChannel,		0,
	\stereoChanIndex,		[0, 1],
	\rotateDegree,			90, // option to rotate the listening position to this angle
	\xOverHPF,				80,
	\xOverLPF,				80,
	\shelfFreq,				400,	// takes predecdence over listening Diameter
	\listeningDiameter,		0.5,	// sweet spot size in meters

	/* SoundLabHardware settings */

	// \useFireface, 			true,
	\useFireface, 			false, // for testing
	// \midiPortName,			"External MIDI- MIDI 1",
	\midiPortName,			nil, // nil for no MIDI
	// \cardNameIncludes,		"RME",
	\cardNameIncludes,		nil, // for OSX
	// \jackPath,				"/usr/bin/jackd",
	\jackPath,				"/usr/local/bin/jackdmp", // for osx
	\fixAudioInputGoingToTheDecoder, true,
	\hwPeriodSize, 			256,
	\hwPeriodNum,			1,
	\firefaceID,            "000a35009caf3c69",
	\whichMadiInput,        nil,
	\whichMadiOutput,       nil,

	/* file paths */
	\kernelsPath,          	"/Users/admin/Documents/Sound_Labs/SoundLabKernels113/",
	\decoderMatricesPath,	nil
])

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
		[\Hex_2D, \diametric, 'dual', 2, (0..2), 4],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_10ch_6x3x1,	\dome,	'dual', 3,	(0..2)++(6..9), 4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\All, \discrete, \NA, 2, (0..10), 11],			// includes sub as last channel
		[\Hex, \discrete, \NA, 2, (0..5)++[10], 7],		// includes sub as last channel
		[\Quad, \discrete, \NA, 2, [0,1,3,4,10], 5]		// includes sub as last channel
	]
)

// no leading '/' in relative folder path
.put(\kernelSpec, [
	// note popup array size = number of popups
	// column defines selection within one dropdown.
	// [ path relative to \kernelsPath, 	[...check box (true) attributes],	[...dropdown  attributes] ]

	["moderate/m_24_1.365w_18c_5b_0.7t/", 	[],			['moderate','realtime'	] ],
	["moderate/k_24_1.365w_18c_5b_0.7t/", 	[],			['moderate','reference'	] ],
	["moderate/m_24_1.365w_18c_5b_0.7t/", 	['desk'],	['moderate','realtime'	] ],
	["moderate/k_24_1.365w_18c_5b_0.7t/", 	['desk'],	['moderate','reference'	] ],
	["open/m_9_1.365w_18c_3b_0.7t/", 		[],			['open',	'realtime'	] ],
	["open/k_9_1.365w_18c_3b_0.7t/", 		[],			['open',	'reference' ] ],
	["open/m_9_1.365w_18c_3b_0.7t/", 		['desk'],	['open',	'realtime'	] ],
	["open/k_9_1.365w_18c_3b_0.7t/",	 	['desk'],	['open',	'reference'	] ],
	["tight/m_24_1.365w_18c_9b_0.7t/", 		[],			['tight',	'realtime'	] ],
	["tight/k_24_1.365w_18c_9b_0.7t/",	 	[],			['tight',	'reference'	] ],
	["tight/m_24_1.365w_18c_9b_0.7t/",	 	['desk'],	['tight',	'realtime'	] ],
	["tight/k_24_1.365w_18c_9b_0.7t/",	 	['desk'],	['tight',	'reference'	] ],
])

/*
speaker order is assumed to be satellites, subs, stereo (optional)
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	[
		1.323, 1.344, 1.362, 1.352, 1.394, 1.353,
		1.450, 1.469, 1.535,
		1.755,
		1.323, //1.323  //sub
	]
)
.put(\defaultSpkrGainsDB,
	[
		0,0,0,0,0,0,0,0,0,0,
		4.8+3, //0 //sub, rolled off 3dB by ears
	].neg
)
.put(\spkrAzimuthsRad,
	[
		30, 330, 270, 210, 150, 90, //median
		0, 240, 120,                //upper
		0, 							//top
		0, //90 	      			//sub
	].degrad
)
.put(\spkrElevationsRad,
	[
		0,0,0,0,0,0,				//median
		45,45,45,					//upper
		90,							//top
		0, //0						//sub
	].degrad
)
/*
each speaker channel and it's opposing channel, alternating
Array size must equal numDiametricPairsIncludingSubs * 2
*/
.put(\spkrOppDict,
	IdentityDictionary.new(know:true).putPairs([
			// each speaker channel and it's opposing channel, alternating
			0,3,3,0,
			1,4,4,1,
			2,5,5,2,
			6,12, // 12 is a dummy channel
			7,12,
			8,12,
			9,12,
			10,11,
			11,10//eek
		])
)
;