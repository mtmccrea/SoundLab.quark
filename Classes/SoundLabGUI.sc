SoundLabGUI {
	// copyArgs
	var <sl, <wwwPort;
	var <slhw, <deviceAddr, <interfaceJS; //<listeners
	var <decoders, <orders, <sampleRates, <kernels, <stereoPending;
	var <curDecType, <curOrder, <curSR, <curKernel, <pendingDecType, <pendingInput, <pendingOrder, <pendingSR, <pendingKernel;
	var numCols, butheight, vpad, hpad, h1, h2, h3,h4, buth, butw, inLabel;
	var <ampSpec, <oscFuncDict, buildList;

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

			" ---------- \n creating new instance of node JS \n------------".postln;
			interfaceJS = InterfaceJS.new(wwwPort);

			postf("returned from interfaceJS: %\n", interfaceJS);

			// listeners = [];
			decoders = [];
			orders = [];

			sl.decAttributeList.do({ |attset|
				if(decoders.includes(attset[0]).not,
					{decoders = decoders.add(attset[0])})
			});
			sl.decAttributeList.do({ |attset|
				if( orders.includes(attset[2].asSymbol).not,	// cast to symbol for button name
					{orders = orders.add(attset[2].asSymbol)}	// cast to symbol for button name
				)
			});

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
			kernels = kernels.asArray ++ [\basic_balance]; // TODO replaced by \default?
			ampSpec = ControlSpec.new(-80, 12, -2, default: 0);
			sampleRates = [\SR44100, \SR48000, \SR96000];
			this.initVars(cond);
			cond.wait;

			numCols = 4;
			vpad = 1/64;
			hpad = 1/24;
			// height of the 4 sections of the layout
			h1 = 4/32;
			h2 = 20/32;
			h3 = 4/32;
			h4 = 4/32;

			interfaceJS.clear;	// clear everything, just in case
			0.5.wait; 			// see if it helps
			interfaceJS.background_(nil, Color.rand(0, 0.2)); // set page background

			this.buildListeners;
			this.buildControls;	// changed order - build Listeners defines functions
		}
	}

	buildListeners {
		// building individual responders for specific widgets
		oscFuncDict = IdentityDictionary( know: true ).put(
			\ampSlider, {
				|val|
				sl.amp_( val );
		}).put(
			\Attenuate, {
				|val|
				case
				{val == 0} {sl.attenuate(false)}
				{val == 1} {sl.attenuate(true)}
				;
		}).put(
			\Mute, {
				|val|
				case
				{val == 0} {sl.mute(false)}
				{val == 1} {sl.mute(true)}
				;
		}).put(
			\Stereo, {
				|val|
				{
					case
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
			\Update, {
				|val|
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
							pendingOrder.isNil		and:
							pendingSR.isNil			and:
							pendingKernel.isNil		and: // added 5/1/14
							stereoPending.isNil,
							{this.status("No updates."); interfaceJS.value_( \Update, 0); break.()}
						);

						// TODO check here if there's a SR change, if so set state current vars
						// of soundlab then change sample rate straight away,
						// no need to update signal chain twice

						this.setColor( \Update, \yellow );
						updateCond = Condition(false);

						/* Update Decoder/Order/Kernel */
						if( pendingDecType.notNil 	or:
							pendingOrder.notNil		or:
							pendingKernel.notNil	or:
							pendingSR.notNil,
							{
								pendingDecType = pendingDecType ?? curDecType;
								pendingOrder = pendingOrder ?? curOrder;
								// catch invalid order
								if(
									("(T|t)hru".matchRegexp(pendingDecType.asString).not // ambi decoder pending
									and: (pendingOrder == \NA) // ambi order not specified
									),
									{this.status("Invalid order."); interfaceJS.value_( \Update, 0); break.()}
								);
								this.status(("Updating decoder to "++pendingDecType++" order "++pendingOrder).postln);

								if( pendingKernel.notNil,
									{
										sl.startNewSignalChain(pendingDecType, pendingOrder, pendingKernel, updateCond);
									},{
										"before: ".post; updateCond.postln;
										sl.startNewSignalChain(pendingDecType, pendingOrder, completeCondition: updateCond);
								});
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
		buildList = [decoders, orders, sampleRates, kernels];
		buildList.do({ |controls, i|
			controls !? {			// sometimes kernels is nil if not used
				controls.do({
					|selected|
					oscFuncDict.put(selected, {
						|val|
						var current, pending;
						// get the current and pending settings for the selected category
						current = switch( i,
							0, {curDecType},
							1, {curOrder},
							2, {curSR},
							3, {curKernel}
						);
						pending = switch( i,
							0, {pendingDecType},
							1, {pendingOrder},
							2, {pendingSR},
							3, {pendingKernel}
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
											1, { if( "(T|t)hru".matchRegexp(pendingDecType.asString).not,
												{
													/*debug*/("setting pending order: "++selected).postln;
													pendingOrder = selected
												},{
													/*debug*/"pending decoder is a thru decoder, setting thru controls".postln;
													this.setThruControls(pendingDecType);
													break.()
												}
											)},
											2, {pendingSR = selected},
											3, {
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
											1, {pendingOrder = nil},
											2, {pendingSR = nil},
											3, {pendingKernel = nil}
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
	}

	// for updating the GUI when SoundLab model is changed
	// this is impportant to see feedback as to the state of decoder communication
	update {
		| who, what ... args |
		if( who == sl, {
			switch ( what,
				\amp,	{
					interfaceJS.value_(\ampLevelLabel, args[0].ampdb.round(0.1).asString);
				},
				\attenuate,	{
					switch ( args[0],
						0, {
							this.setColor(\Attenuate, \default);
							if( sl.isMuted.not, {
								this.status("Amp restored.") },{ this.status("Muted.")
							});
						},
						1, {
							this.setColor(\Attenuate, \purple);
							if( sl.isMuted.not,
								{
									this.status("Attenuated.")
								},{
									this.status("Muted, attenuated.")
							});
						}
					)
				},
				\mute,	{
					switch ( args[0],
						0, {
							this.setColor(\Mute, \default);
							if( sl.isAttenuated.not,
								{ this.status("Amp restored.") },
								{ this.status("Attenuated.") }
							);
						},
						1, {
							this.setColor(\Mute, \red);
							if( sl.isAttenuated.not,
								{ this.status("Muted.") },
								{ this.status("Muted, attenuated.") }
							);
						}
					)
				},
				\clipped,	{
					if( args[0] < sl.numHardwareIns,
						{
							interfaceJS.value_(\I, 1);
							this.status("Clipped IN " ++ args[0].asString);
						},{
							interfaceJS.value_(\O, 1);
							this.status("Clipped OUT " ++ (args[0]-sl.numHardwareIns).asString);
						}
					)
				},
				\decoder,	{
					var decPatch;
					decPatch = args[0];
					curDecType = decPatch.decoderName;
					curOrder = decPatch.order;
					pendingDecType = nil;
					pendingOrder = nil;

					this.clearControls(decoders, curDecType, nil);
					this.clearControls(orders, curOrder, nil);
					this.setCtlActive( curDecType );
					this.setCtlActive( curOrder );
					this.status(("Now decoding with: " ++ decPatch.decoderName).postln);
				},
				\stereo,	{
					var stereoActive;
					stereoActive = args[0];
					if( stereoActive,
						{
							this.setCtlActive(\Stereo);
							this.status(("Stereo added to first two output channels.").postln);
							stereoPending = nil;

						},{
							this.setColor(\Stereo, \default);
							interfaceJS.value_( \Stereo, 0);
							this.status(("Stereo cleared.").postln);
							stereoPending = nil;
						}
					)
				},
				\kernel,	{
					var k_name;
					k_name = args[0];
					k_name !? {k_name = k_name.asSymbol};
					curKernel = k_name;
					this.clearControls(kernels, k_name, nil);
					k_name.notNil.if({this.setCtlActive(k_name)}, {this.setCtlActive(\basic_balance)});
					this.status(("Kernel updated: "++k_name).postln);
					pendingKernel = nil;
				},
				\stateLoaded,	{
					this.recallValues;
					// this.updateSrButtons;	// TODO: check this
				},
				\reportError,	{ this.status(args[0]) },
				\reportStatus,	{ this.status(args[0]) }
			);
		});
		if( who == slhw, {
			switch( what,
				\audioIsRunning, {
					if(args[0].not, {
							this.status(("Audio stopped. Cannot update at this time.").postln)
						}
					);
				},
				\stoppingAudio, {
					this.status(("Audio is stopping - Standby.").postln);
				}
			)
		});
	}

	setCtlPending { |which|
		this.setColor(which, \yellow);
		this.status((which ++ " is pending.").postln);
	}

	setCtlActive { |which|
		this.setColor(which,
			if( which == \Mute, {\red},
				{ if( which == \Attenuate, {\purple},
					{\green} /* otherwise, all else are green when active */
				)}
			);
		);
		interfaceJS.value_( which, 1);
	}

	clearControls { |controlArr, current, selected|
		controlArr.do({ |name|
			if( ((name != current) && (name != selected)), {
				("turning off: " ++ name).postln;
				interfaceJS.value_( name.asSymbol, 0);
				this.setColor( name, \default);
			});
		})
	}

/*	updateSrButtons {
		sampleRates.do({|thisRate|
			"thisRate: ".post; thisRate.postln;
			if(thisRate.notNil, {
				this.setColor(thisRate, \default);
				interfaceJS.value_(thisRate, 0);
			});
		});
		if(curSR.notNil, {
			("curSR in udateSrButtons is: "++curSR).postln;
			this.setColor(curSR, \green);
			interfaceJS.value_(curSR, 1);
			}, {
				this.status("Warning: curSR is nil (in updateSrButtons)");
				"curSR is nil (in updateSrButtons), you gotta be kiddin'...".warn;
		});
	}*/

	// TODO Update this
	setThruControls { |whichThruCtl|
		// update decoder controls
		pendingDecType = whichThruCtl;
		this.clearControls( decoders, curDecType, whichThruCtl );
		this.setCtlPending( whichThruCtl );
		// update order controls
		pendingOrder = \NA;
		this.clearControls( orders, curOrder, whichThruCtl );
		this.setCtlPending( \NA );
		interfaceJS.value_( \NA, 1);

		this.status( "Thru: direct outs, no BF decoder." );
	}

	status { |postString|
		interfaceJS.value_(\Status, postString);
	}

	setColor { |controlName, color|
		switch( color,
			\yellow, {
				interfaceJS.backgroundFillStroke_(controlName,
					Color.fromHexString("#000000"),
					Color.fromHexString("#FFCC00"),
					Color.white)},
			\purple, {
				interfaceJS.backgroundFillStroke_(controlName,
					Color.fromHexString("#000000"),
					Color.fromHexString("#CC0099"),
					Color.white)},
			\red, {
				interfaceJS.backgroundFillStroke_(controlName,
					Color.fromHexString("#000000"),
					Color.fromHexString("#FF0033"),
					Color.white)},
			\green, {
				interfaceJS.backgroundFillStroke_(controlName,
					Color.fromHexString("#000000"),
					Color.fromHexString("#66FF00"),
					Color.white)},
			\default, {
				interfaceJS.backgroundFillStroke_(controlName,
					Color.fromHexString("#000000"),
					Color.fromHexString("#aaaaaa"),
					Color.white)}
		)
	}

	buildControls {
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
			\STEREO,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, [\Stereo]
			]),
			\DECODER,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, decoders
			]),
			\ORDER,
			IdentityDictionary.new(know: true).putPairs([
				\kind, \button,
				\controls, orders
			]),
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
	}

	initVars { |loadCondition|
		pendingDecType = nil;
		pendingOrder = nil;
		pendingInput = nil;
		pendingSR = nil;
		// defaults on startup - pulled from SoundLab state
		curDecType = sl.curDecoderPatch.decoderName; // not the same as synthdef name
		curOrder = sl.curDecoderPatch.order.asSymbol;
		curSR = (\SR ++ sl.sampleRate).asSymbol;
		curKernel = sl.curKernel ?? {\basic_balance};
		stereoPending = nil;

		"variables initialized".postln;
		postf("curDecType: %, curOrder: %, curSR: %, curKernel: %\n",
			curDecType, curOrder, curSR, curKernel);
			loadCondition !? {loadCondition.test_(true).signal}
	}

	recallValues {
		var cond;
		fork {
			cond = Condition(false);
			this.initVars(cond);
			cond.wait;

			interfaceJS.value_( \ampLevelLabel, sl.globalAmp.ampdb.round(0.1).asString );
			interfaceJS.value_(\ampSlider, sl.globalAmp);
			if(sl.stereoActive, {this.setCtlActive(\Stereo)});
			if(sl.isMuted, {this.setCtlActive(\Mute)});
			if(sl.isAttenuated, {this.setCtlActive(\Attenuate)});

			this.clearControls(sampleRates, curSR, nil); // TODO, why don't I have to clear the other categories?

			/*  turn on defaults  */
			[curDecType, curOrder, curSR, curKernel].do({ |name|
				("turning on" + name).postln;
				if( name.notNil, {
					// remove calls to non-existing controls
					if(interfaceJS.guiObjects[name].notNil, {
						this.setCtlActive(name)
					})
				},{"nil found when turning on controls".warn})
			});

			interfaceJS.value_( \Update, 0);
			this.setColor( \Update, \default );
			/*0.2.wait;
			interfaceJS.reloadPage;*/
		}
	}

	cleanup {
		sl.removeDependant( this );
		slhw !? {slhw.removeDependant(this)};
		interfaceJS.free;
		// listeners.do(_.free);
	}

}

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

// removed when moving to JS
/*
makeButton { |name, xpos, ypos, height, width, color = '#aaaaaa', stroke = '#555555', labeltext, funcstring|
var msg;
msg = "{'name' : '" ++ name ++
"','type' : 'Button', " ++
"'x' : "++ xpos ++ ", " ++
"'y' : "++ ypos ++ ", " ++
"'width' : "++ height ++ ", "++
"'height' : "++ width ++ ", "++
"'label' : '"++	(labeltext ?? name).asString ++ "', "++
"'labelSize' : 20.0, "++
"'color' : '"++color++"', "++
"'stroke' : '"++stroke++"', "++
funcstring.notNil.if({ funcstring++", }" },{ "}" })
;
// msg.postln;
deviceAddr.sendMsg( "/control/addWidget", msg );
}

// make a slider function
makeSlider { |name, xpos, ypos, height, width, vert = true|
var msg;
msg = "{'name' : '" ++ name ++
"','type' : 'Slider', " ++
"'x' : "++ xpos ++ ", " ++
"'y' : "++ ypos ++ ", " ++
"'width' : "++ height ++ ", "++
"'height' : "++ width ++ ", "++
"'isVertical' : '"++vert++"', }";
deviceAddr.sendMsg( "/control/addWidget", msg );
}

// make a label function
makeLabel { |name, xpos, ypos, height, width, labeltext, size, align|
var msg;
msg = "{'name' : '" ++ name ++
"','type' : 'Label', " ++
"'x' : "++ xpos ++ ", " ++
"'y' : "++ ypos ++ ", " ++
"'width' : "++ height ++ ", "++
"'height' : "++ width ++ ", "++
"'size' : " ++ (size ?? 24.0) ++ ", "++
"'align' : '"++ (align ?? "center") ++ "', "++
"'value' : '" ++ (labeltext ?? name).asString ++ "', }";
deviceAddr.sendMsg( "/control/addWidget", msg );
}

// send this computer's IP and port to Control app and set as "destination" to send its messages
pushMyIP {
var pipe, addr;
pipe   = Pipe.new("ipconfig getifaddr en1", "r");
//for linux; address of the SECOND network interface (eth1)
// pipe   = Pipe.new("ifconfig eth1 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1}'", "r");
addr   = pipe.getLine().postln;
deviceAddr.sendMsg("/control/pushDestination", addr.asString ++":"++NetAddr.langPort.asString);
}
addListener { |tag, oscfunc|
listeners = listeners.add( OSCFunc(oscfunc, tag, nil) );
}

*/