SoundLabGUI {
	// copyArgs
	var <sl, <wwwPort;
	var <slhw, <deviceAddr, <wsGUI; //<listeners
	var <decsHoriz, <decsSphere, <decsDome, <discreteRouters;//<decoders,
	var <sampleRates, <kernels, <stereoPending, <rotatePending;
	var <gainTxt, <gainSl, <muteButton, <attButton, <srMenu, <decMenus, <horizMenu, <sphereMenu, <domeMenu, <discreteMenu, <stereoMenu, <rotateMenu, <correctionMenu, <applyButton, <stateTxt, <postTxt;
	var <curDecType, <curSR, <curKernel, <pendingDecType, <pendingInput, <pendingSR, <pendingKernel;
	// var numCols, butheight, vpad, hpad, h1, h2, h3,h4, buth, butw, inLabel;
	var <ampSpec, <oscFuncDict, buildList;
	var <>maxPostLines;

	*new { |soundLabModel, webInterfacePort = 8080| //don't change web port unless you know how to change redirection in Apache web server
		^super.newCopyArgs(soundLabModel, webInterfacePort).init;
	}

	init {
		var cond;
		cond = Condition(false);
		fork {
			"initializing slgui".postln;
			sl.addDependant( this );
			if( sl.usingSLHW, {
				slhw = sl.slhw.addDependant(this)
			});

			" ---------- \n creating new instance of interfaceJS \n------------".postln;
			wsGUI = wsGUI.new(wwwPort);

			// decoders = [];
			// sl.decAttributeList.do({ |attset|
			// 	if(decoders.includes(attset[0]).not,
			// 	{decoders = decoders.add(attset[0])})
			// });

			decsHoriz = decsSphere = decsDome = discreteRouters = [];
			sl.decAttributeList.do{ |dAtts|
				if( dAtts[1] == \dicrete, {
					discreteRouters = discreteRouters.add(dAtts.first)
					},{
						switch( dAtts[3], // numDimensions
							2, { decsHoriz = decsHoriz.add(dAtts.first)},
							3, { if(dAtts[1] == \dome,
								{ decsDome = decsDome.add(dAtts.first)},
								{ decsSphere = decsSphere.add(dAtts.first)}
								)
							}
						)
				});
			};

			kernels = sl.compDict.delays.keys.select({ |name|
				name.asString.contains(sl.sampleRate.asString)
			});
			// reformat to exclude SR
			kernels = kernels.collect{|key|
				var modkey;
				modkey = key.asString;
				modkey = modkey.replace("_44100","");
				modkey = modkey.replace("_48000","");
				modkey = modkey.replace("_96000","");
				modkey.asSymbol;
			};
			kernels = [\basic_balance] ++ kernels.asArray;

			ampSpec = ControlSpec.new(-80, 12, -2, default: 0);
			sampleRates = [44100, 48000, 96000];
			maxPostLines = 12;
			this.initVars(cond);
			cond.wait;

			// numCols = 4;
			// vpad = 1/64;
			// hpad = 1/24;
			// // height of the 4 sections of the layout
			// h1 = 4/32;
			// h2 = 20/32;
			// h3 = 4/32;
			// h4 = 4/32;

			interfaceJS.clear;	// clear everything, just in case
			0.5.wait; 			// see if it helps
			interfaceJS.background_(nil, Color.rand(0, 0.2)); // set page background

			this.buildListeners;
			this.buildControls;	// changed order - build Listeners defines functions
		}
	}

	initControls {
		// GAIN
		gainTxt = WsStaticText.init(wsGUI)
		.string_("Digital Gain: ")
		;
		gainSl = WsEZSlider.init(wsGUI)
		.controlSpec_(ampSpec) //only in EZSlider;
		.action_({|sl|
			sl.amp_( sl.value );
			postf("slider value: %\n", sl.value)}); // debug
		;
		// MUTE / ATTENUATE
		muteButton = WsButton.init(wsGUI)
		.states_([
			["Mute", Color.black, Color.gray],
			["Muted", Color.white, Color.red]
		]);
		.action_({ |but|
			switch( but.value,
				0, {sl.mute(false)}, 1, {sl.mute(true)}
			)
		})
		;
		muteButton = WsButton.init(wsGUI)
		.states_([
			["Att", Color.black, Color.gray],
			["Att'd", Color.white, Color.magenta]
		]);
		.action_({ |but|
			switch( but.value,
				0, {sl.attenuate(false)}, 1, {sl.attenuate(true)}
			)
		})
		;
		// SAMPLE RATE
		srMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-']++ sampleRates.collect(_.aSymbol))
		.action_({ |mn|
			"menu val: ".post; mn.value.postln; "item: ".post;
			(mn.value==0).if(
				{pendingSR = nil},{
					(mn.item == curSR.asSymbol).if(
						{	this.status_("Selected sample rate is already " ++ curSR.asSymbol);
							mn.valueAction_(0); // set back to '-'
						},
						{	pendingSR = mn.item.asInt; }
					)
			})
		})
		;
		// DECODER
		decMenus = Dictionary();

		(decsHoriz.size > 0).if{ decMenus.put( \horiz,
			horizMenu = WsPopUpMenu.init(wsGUI)
			.items_(['-'] ++ decsHoriz)
			.action_({|mn|
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
				this.clearDecSelections(\horiz);
				discreteMenu.valueAction_(0);
			})
			)
		};
		(decsSphere.size > 0).if{ decMenus.put( \sphere,
			sphereMenu = WsPopUpMenu.init(wsGUI)
			.items_(['-'] ++ decsSphere)
			.action_({|mn|
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
				this.clearDecSelections(\sphere);
				discreteMenu.valueAction_(0);
			})
			)
		};
		(decsDome.size > 0).if{ decMenus.put( \dome,
			domeMenu = WsPopUpMenu.init(wsGUI)
			.items_(['-'] ++ decsDome)
			.action_({|mn|
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
				this.clearDecSelections(\dome);
				discreteMenu.valueAction_(0);
			})
			)
		};

		// DISCRETE ROUTING
		discreteMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-'] ++ discreteRouters)
		.action_({|mn|
			pendingDecType = if(mn.item != '-', {mn.item},{nil});
			this.clearDecSelections();
		})
		;
		// STEREO / ROTATE
		stereoMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-','yes','no'])
		.action_({|mn|
			stereoPending = switch(mn.item,
				'-',{nil},'yes',{\on},'no',{\off}
			)
		})
		;
		rotateMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-','yes','no'])
		.action_({|mn|
			rotatePending = switch(mn.item,
				'-',{nil},'yes',{\on},'no',{\off}
			)
		})
		;
		// CORRECTION
		correctionMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-'] ++ kernels)
		.action_({|mn|
			pendingKernel = if(mn.item != '-', {mn.item},{nil});
			// TODO check if requesting new SR if selected correction is available
			// make the switch but post warning that correction change not made
		})
		;

		// APPLY
		applyButton = WsSimpleButton.init(wsGUI)
		.action_({
			fork {
				block { |break|
					var updateCond;
					if( sl.usingSLHW,
						{
							if( slhw.audioIsRunning.not, {
								this.status_("Warning: Audio is stopped in Hardware");
								break.("Audio is currently stopped in the Hardware.".warn)
							});
						},{
							if( sl.server.serverRunning.not, {
								this.status_("Warning: Server is stopped");
								break.("Server is currently stopped.".warn)
							});
						}
					);
					// Anything to update?
					if( pendingDecType.isNil 	and:
						pendingSR.isNil			and:
						pendingKernel.isNil		and:
						stereoPending.isNil		and:
						rotatePending.isNil,
						{this.status_("No updates."); break.()}
					);

					// TODO check here if there's a SR change, if so set state current vars
					// of soundlab then change sample rate straight away,
					// no need to update signal chain twice

					this.status_( "Updating..." );
					updateCond = Condition(false);

					/* Update Decoder/Kernel */
					if( pendingDecType.notNil 	or:
						pendingKernel.notNil	or:
						pendingSR.notNil,
						{
							pendingDecType = pendingDecType ?? curDecType;
							this.status_(("Updating decoder to "++pendingDecType).postln);

							if( pendingKernel.notNil,
								{ sl.startNewSignalChain(pendingDecType, pendingKernel, updateCond) },
								{ sl.startNewSignalChain(pendingDecType, completeCondition: updateCond) }
							);
						},{ updateCond.test_(true).signal }
					);
					updateCond.wait; // wait for new signal chain to play

					/* Update Stereo routing */
					stereoPending !? {
						switch( stereoPending,
							\on,	{sl.stereoRouting_(true)},
							\off,	{sl.stereoRouting_(false)}
						)
					};
					/* Update listening position rotation */
					rotatePending !? {
						switch( rotatePending,
							\on, {
								if( sl.curDecoderPatch.attributes.kind == \discrete, {
									this.status_("Rotation not available for discrete routing".warn);
									rotateMenu.valueAction_(0);
									},{ sl.rotate_(true) }
								)
							},
							\off, { sl.rotate_(false) }
						)
					};
					/* Update Samplerate */
					if( pendingSR.notNil,
						{ sl.sampleRate_( pendingSR ) },
						{ this.status_("... Update COMPLETE.") }
					);
				}
			}
		})
		;
		// STATE
		stateTxt = WsStaticText.init(wsGUI)
		.string_( "SOUND LAB STATE")
		;
		// POST WINDOW
		postTxt = WsStaticText.init(wsGUI)
		.string_( "Post")
	}

	// for updating the GUI when SoundLab model is changed
	// this is impportant to see feedback as to the state of decoder communication
	update {
		| who, what ... args |
		if( who == sl, {
			switch ( what,
				\amp,	{ var val;
					val  = args[0].ampdb;
					gainSl.value_(val);
					gainTxt.string_(format("Digital Gain: % dB",val.asString));
				},
				\attenuate,	{
					switch ( args[0],
						0, {
							attButton.value_(0);
							if( sl.isMuted.not, {
								this.status_("Amp restored.") },{ this.status_("Muted.")
							});
						},
						1, {
							attButton.value_(1);
							if( sl.isMuted.not,
								{ this.status_("Attenuated.") },
								{ this.status_("Muted, attenuated.") }
							);
						}
					)
				},
				\mute,	{
					switch ( args[0],
						0, {
							muteButton.value_(0);
							if( sl.isAttenuated.not,
								{ this.status_("Amp restored.") },
								{ this.status_("Attenuated.") }
							);
						},
						1, {
							muteButton.value_(1);
							if( sl.isAttenuated.not,
								{ this.status_("Muted.") },
								{ this.status_("Muted, attenuated.") }
							);
						}
					)
				},
				\clipped,	{
					this.status_( (args[0] < sl.numHardwareIns).if(
						{ "Clipped IN " ++ args[0].asString },
						{ "Clipped OUT " ++ (args[0]-sl.numHardwareIns).asString }
					));
				},
				\decoder,	{
					curDecType = args[0].decoderName; // args[0] is the decoderpatch
					this.clearDecSelections;
					this.status_("Now decoding with: " ++ curDecType);
				},
				\stereo,	{
					this.status_( args[0].if(
						{"Stereo added to first two output channels."},{"Stereo cleared."}
					));
					stereoMenu.valueAction_(0);
				},
				\rotate,	{
					var rotated;
					rotated = args[0];
					this.status_( rotated.if(
						{"Soundfield rotated."},{"Rotation cleared."}
					));
					rotateMenu.valueAction_(0);
				},
				\kernel,	{
					var k_name;
					k_name = args[0];
					curKernel = k_name !? {k_name.asSymbol};
					correctionMenu.valueAction_(0);
					this.status_("Kernel updated: " ++ curKernel);
				},
				\stateLoaded,	{
					this.recallValues;
				},
				\reportError,	{ this.status_(args[0]) },
				\reportStatus,	{ this.status_(args[0]) }
			);
			this.postState;
		});
		if( who == slhw, {
			switch( what,
				\audioIsRunning, { args[0].not.if(
					{ this.status_("Audio stopped. Cannot update at this time.") }
					);
				},
				\stoppingAudio, { this.status_("Audio is stopping - Standby.") }
			)
		});
	}

	status_ { |aString|
		var newLines, curLines, numLines;
		// add new status to post "buffer" and update the status text box
		curLines = postString.split($\n);
		newLines = ( Date.getDate.format("%a %m/%d %I:%M:%S\t") ++ aString).split($\n);
		if((curLines.size + newLines.size) > maxPostLines, {
			var stripNum;
			stripNum = (curLines.size + newLines.size) - maxPostLines;
			curLines = curLines.drop(stripNum);
		});
		newPost = "";
		curLines.do{ |line| newPost = newPost ++ line ++ "\n" };
		newLines.do{ |line| newPost = newPost ++ line ++ "\n"};

		postTxt.string_(newPost);
		postString = newPost;
	}

	postState {
		// post info to the soundlab state text box:
		// sample rate, decoder, correction, stereo, rotated
		stateTxt.string_(
			"SOUND LAB STATE " ++
			"\nSample Rate: " ++ curSR ++
			"\nDecoder / Router setting: " ++ curDecType ++
			"\nCorrection: " ++ curKernel ++
			"\nStereo channels first: " ++ sl.stereoActive ++
			"\nSound field rotated: " ++ sl.rotated
		);
	}

	clearDecSelections {|exceptThisKey|
		decMenus.keysValuesDo{|k,v| if(k!=exceptThisKey,{v.valueAction_(0)}) };
		pendingDecType = nil;
	}

		buildControls {
	// do page layout
		wsGUI.layout_(
			WsVLayout( nil,
				WsStaticText.init(wsGUI, Rect(0,0,1,0.1)).string_(
					format("Sound Lab %\nRouting and Decoding System",sl.configFileName))
				WsHLayout( nil,
					// COLUMN 1
					WsVLayout( Rect(0,0,0.4,1),
						// gain
						gainTxt,
						gainSl,
						WsHLayout(nil, muteButton, nil, attButton, nil, nil),
						nil,
						// decoder/rotation
						WsStaticText.init(wsGUI).string_("Select an Ambisonic DECODER"),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("2-D Horizontal Only"),
							horizMenu ),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("3-D Sphere"),
							sphereMenu ),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("3-D Dome"),
							domeMenu ),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_(
								format("Rotate the listening position % degrees?",sl.rotateDegree)),
							rotateMenu ),
						nil,
						// discrete routing
						WsStaticText.init(wsGUI).string_("Select a Discrete Routing Layout"),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("Which speakers?"),
							discreteMenu ),
						nil,
						// stereo
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("Insert stereo channels before decoder/router?"),
							stereoMenu ),
						nil,
						// correction
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_("Room correction"),
							correctionMenu ),
						nil,
						nil,
						applyButton
					),
					// COLUMN 2
					WsVLayout( Rect(0,0,0.2,1),
						// picture
					),
					// COLUMN 3
					WsVLayout( Rect(0,0,0.4,1),
						WsStaticText.init(wsGUI).string_("Current System Settings:"),
						stateTxt,
						0.05,
						WsStaticText.init(wsGUI).string_("Post:")
						postTxt
					)
				)
			);

			this.recallValues; /* this will turn on the defaults */
	}

	initVars { |loadCondition|
		pendingDecType = nil;
		pendingInput = nil;
		pendingSR = nil;
		// defaults on startup - pulled from SoundLab state
		curDecType = sl.curDecoderPatch.decoderName; // not the same as synthdef name
		curSR = (\SR ++ sl.sampleRate).asSymbol;
		curKernel = sl.curKernel ?? {\basic_balance};
		stereoPending = nil;
		rotatePending = nil;

		"variables initialized".postln;
		postf("curDecType: %, curSR: %, curKernel: %\n",
			curDecType, curSR, curKernel);
			loadCondition !? {loadCondition.test_(true).signal}
	}

	recallValues {
		var cond;
		fork {
			cond = Condition(false);
			this.initVars(cond);
			cond.wait;

			// TODO: post current settings to state window

			gainSl.value_(sl.globalAmp.ampdb);
			gainTxt.value_(sl.globalAmp.ampdb);
			muteButton.value_( if(sl.isMuted, {1},{0}) );
			attButton.value_( if(sl.isAttenuated, {1},{0}) );

			this.postState;
		}
	}

	cleanup {
		sl.removeDependant( this );
		slhw !? {slhw.removeDependant(this)};
		wsGUI.free;
		// listeners.do(_.free);
	}

}
	// setCtlPending { |which|
	// 	this.setColor(which, \yellow);
	// 	this.status((which ++ " is pending.").postln);
	// }
	//
	// setCtlActive { |which|
	// 	this.setColor(which,
	// 		if( which == \Mute, {\red},
	// 			{ if( which == \Attenuate, {\purple},
	// 				{\green} /* otherwise, all else are green when active */
	// 			)}
	// 		);
	// 	);
	// 	interfaceJS.value_( which, 1);
	// }
	//
	// clearControls { |controlArr, current, selected|
	// 	controlArr.do({ |name|
	// 		if( ((name != current) && (name != selected)), {
	// 			("turning off: " ++ name).postln;
	// 			interfaceJS.value_( name.asSymbol, 0);
	// 			this.setColor( name, \default);
	// 		});
	// 	})
	// }
	//
	// // TODO Update this
	// setThruControls { |whichThruCtl|
	// 	// update decoder controls
	// 	pendingDecType = whichThruCtl;
	// 	this.clearControls( decoders, curDecType, whichThruCtl );
	// 	this.setCtlPending( whichThruCtl );
	// 	this.status( "Thru: direct outs, no BF decoder." );
	// }
	//
	// setColor { |controlName, color|
	// 	switch( color,
	// 		\yellow, {
	// 			interfaceJS.backgroundFillStroke_(controlName,
	// 				Color.fromHexString("#000000"),
	// 				Color.fromHexString("#FFCC00"),
	// 		Color.white)},
	// 		\purple, {
	// 			interfaceJS.backgroundFillStroke_(controlName,
	// 				Color.fromHexString("#000000"),
	// 				Color.fromHexString("#CC0099"),
	// 		Color.white)},
	// 		\red, {
	// 			interfaceJS.backgroundFillStroke_(controlName,
	// 				Color.fromHexString("#000000"),
	// 				Color.fromHexString("#FF0033"),
	// 		Color.white)},
	// 		\green, {
	// 			interfaceJS.backgroundFillStroke_(controlName,
	// 				Color.fromHexString("#000000"),
	// 				Color.fromHexString("#66FF00"),
	// 		Color.white)},
	// 		\default, {
	// 			interfaceJS.backgroundFillStroke_(controlName,
	// 				Color.fromHexString("#000000"),
	// 				Color.fromHexString("#aaaaaa"),
	// 		Color.white)}
	// 	)
	// }
	/* buildListeners {
		// building individual responders for specific widgets
		oscFuncDict = IdentityDictionary( know: true ).put(
			\ampSlider, { |val|
				sl.amp_( val );
		}).put(
			\Attenuate, { |val|
				case
				{val == 0} {sl.attenuate(false)}
				{val == 1} {sl.attenuate(true)}
				;
		}).put(
			\Mute, { |val|
				case
				{val == 0} {sl.mute(false)}
				{val == 1} {sl.mute(true)}
				;
		}).put(
			\Stereo, { |val|
				{ case
					{val == 0} {
						if(sl.stereoActive,
							{
								stereoPending = \off;
								this.setColor(\Stereo, \yellow);
								interfaceJS.value_(\Stereo, 1);
							},{
								stereoPending = nil;
								this.setColor(\Stereo, \default);
							}
						)
					}
					{val == 1} {
						stereoPending = \on;
						this.setColor(\Stereo, \yellow);
					}
				}.fork
				;
		}).put(
			\Rotate, { |val|
				{ case
					{val == 0} {
						if(sl.rotated,
							{
								rotatePending = \off;
								this.setColor(\Rotate, \yellow);
								interfaceJS.value_(\Rotate, 1);
							},{
								rotatePending = nil;
								this.setColor(\Rotate, \default);
							}
						)
					}
					{val == 1} {
						rotatePending = \on;
						this.setColor(\Rotate, \yellow);
					}
				}.fork
				;
		}).put(
			\Update, { |val|
				fork {
					block { |break|
						var updateCond;
						if( sl.usingSLHW,
							{
								if( slhw.audioIsRunning.not, {
									this.status("Warning: Audio is stopped in Hardware");
									break.("Audio is currently stopped in the Hardware.".warn)
								});
							},{
								if( sl.server.serverRunning.not, {
									this.status("Warning: Server is stopped");
									break.("Server is currently stopped.".warn)
								});
							}
						);
						// Anything to update?
						if( pendingDecType.isNil 	and:
							pendingSR.isNil			and:
							pendingKernel.isNil		and:
							stereoPending.isNil		and:
							rotatePending.isNil,
							{this.status("No updates."); interfaceJS.value_( \Update, 0); break.()}
						);

						// TODO check here if there's a SR change, if so set state current vars
						// of soundlab then change sample rate straight away,
						// no need to update signal chain twice

						this.setColor( \Update, \yellow );
						updateCond = Condition(false);

						/* Update Decoder/Kernel */
						if( pendingDecType.notNil 	or:
							pendingKernel.notNil	or:
							pendingSR.notNil,
							{
								pendingDecType = pendingDecType ?? curDecType;
								this.status(("Updating decoder to "++pendingDecType).postln);

								if( pendingKernel.notNil,
									{ sl.startNewSignalChain(pendingDecType, pendingKernel, updateCond) },
									{ sl.startNewSignalChain(pendingDecType, completeCondition: updateCond) }
								);
							},{ updateCond.test_(true).signal }
						);

						updateCond.wait; // wait for new signal chain to play

						/* Update Stereo routing */
						stereoPending !? {
							switch( stereoPending,
								\on, {sl.stereoRouting_(true)},
								\off, {sl.stereoRouting_(false)}
							)
						};

						/* Update listening position rotation */
						rotatePending !? {
							switch( rotatePending,
								\on, {
									if(sl.curDecoderPatch.attributes.kind == \discrete, {
										this.status("Rotation not available for discrete routing".warn);
										interfaceJS.value_( \Rotate, 0);
									},{ sl.rotate_(true) }
									)
								},
								\off, {
									sl.rotate_(false)
								}
							)
						};

						/* Update Samplerate */
						if( pendingSR.notNil, {
							sl.sampleRate_( pendingSR.asString.drop(2).asInt )
							},{
								interfaceJS.value_( \Update, 0);
								this.setColor( \Update, \default );
							}
						);
					}
				}
		});


		// // collect listeners built above
		// oscFuncDict.keysValuesDo({ |tag,func|
		// listeners = listeners.add( OSCFunc(func, tag, nil) )
		// });

		// buildList order defines indexing in switch statements below
		buildList = [decoders, sampleRates, kernels];
		buildList.do({ |controls, i|
			controls !? {	// sometimes kernels is nil if not used
				controls.do({
					|selected|
					oscFuncDict.put(selected, {
						|val|
						var current, pending;
						// get the current and pending settings for the selected category
						current = switch( i,
							0, {curDecType},
							1, {curSR},
							2, {curKernel}
						);
						pending = switch( i,
							0, {pendingDecType},
							1, {pendingSR},
							2, {pendingKernel}
						);

						postf("in the selected category, current control is: %, pending control is: %\n", current, pending);

						case
						// button ON
						{val == 1} {
							(selected != current).if(
								{
									block { |break|
										if( "(T|t)hru".matchRegexp(selected.asString),
											{	/*debug*/"selected is a thru decoder, setting thru controls".postln;
												this.setThruControls(selected);
												break.()
											}
										);
										switch( i,
											0, {pendingDecType = selected},
											1, {pendingSR = selected},
											2, {
												// kernels only avail at 48000
												if((
													(pendingSR == \SR48000) or:
													((curSR == \SR48000) and: (pendingSR == nil))
													),{
														"\nsetting pending kernel normally\n".postln;
														pendingKernel = selected;
													},{
														this.status("cancelling pending kernel - not available at selected samplerate".postln);
														pendingKernel = nil;
														this.clearControls( controls, current, nil );
														break.();
													}
												)
											}
										);
										this.clearControls( controls, current, selected );
										this.setCtlPending( selected );
									} // end of blocking function
								},{
									this.setColor(selected, \green);
									this.status((selected ++ " is current.").postln);
							})
						}
						// button OFF
						{val == 0} {
							case
							{ selected == pending } {
								if( "(T|t)hru".matchRegexp(pendingDecType.asString) && (i==1),
									{   // turn it back on if Thru routing chosen
										this.setCtlPending(\NA);
										interfaceJS.value_(\NA, 1);
									},{ // otherwhise turn it off
										switch( i,
											0, {pendingDecType = nil},
											1, {pendingSR = nil},
											2, {pendingKernel = nil}
										);
										this.setColor(selected, \default);
										this.status((selected ++ " removed.").postln);
									}
								)
							}
							{ selected == current } {
								this.setCtlActive(selected);
								this.status((selected ++ " is current.").postln);
							}
						}
						}
					)
				})
			}
		});
		"oscFuncDict in the end: ".post; oscFuncDict.postln;
	}*/
