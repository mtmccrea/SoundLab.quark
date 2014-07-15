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
	var <>maxPostLines, postString;

	*new { |soundLabModel, webInterfacePort = 8000| //don't change web port unless you know how to change redirection in Apache web server
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

			" ---------- \n creating new instance of wsGUI \n------------".postln;
			wsGUI = WsGUI.new(wwwPort);
			1.5.wait; // TODO: add condition

			// decoders = [];
			// sl.decAttributeList.do({ |attset|
			// 	if(decoders.includes(attset[0]).not,
			// 	{decoders = decoders.add(attset[0])})
			// });

			decsHoriz = [];
			decsSphere = [];
			decsDome = [];
			discreteRouters = [];

			sl.decAttributeList.do{ |dAtts|
				dAtts.postln;
				if( dAtts[1] == \discrete, {
					discreteRouters = discreteRouters.add(dAtts.first);
					"adding discrete routing".postln;
					discreteRouters.postln;
					},{
						switch( dAtts[3], // numDimensions
							2, { decsHoriz = decsHoriz.add(dAtts.first);
								"adding horiz routing".postln;
								decsHoriz.postln;
							},
							3, { if(dAtts[1] == \dome,
								{ decsDome = decsDome.add(dAtts.first);
									"adding dome routing".postln;
									decsDome.postln;
								},
								{ decsSphere = decsSphere.add(dAtts.first);
									"adding sphere routing".postln;
									decsSphere.postln;
								}
								)
							}
						)
				});
			};

			postf(
				"decsHoriz = %\n
				decsSphere = %\n
				decsDome = %\n
				discreteRouters = %\n",
				decsHoriz, decsSphere, decsDome, discreteRouters);

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
			maxPostLines = 9;
			postString = "";
			this.initVars(cond);
			cond.wait;

			this.initControls;
			this.buildControls;	// changed order - build Listeners defines functions
		}
	}

	initControls {
		"initializing controls".postln;
		// GAIN
		gainTxt = WsStaticText.init(wsGUI, Rect(0,0,1,0.05)).string_("")
		;
		gainSl = WsEZSlider.init(wsGUI)
		.controlSpec_(ampSpec) //only in EZSlider;
		.action_({|sldr|
			sl.amp_( sldr.value );
			postf("slider value: %\n", sl.value)}) // debug
		;
		// MUTE / ATTENUATE
		muteButton = WsButton.init(wsGUI)
		.states_([
			["Mute", Color.black, Color.gray],
			["Muted", Color.white, Color.red]
		])
		.action_({ |but|
			switch( but.value,
				0, {sl.mute(false)}, 1, {sl.mute(true)}
			)
		})
		;
		attButton = WsButton.init(wsGUI)
		.states_([
			["Attenuate", Color.black, Color.gray],
			["Attenuated", Color.white, Color.magenta]
		])
		.action_({ |but|
			switch( but.value,
				0, {sl.attenuate(false)}, 1, {sl.attenuate(true)}
			)
		})
		;
		// SAMPLE RATE
		srMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-']++ sampleRates.collect(_.asSymbol))
		.action_({ |mn|
			(mn.value==0).if(
				{ pendingSR = nil },
				{
					(mn.item == curSR.asSymbol).if({
						this.status_("Sample rate is already " ++ curSR.asString);
						mn.valueAction_(0); // set back to '-'
						},{	pendingSR = mn.item.asInt }
					)
				}
			)
		})
		;
		// DECODER
		decMenus = Dictionary();

		decMenus.put( \horiz,
			horizMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsHoriz)
		);
		decMenus.put( \sphere,
			sphereMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsSphere)
		);
		decMenus.put( \dome,
			domeMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsDome)
		);

		decMenus.keysValuesDo{|k,v|
			decMenus[k].action_({|mn|
				this.clearDecSelections(k); // sets pendingDecType to nil
				discreteMenu.value_(0);		// reset discrete routing menu
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
				postf("selected: %\npending decType: %\n", mn.item, pendingDecType); //debug
			})
		};

		// DISCRETE ROUTING
		discreteMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-'] ++ discreteRouters)
		.action_({|mn|
			this.clearDecSelections(); // sets pendingDecType to nil
			pendingDecType = if(mn.item != '-', {mn.item},{nil});
			postf("selected: %\npending decType: %\n", mn.item, pendingDecType); //debug
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
		.string_("Apply")
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

					this.status_( "Updating..." );
					updateCond = Condition(false);

					/* Update Decoder/Kernel */
					if( pendingDecType.notNil 	or:
						pendingKernel.notNil	or:
						pendingSR.notNil,
						{
							// TODO check here if there's a SR change, if so set state current vars
							// of soundlab then change sample rate straight away,
							// no need to update signal chain twice

							pendingDecType = pendingDecType ?? curDecType;
							("Updating decoder to "++pendingDecType).postln;

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
						{
							this.status_(format("Changing the sample rate to %.\nAudio will stop for a time while the routing system reboots to the new sample rate.", pendingSR));
							sl.sampleRate_( pendingSR ) },
						{ this.status_("Update complete.") }
					);
				}
			}
		})
		;
		// STATE
		stateTxt = WsStaticText.init(wsGUI).string_("")
		;
		// POST WINDOW
		postTxt = WsStaticText.init(wsGUI).string_("")
		;
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
					gainTxt.string_(format("<strong>Gain: </strong>% dB",val.round(0.01).asString));
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
					this.clearDecSelections;	// sets pending decoder to nil
					discreteMenu.value_(0);		// reset discrete routing menu
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
					this.initVars;
					this.recallValues;
					this.status_("State reloaded. Check Current Settings window.")
				},
				\stoppingAudio, { this.status_("Audio is stopping - Standby.") },
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
		var newLines, curLines, curAndNewLines, newPost;
		curLines = postString.split($\n);
		newLines = ( Date.getDate.format("%a %m/%d %I:%M:%S")++"\t"++ aString).split($\n);
		curAndNewLines = (curLines ++ newLines);
		if((curLines.size + newLines.size) > maxPostLines, {
			var stripNum;
			stripNum = (curLines.size + newLines.size) - maxPostLines;
			curAndNewLines = curAndNewLines.drop(stripNum);
		});
		newPost = "";
		curAndNewLines.do{ |line, i|
			newPost = newPost ++ line ++ "\n";
		};
		postTxt.string_(newPost);
		postString = newPost;
	}

	postState {
		stateTxt.string_("\n"
			++ curSR ++ "\n"
			++ curDecType ++ "\n"
			++ curKernel ++ "\n"
			++ sl.stereoActive ++ "\n"
			++ sl.rotated
		);
	}

	clearDecSelections {|exceptThisKey|
		decMenus.keysValuesDo{|k,v| if(k!=exceptThisKey,{v.value_(0)}) };
		pendingDecType = nil;
	}


	/* PAGE LAYOUT */

	buildControls {
		wsGUI.layout_(
			WsVLayout( Rect(0.025,0.025,0.95,0.95),
				WsStaticText.init(wsGUI, Rect(0,0,1,0.1)).string_(
					format("Sound Lab %\nRouting and Decoding System",sl.configFileName))
				.textAlign_(\center),
				WsHLayout( Rect(0,0,1,0.9),

					// COLUMN 1
					WsVLayout( Rect(0,0,0.45,1),
						WsStaticText.init(wsGUI).string_(
							"<strong>Change Settings</strong>").textAlign_(\center),
						// sample rate
						WsStaticText.init(wsGUI, Rect(0,0,1,0.05)).string_(
							"<strong>Sample Rate</strong>"),
						WsHLayout(Rect(0,0,1,0.05), srMenu, 2),
						// gain
						gainTxt,
						gainSl,
						// mute / attenuate
						WsHLayout(Rect(0,0,1,0.05), muteButton, 0.025, attButton, 1.25),
						0.05,
						// decoder
						WsStaticText.init(wsGUI,Rect(0,0,1,0.08)).string_(
							"<strong>Select an Ambisonic Decoder</strong>"),
						WsHLayout(Rect(0,0,1,0.25),
							WsVLayout(Rect(0,0,0.6, 1),
								WsHLayout( nil,
									WsStaticText.init(wsGUI).string_("2-D Horizontal"),
									horizMenu ),
								WsHLayout( nil,
									WsStaticText.init(wsGUI).string_("3-D Sphere"),
									sphereMenu ),
								WsHLayout( nil,
									WsStaticText.init(wsGUI).string_("3-D Dome"),
									domeMenu )
							),
							0.1,
							// rotation
							WsHLayout( Rect(0,0,0.4, 1),
								WsVLayout( Rect(0,0,0.8, 1),
									1/4,
									WsStaticText.init(wsGUI, Rect(0,0,0.7, 1)).string_(
										format("<strong>Rotate</strong> listening position % degrees?",sl.rotateDegree)),
									1/4),
								WsVLayout( Rect(0,0,0.2, 1), 1/3, rotateMenu, 1/3)
							)
						),
						nil,
						// discrete routing
						WsStaticText.init(wsGUI, Rect(0,0,1,0.08)).string_(
							"<strong>Select a Discrete Routing Layout</strong>"),
						WsHLayout(Rect(0,0,1,0.1),
							WsHLayout( Rect(0,0,0.7,1),
								WsStaticText.init(wsGUI).string_("Which speakers?"),
								discreteMenu ),
							0.6
						),
						nil,
						// stereo
						WsHLayout( Rect(0,0,1,0.12),
							WsStaticText.init(wsGUI).string_("<strong>Insert STEREO channels before decoder/router?</strong>"),
							stereoMenu ),
						nil,
						// correction
						WsHLayout( Rect(0,0,1,0.12),
							WsStaticText.init(wsGUI).string_("<strong>Room correction</strong>"),
							correctionMenu ),
						nil,
						nil,
						applyButton
					),

					// COLUMN 2
					WsVLayout( Rect(0,0,0.1,1),
						// picture
					),

					// COLUMN 3
					WsVLayout( Rect(0,0,0.45,1),
						WsStaticText.init(wsGUI, Rect(0,0,1,0.025)).string_(
							"<strong>Current System Settings</strong>").textAlign_(\center),
						WsHLayout( nil,
							WsStaticText.init(wsGUI).string_(
								"\nSample Rate:  \nDecoder / Router:  \nCorrection:  \nStereo channels first:  \nSound field rotated:  "
							).textAlign_(\right),
							//0.025, //TODO: reimplement this, fix layouts of specified dimensions
							stateTxt
						),
						0.1,
						WsStaticText.init(wsGUI, Rect(0,0,1,0.025)).string_(
							"<strong>Post:</strong>"),
						postTxt
					)
				)
			);
		);
		this.recallValues; /* this will turn on the defaults */
	}

	initVars { |loadCondition|
		pendingDecType = nil;
		pendingInput = nil;
		pendingSR = nil;
		// defaults on startup - pulled from SoundLab state
		// TODO: consider not storing 'cur' variables in gui class, refer to sl directly
		curDecType = sl.curDecoderPatch.decoderName; // not the same as synthdef name
		curSR = sl.sampleRate;
		curKernel = sl.curKernel ?? {\basic_balance};
		stereoPending = nil;
		rotatePending = nil;

		"variables initialized".postln;
		postf("curDecType: %, curSR: %, curKernel: %\n",
			curDecType, curSR, curKernel);
		loadCondition !? {loadCondition.test_(true).signal}
	}

	recallValues {
		fork {
			gainSl.value_(sl.globalAmp.ampdb);
			gainTxt.string_( format("<strong>Gain: </strong>% dB",sl.globalAmp.ampdb.round(0.01)) );
			muteButton.value_( if(sl.isMuted, {1},{0}) );
			attButton.value_( if(sl.isAttenuated, {1},{0}) );
			(decMenus ++ [srMenu, discreteMenu, stereoMenu, rotateMenu, correctionMenu]).do{|menu| menu.value_(0)};
			this.postState;
		}
	}

	cleanup {
		sl.removeDependant( this );
		slhw !? {slhw.removeDependant(this)};
		wsGUI.free;
	}

}

/* TESTING
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false, configFileName: "CONFIG_205.scd")

l.cleanup
s.quit

InterfaceJS.nodePath = "/usr/local/bin/node"
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false)



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