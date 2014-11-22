SoundLabGUI {
	// copyArgs
	var <sl, <wwwPort;
	var <slhw, <deviceAddr, <wsGUI; //<listeners
	var <decsHoriz, <decsSphere, <decsDome, <discreteRouters, <decsMatrix;
	var <sampleRates, <stereoPending, <rotatePending;
	var <gainTxt, <gainSl, <muteButton, <attButton, <srMenu, <decMenus, <decMenuLayouts, <horizMenu, <sphereMenu, <domeMenu, < matrixMenu, <discreteMenu, <stereoMenu, <rotateMenu, <correctionMenu, <applyButton, <stateTxt, <postTxt;
	var <pendingDecType, <pendingInput, <pendingSR, <pendingKernel; //<curKernel, <curSR, <curDecType,
	// var numCols, butheight, vpad, hpad, h1, h2, h3,h4, buth, butw, inLabel;
	var <ampSpec, <oscFuncDict, buildList;
	var <>maxPostLines, <postString, font = 'Helvetica', lgFontSize = 24, mdFontSize = 20, smFontSize = 16 ;

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

			" ---------- \n creating new instance of wsGUI \n------------".postln;
			wsGUI = WsGUI.new(wwwPort);
			1.5.wait; // TODO: add condition

			/*
			build arrays of the decoder/router synth names
			as specified in the config file
			*/
			decsHoriz = [];
			decsSphere = [];
			decsDome = [];
			discreteRouters = [];
			decsMatrix = [];

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

			decsMatrix = sl.matrixDecoderNames;

			postf(
				"decsHoriz = %\n
				decsSphere = %\n
				decsDome = %\n
				discreteRouters = %\n
				decsMatrix = %\n",
				decsHoriz, decsSphere, decsDome, discreteRouters, decsMatrix);

			ampSpec = ControlSpec.new(-80, 12, -2, default: 0);
			sampleRates = [44100, 48000, 96000];
			maxPostLines = 16;
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
		gainTxt = WsStaticText.init(wsGUI, Rect(0,0,1,0.05)).string_("").font_(Font(font, mdFontSize))
		;
		gainSl = WsEZSlider.init(wsGUI)
		.controlSpec_(ampSpec) //only in EZSlider;
		.action_({|sldr|
			sl.amp_( sldr.value )})
		;
		// MUTE / ATTENUATE
		muteButton = WsButton.init(wsGUI)
		.states_([
			["Mute", Color.black, Color.fromHexString("#FAFAFA")],
			["Muted", Color.white, Color.fromHexString("#F78181")]
		])
		.action_({ |but|
			switch( but.value,
				0, {sl.mute(false)}, 1, {sl.mute(true)}
			)
		})
		;
		attButton = WsButton.init(wsGUI)
		.states_([
			["Attenuate", Color.black, Color.fromHexString("#FAFAFA")],
			["Attenuated", Color.white, Color.fromHexString("#E2A9F3")]
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
					(mn.item == sl.sampleRate.asSymbol).if({
						this.status_("Requested samplerate is already current.");
						mn.valueAction_(0); // set back to '-'
						},{	pendingSR = mn.item.asInt }
					)
				}
			)
		})
		;
		// DECODER
		decMenus = Dictionary();

		(decsHoriz.size > 0).if{
			decMenus.put( \horiz,
				IdentityDictionary(know: true)
				.put( \menu, horizMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsHoriz) )
				.put( \label, "2-D Horizontal")
			);
		};
		(decsSphere.size > 0).if{
			decMenus.put( \sphere,
				IdentityDictionary(know: true)
				.put( \menu, sphereMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsSphere) )
				.put( \label, "3-D Sphere" )
			);
		};
		(decsDome.size > 0).if{
			decMenus.put( \dome,
				IdentityDictionary(know: true)
				.put( \menu, domeMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsDome) )
				.put( \label, "3-D Dome" )
			);
		};
		(decsMatrix.size > 0).if{
			decMenus.put( \matrix,
				IdentityDictionary(know: true)
				.put( \menu, matrixMenu = WsPopUpMenu.init(wsGUI).items_(['-'] ++ decsMatrix) )
				.put( \label, "Custom Matrix" )
			);
		};

		decMenuLayouts = [];
		[\horiz, \sphere, \dome, \matrix].do{|key|
			decMenus[key].notNil.if{
				decMenuLayouts = decMenuLayouts.add(
					WsHLayout( nil,
						WsStaticText.init(wsGUI).string_(decMenus[key].label).font_(Font(font, mdFontSize)),
						decMenus[key].menu )
				)
			}
		};

		decMenus.keysValuesDo{|k,v|
			decMenus[k].menu.action_({|mn|
				this.clearDecSelections(k); // sets pendingDecType to nil
				discreteMenu.value_(0);		// reset discrete routing menu
				pendingDecType = if(mn.item != '-', {mn.item},{nil});
			})
		};

		// DISCRETE ROUTING
		discreteMenu = WsPopUpMenu.init(wsGUI)
		.items_(['-'] ++ discreteRouters)
		.action_({|mn|
			this.clearDecSelections(); // sets pendingDecType to nil
			pendingDecType = if(mn.item != '-', {mn.item},{nil});
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
		.items_(['-'] ++ sl.kernels)
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

							pendingDecType = pendingDecType ?? sl.curDecoderPatch.decoderName;
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
		stateTxt = WsStaticText.init(wsGUI).string_("").font_(Font(font, mdFontSize))
		;
		// POST WINDOW
		postTxt = WsStaticText.init(wsGUI).string_("").font_(Font(font, smFontSize))
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
					// curDecType = args[0].decoderName; // args[0] is the decoderpatch
					this.clearDecSelections;	// sets pending decoder to nil
					discreteMenu.value_(0);		// reset discrete routing menu
					this.status_("Now decoding with: " ++ sl.curDecoderPatch.decoderName);
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
					// var k_name;
					// k_name = args[0];
					// curKernel = k_name !? {k_name.asSymbol};
					correctionMenu.valueAction_(0);
					this.status_("Kernel updated: " ++ sl.curKernel);
				},
				\stateLoaded,	{
					this.initVars;
					this.recallValues;
					this.status_("State reloaded. Confirm in Current Settings window.")
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
		stateTxt.string_("<strong>"
			++ sl.sampleRate ++ "\n "
			++ sl.curDecoderPatch.decoderName ++ "\n "
			++ sl.curKernel ++ "\n "
			++ sl.stereoActive.if({"YES"},{"NO"}) ++ "\n "
			++ sl.rotated.if({"YES"},{"NO"})
			++ "</strong>"
		);
	}

	clearDecSelections {|exceptThisKey|
		decMenus.keysValuesDo{|k,v|
			if(k != exceptThisKey,{v.menu.value_(0)}) }
		;
		pendingDecType = nil;
	}


	/* PAGE LAYOUT */

	buildControls {
		wsGUI.layout_(
			WsVLayout( Rect(0.025,0.025,0.95,0.95),
				WsStaticText.init(wsGUI, Rect(0,0,1,0.1)).string_(
					format("<strong>%\nRouting and Decoding System</strong>",sl.labName))
				.textAlign_(\center).font_(Font(font, lgFontSize)),
				WsHLayout( Rect(0,0,1,0.9),

					// COLUMN 1
					WsVLayout( Rect(0,0,0.45,1),
						WsStaticText.init(wsGUI).string_(
							"<strong>Change Settings</strong>").textAlign_(\center).font_(Font(font, mdFontSize)),
						// sample rate
						WsStaticText.init(wsGUI, Rect(0,0,1,0.05)).string_(
							"<strong>Sample Rate</strong>").font_(Font(font, mdFontSize)),
						WsHLayout(Rect(0,0,1,0.05), srMenu, 2),
						0.05,
						// gain
						gainTxt,
						gainSl,
						// mute / attenuate
						WsHLayout(Rect(0,0,1,0.05), muteButton, 0.025, attButton, 1.25),
						0.05,
						// decoder
						WsStaticText.init(wsGUI,Rect(0,0,1,0.08)).string_(
							"<strong>Select an Ambisonic Decoder</strong>").font_(Font(font, mdFontSize)),
						WsHLayout(Rect(0,0,1,0.25),
							WsVLayout(Rect(0,0,0.55, 1),
								*decMenuLayouts
							),
							0.05,
							// rotation
							WsHLayout( Rect(0,0,0.4, 1),
								WsVLayout( Rect(0,0,0.7, 1),
									1/4,
									WsStaticText.init(wsGUI, Rect(0,0,1,0.5))
									.string_( format(
										"<strong>Rotate</strong> listening position % degrees?",
											sl.rotateDegree))
									.font_(Font(font, mdFontSize)),
									1/4
								),
								WsVLayout( Rect(0,0,0.3, 1), 1/3, rotateMenu, 1/3)
							)
						),
						0.05,
						// discrete routing
						WsStaticText.init(wsGUI, Rect(0,0,1,0.08))
						.string_("<strong>Select a Discrete Routing Layout</strong>")
						.font_(Font(font, mdFontSize)),
						WsHLayout(Rect(0,0,1,0.1),
							WsHLayout( Rect(0,0,0.55,1),
								WsStaticText.init(wsGUI)
								.string_("Which speakers?")
								.font_(Font(font, mdFontSize)),
								discreteMenu ),
							0.45
						),
						nil,
						// stereo
						WsHLayout( Rect(0,0,1,0.12),
							stereoMenu.bounds_(Rect(0,0, 0.1,1)),
							0.15,
							WsStaticText.init(wsGUI, Rect(0,0, 0.75,1))
							.string_("<strong>Insert STEREO channels before decoder/router?</strong>")
							.font_(Font(font, mdFontSize))
							),
						nil,
						// correction
						WsHLayout( Rect(0,0,1,0.12),
							correctionMenu.bounds_(Rect(0,0, 0.2,1)),
							0.05,
							WsStaticText.init(wsGUI, Rect(0,0, 0.75,1))
							.string_("<strong>Room correction</strong>")
							.font_(Font(font, mdFontSize))
							),
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
						WsHLayout( Rect(0,0,1, 0.06),
							0.15,
						WsStaticText.init(wsGUI, Rect(0,0,0.7,1)).string_(
							"<strong>Current System Settings</strong>")
						.textAlign_(\center).font_(Font(font, mdFontSize))
							.backgroundColor_(Color.fromHexString("#FFFFCC")),
							0.15
						),
						WsHLayout( nil,
							0.15,
							WsStaticText.init(wsGUI, Rect(0,0,0.35,1)).string_(
								"Sample Rate: \nDecoder / Router: \nCorrection: \nStereo channels first: \nSound field rotated: \n"
							)
							.textAlign_(\right)
							.font_(Font(font, mdFontSize))
							// .backgroundColor_(Color.yellow),
							.backgroundColor_(Color.fromHexString("#FFFFCC")),

							stateTxt.bounds_(Rect(0,0,0.35,1))
							.backgroundColor_(Color.fromHexString("#FFFFCC")),
							0.15
						),
						0.1,
						WsStaticText.init(wsGUI, Rect(0,0,1,0.04))
						.string_("<strong>Post:</strong>").font_(Font(font, mdFontSize)),

						postTxt.bounds_(Rect(0,0,1,0.6))
						.backgroundColor_(Color.fromHexString("#F2F2F2"))
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
		// curDecType = sl.curDecoderPatch.decoderName; // not the same as synthdef name
		// curKernel = sl.curKernel ?? {\basic_balance};
		stereoPending = nil;
		rotatePending = nil;

		postf("current decoder: %, current SR: %, curKernel: %\n",
			sl.curDecoderPatch.decoderName, sl.sampleRate, sl.curKernel);
		loadCondition !? {loadCondition.test_(true).signal}
	}

	recallValues {
		fork {
			gainSl.value_(sl.globalAmp.ampdb);
			gainTxt.string_( format("<strong>Gain: </strong>% dB",sl.globalAmp.ampdb.round(0.01)) );
			muteButton.value_( if(sl.isMuted, {1},{0}) );
			attButton.value_( if(sl.isAttenuated, {1},{0}) );
			correctionMenu.items_(['-'] ++ sl.kernels); // reload the kernel names in the case of a sample rate change
			(
				decMenus.values.collect({|dict| dict.menu})
				++ [srMenu, discreteMenu, stereoMenu, rotateMenu, correctionMenu]
			).do{ |menu| menu.value_(0)};
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
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: false, configFileName: "CONFIG_117.scd",usingOSX: true)
l = SoundLab(48000, loadGUI:true, useSLHW: false, useKernels: true, configFileName: "CONFIG_TEST.scd", usingOSX: true)

l.cleanup
s.quit

s.options.numOutputBusChannels

x = 4.collect{|i| { Out.ar(l.curDecoderPatch.inbusnum+i,PinkNoise.ar(0.75)* SinOsc.kr(0.2*(i+1)).range(0.1,1))}.play}
x.do(_.free)
s.meter


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