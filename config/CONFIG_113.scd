/* Config for SoundLab */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\labName,				"Sound Lab 113",
	\numHardwareOuts, 		16,
	\numHardwareIns, 		16,
	\numSatChans,			10,
	\numSubChans, 			1,
	\defaultDecoderName,	\Hex,
	\jconvHWOutChannel,		0,
	\stereoChanIndex,		[0, 1],
	\rotateDegree,			90, // option to rotate the listening position to this angle
	\xOverHPF,				60,
	\xOverLPF,				60,
	\shelfFreq,				700,	// ~0.17m sweet spot diam, takes precedence over listening Diameter
	\listeningDiameter,		0.17,	// sweet spot size in meters
	\stereoGain,			-3,		// gain on stereo bus in dB, to balance with decoders
	\initSampleRate,        48000,  //used at initialization

	/* SoundLabHardware settings */

	\useFireface, 			false,
	\midiDeviceName,		"HDSPe MADI (23531001)",
	\midiPortName,			"Port 1",
	// \midiPortName,			nil, // nil for no MIDI
	\cardNameIncludes,		"RME",
	// \cardNameIncludes,		nil, // for OSX
	// \jackPath,				"/usr/bin/jackd",
	\jackPath,				"/usr/local/bin/jackdmp", // for osx
	\jconvolverPath,        "/usr/local/bin/jconvolver", // for osx
	\fixAudioInputGoingToTheDecoder, true,
	\hwPeriodSize, 			256,
	\hwPeriodNum,			1,
	\firefaceID,            "000a35009caf3c69",
	\whichMadiInput,        nil,
	\whichMadiOutput,       nil,

	/* file paths */
	\kernelsPath,       "~/Documents/SoundLabSupport/SoundLabKernels113/".standardizePath,
	\decoderMatricesPath, nil
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
		[\Hex_2D, \diametric, 'dual', 2, (0..2), 4, 6],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_10ch_6x3x1,	\dome,	'dual', 3,	(0..2)++(6..9), 4, 6 ],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		// includes sub as last channel
		[\All,  \discrete, \NA, 2, (0..10),		11, 0],
		[\Hex,  \discrete, \NA, 2, (0..5)++[10], 7, 0],
		[\Quad, \discrete, \NA, 2, [0,1,3,4,10], 5, 0],
		// counter-clockwise
		[\Hex_ccw,  \discrete, \NA, 2, (0..5).reverse.rotate++[10], 7, 0],
		[\Quad_ccw, \discrete, \NA, 2, [0,4,3,1,10], 5, 0],
	]
)

// no leading '/' in relative folder path, but DO include trailing '/'
.put(\kernelSpec,
	// note popup array size = number of popups
	// column defines selection within one dropdown.
	// [ path relative to \kernelsPath, 	[...check box (true) attributes],	[...dropdown  attributes] ]
[
		["open/k_9_1.365w_18c_3b_0.37t/", 		[], ['open','reference'	] ],
		["open/m_9_1.365w_18c_3b_0.37t/", 		[], ['open','realtime'	] ],

		["moderate/k_24_1.365w_18c_5b_0.37t/", 	[], ['moderate','reference'	] ],
		["moderate/m_24_1.365w_18c_5b_0.37t/", 	[], ['moderate','realtime'	] ],
])

/*
speaker order is assumed to be satellites, subs, stereo (optional)
Array size must equal \numSatChans + \numSubChans
see prLoadSynthDefs for how channel mappings are used
*/
.put(\defaultSpkrDistances,
	[
		// 1.323, 1.344, 1.362, 1.352, 1.394, 1.353,
		// 1.450, 1.469, 1.535,
		// 1.755,
		// 1.323, //1.323  //sub

		// DRC-measured
		1.144934062500,
		1.162657500000,
		1.261908750000,
		1.272542812500,
		1.283176875000,
		1.230006562500,
		1.198104375000,
		1.297355625000,
		1.325713125000,
		1.513581562500,
		0 // sub was a negative distance, zeroed out
	]
)
.put(\defaultSpkrGainsDB,
	[
		// 0,0,0,0,0,0,0,0,0,0,
		// -4.8-3, //0 //sub, rolled off 3dB by ears

		// DRC-measured
		-2.818483878592,
		-3.044959239167,
		-2.900778895784,
		-1.688320874699,
		-2.123619772538,
		-2.269392052797,
		-2.027991432347,
		-0.855174420130,
		-0.916518816742,
		0.000000000000,
		-4.287137751041
	]
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