/*	buildControls {
		var catCntlArr, ampCtlW, rowSize, cntrlDict;
		var screenW, screenH, catVPad, catHPad, cntrlHPad, cntrlVPad, catLabelH;
		var ncols, ncntrls_row, colW, cntrlW, cntrlH , here, x_home;

		rowSize = 3;
		cntrlDict = IdentityDictionary.new(know: true);

		// this order determines order of control layout
		catCntlArr = [
			/*
			\HARDWARE,
			IdentityDictionary.new(know: true) .putPairs([
			\kind, \label,
			\controls, [\Connections],
			\cSize, 9 //coerce this control to be 9 buttons large
			]),
			*/
			\SAMPLERATE,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, sampleRates
			]),
			\DECODER,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, decoders
			]),
			'STEREO & ROTATION',
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, [\Stereo, \Rotate]
			]),
			// \ROTATION,
			// IdentityDictionary.new(know: true).putPairs([
			// 	\kind, \button,
			// 	\controls, [\Rotate]
			// ]),
			\SETTINGS,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, [\Matrix, \Reload],
				\cSize, 2
			]),
			\CORRECTION,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, kernels
			]),
			'UPDATE_Configuration',
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, [\Update],
				\cSize, 4
			]),
			\STATUS, // status "post" window
			IdentityDictionary.new(know: true).putPairs([
				\kind, \label,
				\controls, [\Status],
				\cSize, 4
			]),
		];
		cntrlDict.putPairs(catCntlArr);

		// screen dimensions are normalized to height and width of 1.0
		screenW = 0.85;		// not 1.0 ... leave room for volume controls
		screenH = 1.0;
		catVPad = 0.035;		// vertical pad between categories, i.e. K, Stereo, etc
		catHPad = 0.04;
		cntrlHPad = 0.012;	// vertical pad between controls (buttons)
		cntrlVPad = 0.02;
		ncols = 2.5;		// number of columns of controls (excluding amp control)
		ncntrls_row = 2;	// number of controls per row
		colW = (screenW - catHPad)/ncols - catHPad;
		cntrlW = (colW / ncntrls_row) - ((ncntrls_row-1)*cntrlHPad/ncntrls_row);
		cntrlH = 0.045;
		catLabelH = cntrlH * 1.0;

		x_home = catHPad;
		here = Point(x_home,catVPad);	// starting position of layout


		catCntlArr.select{|item, i| i.even}.do({ |category_name|
			var cntrl_set, cat_height, nrows, col_cnt, w, h;
			cntrl_set = cntrlDict[category_name];

			("\n"++category_name++" control set").postln; //cntrl_set.keysValuesDo{|k,v|"\t".post; [k,v].postln};// debug

			nrows = cntrl_set.cSize.notNil.if(
				{(cntrl_set.cSize / ncntrls_row) * (cntrl_set.controls.size)},
				{cntrl_set.controls.size / ncntrls_row}
			).ceil;

			// check vertical height of this category of controls
			cat_height = (
				catLabelH +				// category label height
				(nrows * cntrlH)		// control rows height
				+ (nrows * cntrlVPad);  // padding btwn control rows
			);
			if( (cat_height+here.y) > screenH,{
				here.x = here.x+colW+catHPad;
				x_home = here.x;
				here.y = catVPad;
			});

			/* category label */
			// ("making cat label "++category_name++" at: " ++ [here.x, here.y, colW, catLabelH]).postln; // debug
			interfaceJS.makeLabel( category_name, Rect(here.x, here.y, colW, catLabelH), size: 14, align: "left");
			here.y = here.y + catLabelH; // advance place holder

			// "making controls".postln; // debug
			// check for controls with a size attribute
			if(cntrl_set.cSize.notNil,
				{
					w = if(cntrl_set.cSize > ncntrls_row,
						{colW},
						{colW * (cntrl_set.cSize / ncntrls_row)}
					);
					h = (cntrlH * (nrows/cntrl_set.controls.size)); // + (cntrlVPad*(nrows-1));
				},{
					w = cntrlW;
					h = cntrlH;
			});

			cntrl_set.controls.do({ |cntrl, i|
				var row_cnt;
				if(cntrl_set.cSize.notNil,
					{
						row_cnt = (i * cntrl_set.cSize / ncntrls_row).floor;
						col_cnt = (i * cntrl_set.cSize) % ncntrls_row;
					},{
						row_cnt = (i / ncntrls_row).floor;
						col_cnt = i % ncntrls_row;
					}
				);

				// cntrl.postln; // debug
				// ("row/column:\t" ++ [row_cnt,col_cnt]).postln; // debug

/*				if((cntrl_set.controls.size == 1) and: (cntrl_set.cSize.notNil),
					{ "setting special x".postln;
						here.x = x_home + ((cntrlHPad+w)/2) }, // center controls with just 1 value
					{ here.x = x_home + ((col_cnt)*(cntrlHPad+w)) }
				);*/
				here.x = x_home + ((col_cnt)*(cntrlHPad+w));

				if(
					((col_cnt==0) && (row_cnt>0)),
					{ here.y = here.y + (cntrlVPad+h) } // advance y pointer for new row
				);

				switch( cntrl_set.kind,
					\label, {
						// allow labels to be double-wide so no wrapping - temp fix?
						interfaceJS.makeLabel(cntrl, Rect(here.x, here.y, colW, h), align: "left");
					},
					\button, {
						cntrl.class;
						// postf("making button for\t %, function %\n", cntrl, oscFuncDict[cntrl]);
						interfaceJS.makeButton(
							cntrl, Rect(here.x, here.y, w, h),
							function: oscFuncDict[cntrl],
							background: Color.fromHexString("#000000"),
							fill: Color.fromHexString("#aaaaaa"),
							stroke: Color.white,
							value: 0
						);
					}
				);
			});
			here.y = here.y + (cntrlVPad+h) + catVPad;
			here.x = x_home;
		});


		/* amplitude control column */
		ampCtlW = 1 - screenW;

		// 'Amp' label
		interfaceJS.makeLabel( \AMP,
			Rect(screenW, catVPad, ampCtlW, cntrlH)
		);
		// amp level label
		interfaceJS.makeLabel( \ampLevelLabel,
			Rect(
				screenW,
				catVPad + cntrlH + cntrlVPad,
				ampCtlW,
				cntrlH
			),
			labeltext: '0.0'
		);
		/*			// clip meters (buttons)
		[\I, \O].do({ |name, i|
		interfaceJS.makeButton( name,
		screenW + catHPad + (ampCtlW*i/2),
		2* (cntrlH - cntrlVPad),
		cntrlH - cntrlVPad,
		ampCtlW / 2,
		'#FF0033'
		)
		});
		*/
		// Amp slider
		interfaceJS.makeSlider( \ampSlider, // |name, xpos, ypos, height, width, vert = true|
			Rect(
				screenW, //xpos
				0.16, //ypos
				ampCtlW * 0.85, // width
				0.6 //height
			),
			controlSpec: ampSpec,
			function: oscFuncDict[\ampSlider]
		);
		// mute and attenuate button
		[\Attenuate, \Mute].do({ |name, i|
			interfaceJS.makeButton(name,
				Rect(
					screenW, //xpos
					0.76 + (i * (cntrlH *2) + cntrlVPad),
					ampCtlW * 0.85, // width
					cntrlH * 2
				),
				Color.fromHexString("#000000"),
				Color.fromHexString("#aaaaaa"),
				Color.white,
				value: 0,
				function: oscFuncDict[name]
			)
		});

		this.recallValues; /* this will turn on the defaults */
	}*/

/* TESTING
InterfaceJS.nodePath = "/usr/local/bin/node"
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false)
l.cleanup
l.curKernel
l.kernelDict
l.kernelDict.keys
l.usingKernels
l.useKernel_(true)
l.gui

l.gui.pendingKernel
l.gui.curSR== \SR48000

l.gui.curSR
l.sampleRate

InterfaceJS.killNode //class method - kill all processes called node

l.gui.interfaceJS.reloadPage
l.cleanup
*/