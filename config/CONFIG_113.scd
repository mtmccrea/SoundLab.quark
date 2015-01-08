/* Config for SoundLab */
"CONFIG Loading".postln;

IdentityDictionary(know: true)
.putPairs([
	\labName,				"Sound Lab 113",
	\numHardwareOuts, 		16,
	\numHardwareIns, 		16,
	\numSatChans,			10,
	\numSubChans, 			2, //eek
	\defaultDecoderName,	\Hex,
	\defaultKernel,			\basic_balance,
	\jconvHWOutChannel,		0,
	\stereoChanIndex,		[0, 1],
	\rotateDegree,			90, // option to rotate the listening position to this angle
	\xOverHPF,				80,
	\xOverLPF,				80,
	\shelfFreq,				400,	// takes predecdence over listening Diameter
	\listeningDiameter,		0.5,	// sweet spot size in meters

	/* SoundLabHardware settings */

	\useFireface, 			false,
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
	\kernelsPath,       "/home/audioadmin/Documents/SoundLabSupport/SoundLabKernels",
	\decoderMatricesPath, "/home/audioadmin/Documents/SoundLabSupport/DecoderMatrices/"
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
		1.323,1.323  //sub
	]
)
.put(\defaultSpkrGainsDB,
	[
		0,0,0,0,0,0,0,0,0,0,
		0,0 //sub
	].neg
)
.put(\spkrAzimuthsRad,
	[
		30, 330, 270, 210, 150, 90, //median
		0, 240, 120,                //upper
		0, 							//top
		0,0       					//sub
	].degrad
)
.put(\spkrElevationsRad,
	[
		0,0,0,0,0,0,				//median
		45,45,45,					//upper
		90,							//top
		0,0							//sub
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
			6,11,
			7,11,
			8,11,
			9,11,
			10,11,
			11,10//eek
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
		[\Hex, \diametric, 'dual', 2, (0..2), 4],

		// domes - half of horizontal speakers plus all dome speakers
		[\Dome_10ch_6x3x1,	\dome,	'dual', 3,	(0..2)++(6..9), 4	],

		/* --discrete channel routing-- */
		// thru routing assumes contiguous channels, starting at arrayOutIndices.first
		[\All, \discrete, \NA, 2, (0..9), 10]
	]
)
;