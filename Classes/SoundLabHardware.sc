/*
Class for managing 117 Sound Lab in Raitt hall, DXARTS, University of Washington, Seattle
Coded in 2012, 2013 by Marcin Paczkowski (marcinp@uw.edu)
work in progress! :)
*/
/*
usage:
a = SoundLabHardware.new;
a.startAudio(96000);
a.stopAudio;
a.sampleRate_(44100); //also valid: 48000, 88200, 96000
under osx(for test):
l = SoundLabHardware.new(false, true, false, nil, nil, "jackdmp");
l.startAudio;
l.stopAudio
*/

/*
Changelog....?

2013.01.14
-added sampleRate method

2013.01.11
-added Condition
-updated notifications (.changed)

2013.12.05
-added fireface control
	//fireface IDs
	//000a3500c1da0056 //205
	//000a35009caf3c69 //117
*/

SoundLabHardware {
	var useSupernova, fixAudioInputGoingToTheDecoder, <>useFireface;
	var <midiPortName, <cardNameIncludes, jackPath;
	var serverIns, serverOuts, numHwOutChToConnectTo, numHwInChToConnectTo;
	var firefaceID;
	var whichMadiInput, whichMadiOutput;
	// var firstOutput = 66, firstInput = 66;//0 for 117, 64 for 205, at 96k!
	var cardID, <sampleRate, /*<ins, <outs, */midiPort, <server;
	// var getCardID, setSR, initMIDI, sendSysex, startJack, stopJack, initAll, prStartServer;
	var <audioIsRunning, parametersSetting;//bools for status
	var updatingCondition, isUpdating;
	var startRoutine;
	var addWaitTime;
	var jackWasStartedBySC;
	var <clientsDictionary; //name -> [outs, ins]
	var newJackName, newJackIns, newJackOuts, <jackPeriodSize;
	var serverInputNameInJack;
	var fixAudioIn;
	var <dbusServerPid, <phantomState;

	//remmeber to use update notification at some point!!!!


	*new {arg useSupernova = false,
		fixAudioInputGoingToTheDecoder = true,
		useFireface = true,
		midiPortName = "External MIDI-MADIFXtest MIDI 1", // nil for no MIDI
		cardNameIncludes = "RME", // nil for OSX
		jackPath = "/usr/bin/jackd",
		serverIns = 32,
		serverOuts = 128,
		numHwOutChToConnectTo = 32,
		numHwInChToConnectTo = 32,
		firefaceID = "000a3500c1da0056",//117: 000a35009caf3c69, 205: 000a3500c1da0056
		whichMadiInput = 2,
		whichMadiOutput = 2;//nil for regular MADI, 0-2 for MADIFX
		// ^super.newCopyArgs(shellCommand, receiveAction, exitAction, id).init(false);
		^super.newCopyArgs(useSupernova, fixAudioInputGoingToTheDecoder, useFireface, midiPortName, cardNameIncludes, jackPath, serverIns, serverOuts, numHwOutChToConnectTo, numHwInChToConnectTo, firefaceID, whichMadiInput, whichMadiOutput).init;
	}

	*killJack {
		"killall -9 jackdbus".unixCmd;
		"killall -9 jackd".unixCmd;
	}

	init {//arg useSupernova, fixAudioInputGoingToTheDecoder;
		// midiPortName.postln;
		// useFireface.postln;
		this.dump;
		//notification
		this.changed(\updatingConfiguration, 0.0);
		//set default synth
		if(useSupernova, {
			Server.supernova;
			serverInputNameInJack = "supernova:input_"; //this could be set with s.options.device = "supercollider", but not sure about in vs input... leaving for now
			}, {
				Server.scsynth;
				serverInputNameInJack = "SuperCollider:in_";
		});
		Server.default = server = Server.local; //just to make sure
		//init vals
		this.audioIsRunning_(false);
		updatingCondition = Condition.new(false);
		addWaitTime = 0;
		jackWasStartedBySC = false;
		fixAudioIn = fixAudioInputGoingToTheDecoder;
		phantomState = 4.collect({false});
		// clientsDictionary = Dictionary.new; //not here
		//init hardware
		this.prGetCardID;
		this.prInitMIDI;
		this.changed(\updatingConfiguration, 1.0);
		if(cardNameIncludes.isNil, {
			server.options.device_("JackRouter");//osx hack?
		});
		// this.changed(\audioIsRunning, false);
	}

	startAudio { arg newSR, periodSize = 256, periodNum = 2;
		// "audioIsRunning: ".post; audioIsRunning.postln;
		// "this.prSampleRateIsCorrectOrNil(newSR): ".post; this.prSampleRateIsCorrectOrNil(newSR).postln;
		if(this.prSampleRateIsCorrectOrNil(newSR), {
			if(startRoutine.isPlaying, {
				startRoutine.stop;
			});
			startRoutine = Routine.run({
				clientsDictionary = Dictionary.new; //reset names
				this.changed(\updatingConfiguration, 0.0);
				// if(audioIsRunning, {
				//for now - do it always, just in case something was running before
					"stopping audio first...".postln;

					this.prStopAudioFunction;
					// 6.wait;
					// "------------- before wait".postln;
					updatingCondition.wait;
				// if(useFireface, { conditinal moved to class
					this.initFireface; //init fireface here as well
				// });
					addWaitTime.wait;
			// });
				// "------------- after wait".postln;
/*				this.changed(\updatingConfiguration, 0.2);
				if(newSR.notNil, {
					this.prSetSR(newSR);
					// 6.wait;
					updatingCondition.wait;
				});*/
				this.changed(\updatingConfiguration, 0.3);
				if(sampleRate.isNil && newSR.isNil, {
					newSR = 96000;
					"using default sample rate: 96000".postln;
				});
				this.prSetSR(newSR);
				// "---- 3".postln;
				// 6.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				this.changed(\updatingConfiguration, 0.6);
				// "---- 4".postln;
				this.prStartJack(periodSize, periodNum);
				// "---- after jack".postln;
				// 4.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				// "--before setting jack connections".postln;
				this.prSetJackConnections;
				//Fireface - phantom and Routing
				this.setDefaultFfRouting;
				this.recallFfPhantom;
				this.changed(\updatingConfiguration, 0.9);
				// "--before starting server".postln;
				this.prStartServer;
				// 2.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				this.audioIsRunning_(true);
				addWaitTime = 0;
				clientsDictionary = Dictionary.new;
				// fix for not getting analog ins in the decoder - got moved into separate method, uses env vars now
/*				if((sampleRate > 48000) && fixAudioIn, {
					this.prDisconnectRedundantHardwareIns((16..32)); //keeping as is for now
					//this
					//"SC_JACK_DEFAULT_OUTPUTS".setenv("".ccatList(16.collect({|inc| "system:playback_" ++ (inc + 1).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1));
					//not
					//would make SC connect only to the first 16 outputs (needs appro
				});*/
				// clientsDictionary.add("PreSonus", [ins * 0.5, 0]);
				this.prAddClient("PreSonus", [32 * 0.5, 32 * 0.5], false); //ins, because outs is fake 32 for 48k
				this.changed(\updatingConfiguration, 1.0);
				// this.changed(\audioIsRunning, true);
			});
			}, {
				"Wrong sample rate! Valid are: 44100, 48000, 88200, 96000".error;
				this.changed(\error, "Wrong sample rate!");
		});
	}

	audioIsRunning_ {arg isRunningBool;
		audioIsRunning = isRunningBool;
		this.changed(\audioIsRunning, isRunningBool);
	}


	stopAudio {
		Routine.run({
			if(startRoutine.isPlaying, {
				startRoutine.stop;
			});
			1.wait;
			this.prStopAudioFunction;
		});
	}


	sampleRate_ {arg newSR;
		if(this.prSampleRateIsCorrectOrNil(newSR), {
			if((newSR != sampleRate) || audioIsRunning.not, {
				this.startAudio(newSR);
			}, {
					("The system is already running at " ++ sampleRate ++ "!").postln;
			});
			}, {
				"Wrong sample rate! Valid are: 44100, 48000, 88200, 96000".error;
				this.changed(\error, "Wrong sample rate!");
		});
	}

	prStopAudioFunction {
		updatingCondition.test = true;
		this.changed(\updatingConfiguration, 0.0);
		this.changed(\stoppingAudio);
		server.quit;
		// 3.wait;
		// updatingCondition.wait;
		//jack
		this.changed(\updatingConfiguration, 0.6);
		this.audioIsRunning_(false);//moved here so jack knows we're stopping
		this.prStopJack;
		//clear Fireface
		this.clearFireface;
		// 1.wait;
		updatingCondition.wait;
		1.wait;//just to make sure everything's off
		this.changed(\updatingConfiguration, 1.0);
		// this.changed(\audioIsRunning, false);
	}


	//get alsa card number
	prGetCardID {/* arg cardNameIncludes = "RME";*/
		//get card ID
		var p, l, extractedID;
		postf("cardNameIncludes: %\n", cardNameIncludes);
		if(cardNameIncludes.notNil, {
			if(cardNameIncludes.isKindOf(SimpleNumber), {
				cardID = cardNameIncludes;
			}, {
				p = Pipe.new("cat /proc/asound/cards", "r");
				l = p.getLine;
				while({l.notNil}, {
					// l.postln;
					if(l.contains(cardNameIncludes), {
						extractedID = l.split($ )[1];
						// "l.split($ ): ".post; l.split($ ).postln;
						// extractedID.postln;
						if(extractedID.size > 0, { //use only lines, where there is something as the second argument
							cardID = extractedID.asInteger;
							l.postln; "cardID: ".post; cardID.postln;
						});
					});
					l = p.getLine;
				});    // run until l = nil
				p.close; // close the pipe
			});
		}, {
			cardID = 0;
		});
		postf("cardID: %\n", cardID);
	}

	prInitMIDI {
		"ini here".postln;
		// init MIDI
		if(midiPortName.notNil, {
			// "now ini here".postln;
			MIDIClient.init;
			// MIDIClient.destinations;
			// MIDI INIT!!!!!! don't forget to connect.... blah
			midiPort = MIDIOut.newByName(midiPortName, midiPortName);
			postf("midiPort: %\n", midiPort);
			try { midiPort.connect(midiPort.port); };
		});
		postf("midiPort: %\n", midiPort);
	}

	prSendSysex { arg midiDevice, data; // for DA-32 and DA-16
		var sysexHeader, manufacturerID, modelID, bankOrDeviceID, messageType, eof;
		var sysexCommand;
		if(midiPortName.notNil, {
			// these are taken from RME M-32 DA documentation
			sysexHeader = 0xf0;
			manufacturerID = [0x00, 0x20, 0x0d];
			modelID = 0x32;
			bankOrDeviceID = 0x7f; //0x7f addresses all devices... just to be sure.
			messageType =  0x20; //this is to set values (0x10 to request, 0x30 for response)
			eof = 0xf7; //end of the message
			sysexCommand = Int8Array.newFrom(
				sysexHeader.asArray ++ manufacturerID.asArray ++
				modelID.asArray ++ bankOrDeviceID.asArray ++
				messageType.asArray ++ data.asArray ++
				eof.asArray
			);
			// sysexCommand.dump;
			"sending SysEx: ".post; sysexCommand.postln;
			midiDevice.sysex(sysexCommand);
		});
	}
	//setting clock on RME - trial and error...
	/*
	sendSysex.value(m, [0x01, 2r00000000]);//44.1k
	sendSysex.value(m, [0x01, 2r00010000]);//48k
	sendSysex.value(m, [0x01, 2r00000001]);//88.2k
	sendSysex.value(m, [0x01, 2r00010001]);//96k
	*/

	prStartJack { arg periodSize = 256, periodNum = 2;//, jackPath = "/usr/bin/jackd";
		var cmd, options;
		updatingCondition.test = false;
/*		if("pidof jackd".unixCmdGetStdOut.size > 0, {
			"jack was running -
			this.prStopJack;
		});
		while({"pidof jackd".unixCmdGetStdOut.size > 0}, {"waiting for jack to stop...".postln; 0.1.wait});*/
		cmd = "exec " ++ jackPath ++
		" -R ";
		if(cardNameIncludes.notNil, {
			cmd = cmd ++ " -dalsa -H -dhw:"++cardID.asString; //assuming linux
		}, {
			cmd = cmd ++ " -dcoreaudio"; //assuming osx
		});
		cmd = cmd ++ " -r"++sampleRate.asString++
		" -p"++periodSize.asString++
		" -n"++periodNum.asString++
		" -D";//++
		// " -i"++ins.asString++ //needs to be exact as MADI expects, not needed?
		// " -o"++outs.asString;
		// " -o"++ins.asString; //needs to be exact as MADI expects, not needed?
		"run jack command ".post; cmd.postln;
		cmd.unixCmdGetStdOutThruOsc({|line|
			"from jack: ".post; line.postln;
			this.prParseJackOutput(line);
			}, {
				if(audioIsRunning, {
					"Jack crashed, restarting!".warn;
					startRoutine.stop;
					// this.stopAudio;
					"killall scsynth".unixCmd;
					"killall supernova".unixCmd;
					this.changed(\message, "Jack crashed, restarting!");
					{this.startAudio;}.defer(5);//to give extra time
					}, {
						//when exits, signal routine
						updatingCondition.test = true;
						updatingCondition.signal;
						jackWasStartedBySC = false;
						"oscpipe: jack finished.".postln;
				});
		});
		jackPeriodSize = periodSize;

		// {"jack_load netmanager".unixCmd;}.defer(6); //load netmanager later - went to prParseJackOutput
	}

	prStopJack {
		"killall -9 jackd".unixCmd; //should be PID based....
		if("pidof jackd".unixCmdGetStdOut.size > 0, {
			"killall -9 jackdbus".unixCmd;
			"killall -9 jackd".unixCmd;
			if(jackWasStartedBySC, {
				updatingCondition.test = false;
				}, {
					1.wait;
			}); //pause routine only if jack was started by sc, and thus only if exit action can resume it; otherwise wait
			}, {
				"jack not running".postln;
		});
	}

	prParseJackOutput { arg line;
		var removedName;
		//put analysis of jack output here! - connecting for netjack etc
		//use switch on first 8 characters from the line
		switch(line.asString.copyRange(0, 7),
			"configur", {
				//when jack started up, signal routine
				"oscpipe: jack started up!".postln;
				jackWasStartedBySC = true;
				{
					updatingCondition.test = true;
					updatingCondition.signal;
				}.defer(1);// to give extra time

				//and start netmanager
				"jack_load netmanager".unixCmd;
			},
			"CoreAudi", { // for osx....
				//when jack started up, signal routine
				"oscpipe: jack started up!".postln;
				jackWasStartedBySC = true;
				{
					updatingCondition.test = true;
					updatingCondition.signal;
				}.defer(1);// to give extra time

				//and start netmanager
				"jack_load netmanager".unixCmd;
			},
			"Slave na", {
				"new slave name".postln;
				newJackName = line.asString.copyToEnd(13);
			},
			"Send cha", {
				"new slave inputs ('send')".postln;
				newJackIns = line.asString.copyToEnd(31).split($ )[0];
			},
			"Return c", {
				"new slave outputs ('return')".postln;
				newJackOuts = line.asString.copyToEnd(33).split($ )[0];
				this.prAddClient(newJackName, [newJackOuts, newJackIns]);
				// clientsDictionary.add(newJackName, [newJackOuts, newJackIns]);
			},
			"Exiting ", {
				removedName = line.asString.copyToEnd(8).replace("'", "");
				("Netjack client " ++ removedName ++ " removed.").postln;
				this.prRemoveClient(removedName);
		});
/*		(line.copyRange(0, 6) == "Name : ").if({
			newJackDetected = true;
			newJackName = line.copyToEnd(7)
		});*/
	}

	prAddClient {arg name, vals, isNetJackClient = true;
		clientsDictionary.put(name, vals);
		// this.changed(\clients, clientsDictionary.collect({|val, inc| val[0]}).asSortedArray /*[[name, outs], [name, outs]]*/);
		this.changed(\clients);
		if(isNetJackClient, {
			{
				this.prConnectNetJackClientToSC(name);
				this.prConnectAuxInputsToNetJackClient(name);
					}.defer(2);//to give time for establishing connectino?
		});

	}

	prRemoveClient {arg name;
		clientsDictionary.removeAt(name);
		// this.changed(\clients, clientsDictionary.collect({|val, inc| val[0]}).asSortedArray /*[[name, outs], [name, outs]]*/);
		this.changed(\clients);
	}

	prConnectNetJackClientToSC {arg clientName;
		var srcDestArray;
		//serverInputNameInJack
		// "clientsDictionary.at(clientName)[0]: ".post; clientsDictionary.at(clientName)[0].postln;
		"connecting new client to SC: ".post; clientName.postln;
		srcDestArray = clientsDictionary.at(clientName)[0].asInteger.collect({|inc| inc}) + 1; //since it's 1-based
		SCJConnection.connect(srcDestArray, srcDestArray, clientName ++ ":from_slave_", serverInputNameInJack);
	}

	prConnectAuxInputsToNetJackClient {arg clientName;
		var srcDestArray;
		//serverInputNameInJack
		"connecting inputs to new client: ".post; clientName.postln;
		srcDestArray = clientsDictionary.at(clientName)[1].asInteger.collect({|inc| inc}) + 1; //since it's 1-based
		SCJConnection.connect(srcDestArray, srcDestArray, "system:capture_", clientName ++ ":to_slave_");
	}

	//not needed anymore
/*	prDisconnectRedundantHardwareIns {arg channelArrayToDisconnect; //should be 0-based
		SCJConnection.disconnect(channelArrayToDisconnect, channelArrayToDisconnect, "system:capture_", serverInputNameInJack);
	}*/

	/*
	parse jack output tests
	"configuring for 96000Hz, per".copyRange(0, 7) "configur"
	"Slave name : dyferstation".copyRange(0, 7) "Slave na"
	"Send channels (audio - midi) : 32 - 0".copyRange(0, 7) "Send cha"
	"Return channels (audio - midi) : 32 - 0".copyRange(0, 7) "Return c"
	"Exiting 'dyferstation'".copyRange(0, 7) "Exiting "

	getting data:
	"Slave name : dyferstation".copyToEnd(13)
	"Send channels (audio - midi) : 32 - 0".copyToEnd(31).split($ )[0]
	"Return channels (audio - midi) : 32 - 0".copyToEnd(33).split($ )[0]
	"Exiting 'dyferstation'".copyToEnd(8).replace("'", "")
	*/

	prSetJackConnections {
		//connect only as many sc outputs as jack outputs
		var numChannelsPerMADI, inOffset, outOffset;
		if(sampleRate <= 48000, {
			numChannelsPerMADI = 64;
			}, {
				numChannelsPerMADI = 32;
		});
		if(whichMadiInput.isNil, {
			inOffset = 0
			}, {
				inOffset = (whichMadiInput * numChannelsPerMADI) + 2;
		});
		if(whichMadiOutput.isNil, {
			outOffset = 0
			}, {
				outOffset = (whichMadiOutput * numChannelsPerMADI) + 2;
		});

		"SC_JACK_DEFAULT_OUTPUTS".setenv("".ccatList(numHwOutChToConnectTo.collect({|inc| "system:playback_" ++ (inc + 1 + outOffset).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1));
		//fix audio in
/*		if((sampleRate > 48000) && fixAudioIn, {
			"SC_JACK_DEFAULT_INPUTS".setenv("".ccatList(16.collect({|inc| "system:capture_" ++ (inc + 1).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1)); //connect only 16 ins so rme input doesn't get into the decoder
		});*/ //this was needed for 117 with Presonus
		"SC_JACK_DEFAULT_INPUTS".setenv("".ccatList(numHwInChToConnectTo.collect({|inc| "system:capture_" ++ (inc + 1 + inOffset).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1))
	}

	prStartServer {
		updatingCondition.test = false;
		// server.boot;
		server.waitForBoot({
			//routine
			"server started".postln;
			updatingCondition.test = true;
			updatingCondition.signal;
		}, onFailure: {
				"addWaitTime <= 5: ".post; (addWaitTime <= 5).postln;
				if(addWaitTime <= 5, {
					addWaitTime = addWaitTime + 1; //increase waittimes
					this.startAudio;//and try again
					"Something went wrong! Trying to start again...".warn;
					this.changed(\message, "Restarting audio on initialisation error...");
					}, {
						"I've tried couple times, still can't boot the server, something's really wrong... check the configuration".error;
						this.changed(\message, "Error: Couldn't start audio. Check the configuration");
				});
				false;
		});
	}

	//set paramers for various samplerates - only when audio is NOT running
	prSetSR {arg sr = 48000; //valid: 44100, 48000, 88200, 96000
		var modeWord, srWord, modeByte, cmds, msgBack1, msgBack2;
		cmds = Array.new;
		("setting samplerate to " ++ sr).postln;
		switch(sr,
			44100, {
				modeWord = "'Single'";
				// ins = 64;
				// outs = 64;
				// outs = 32; //always 32, just in case - we don't have more converters anyway
				modeByte = 2r00000000;
				srWord = "'44.1 kHz'";
			},
			48000, {
				modeWord = "'Single'";
				// ins = 64;
				// outs = 64;
				// outs = 32; //always 32, just in case - we don't have more converters anyway
				modeByte = 2r00010000;
				srWord = "'48 kHz'";
			},
			88200, {
				modeWord = "'Double'";
				// ins = 32;
				// outs = 32;
				modeByte = 2r00000001;
				srWord = "'88.2 kHz'";
			},
			96000, {
				modeWord = "'Double'";//would be "'Quad'" for 176/192kHz
				// ins = 32;
				// outs = 32;
				modeByte = 2r00010001;
				srWord = "'96 kHz'";
			}
		);
		// set commands for card parameters
		// cmd1 = "amixer -c " ++ cardID.asString ++ " sset 'Internal Clock' " ++ srWord;
		cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'Clock Selection' " ++ "'Word Clock'");
		cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'Internal Clock' " ++ srWord);
		cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'MADI Speed Mode' " ++ modeWord);
		//add Clock Selection
		//add WC Single speed?
		if(modeWord.notNil,
			{
				updatingCondition.test = false;
				sampleRate = sr;
				// set sampleRate on RME
				this.prSendSysex(midiPort, [0x01, modeByte]);
				// 0.1.wait; // to stabilize - propbably not needed... it's destabilized for some time anyway
				// "before cmds".postln;
				// msgBack1 = cmd1.unixCmdGetStdOut; // set proper sample rate on madi
				// msgBack1.postln;
				// msgBack2 = cmd2.unixCmdGetStdOut; // set proper speed mode on madi
				// msgBack2.postln;
				if(cardNameIncludes.notNil, {
					cmds.postln;
					cmds.do(_.unixCmd);
				});//run commands only if card name provided -> assuming we're on linux
				// "after cmds".postln;
				// server params
				// server.options.numOutputBusChannels = outs;
				server.options.numOutputBusChannels = serverOuts; //hardcoded, so we have extra to use with jconvolver
				server.options.numInputBusChannels = serverIns;
				server.options.numAudioBusChannels = (serverIns + serverOuts) * 8;
				server.options.sampleRate = sampleRate;
				server.options.numWireBufs = 512; //to make metering possible with many channels
				server.options.memSize = 8192 * 16;

				//fireface here as well

				6.wait;//wait for clocks to get in sync - not sure if we need that much...
				this.setFfSampleRate(sampleRate);
				updatingCondition.test = true;
				updatingCondition.signal;
				this.changed(\sampleRate, sampleRate);
			},{
				"no proper sampleRate provided".warn;
			}
		);
	}

	prSampleRateIsCorrectOrNil {arg newSR;
		"newSR in prSampleRateIsCorrectOrNil: ".post; newSR.postln;
		if(newSR.notNil, {
			if([44100, 48000, 88200, 96000].includes(newSR), {
				"prSampleRateIsCorrectOrNil: true".postln;
				^true;
				}, {
					"prSampleRateIsCorrectOrNil: false".postln;
					^false;
			});
		}, {
			^true;
		});
	}

	//fireface
	initFireface {
		if(useFireface, {
			// this.clearFireface;
			"Starting ffado-dbus-server for Fireface".postln;
			dbusServerPid = "exec ffado-dbus-server".unixCmd({|msg|
				dbusServerPid = nil;
				"Dbus server finished".postln;
			}); //needs to be run each time fireface disconnects
			//so to be safe: record the pid of the process, and kill/restart it on each sampleRate change / audio restart, that way user can bring the device back if needed.
			//also, set autoSync shortly afterwards here, so we don't have to remember
			{this.setFfAutoSync(true)}.defer(2);
		});
	}

	clearFireface {
		if(dbusServerPid.notNil, {
			"killing pid ".post; dbusServerPid.postln;
			("killing pid " ++ dbusServerPid).postln;
			("kill -9 " ++ dbusServerPid).unixCmd;
		});
	}

	setFfMatrixGain {|inbus = 6 /*mic: 6-9*/, outbus = 12/*ADAT: 12-19(27)*/, gain = 1, post = true|
		var dbusCmd, gainRaw;
		if(useFireface, {
			gainRaw = gain * 16384;
			dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ firefaceID ++ "/Mixer/InputFaders org.ffado.Control.Element.MatrixMixer.setValue int32:" ++ outbus.asString ++ " int32:" ++ inbus.asString ++ " double:" ++ gainRaw.asString;
			// dbusCmd.postln;
			dbusCmd.unixCmd(postOutput: post);
		});
	}

	setDefaultFfRouting {
		var micRoutings;
		if(useFireface, {
			"Setting Fireface default routing".postln;
			micRoutings = [
				[6, 12],
				[7, 13],
				[8, 14],
				[9, 15],
				[0, 16], //line
				[1, 17],
				[2, 18],
				[3, 19]
			];
			26.do({|inInc|
				26.do({|outInc|
					if(micRoutings.any({|item| item == [inInc, outInc]}), {
						this.setFfMatrixGain(inInc, outInc, 1, false); //full gain for predefined routings
						}, {
							this.setFfMatrixGain(inInc, outInc, 0, false); //mute others
					});
				});
			});
		});
	}

	ffPhantom {|channel = 0/*0-3*/, state /*bool or 0-1*/|
		var phantomRawValue, rawValuesArray, dbusCmd;
		if(useFireface, {
			if(state.notNil, {
				"Setting Fireface phantom".postln;
				//store in the class
				phantomState[channel] = state.asBoolean;

				/*
				mic 7 on -> 65537
				mic 7 off -> 65536
				mic 8 on -> 131074
				mic 8 off -> 131072
				mic 9 on -> 262148
				mic 9 off -> 262144
				mic 10 on -> 524296
				mic 10 off -> 524288
				*/
				//method call sender=:1.88 -> dest=:1.89 serial=220138 path=/org/ffado/Control/DeviceManager/ ++ firefaceID ++ /Control/Phantom; interface=org.ffado.Control.Element.Discrete; member=setValue

				// int32 524296

				rawValuesArray = [
					[65536, 65537],
					[131072, 131074],
					[262144, 262148],
					[524288, 524296]
				];
				phantomRawValue = rawValuesArray[channel][state.asInteger];
				// phantomRawValue.postln;
				dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ firefaceID ++ "/Control/Phantom org.ffado.Control.Element.Discrete.setValue int32:" ++ phantomRawValue.asString;
				// dbusCmd.postln;
				dbusCmd.unixCmd(postOutput: false);
				^[channel, state];
				}, {
					^phantomState[channel];
			});
		});
	}

	recallFfPhantom {
		if(useFireface, {
			phantomState.do({|state, inc|
				if(state.notNil, {
					this.ffPhantom(inc, state);
				});
			});
		});
	}

	setFfAutoSync {|val = true|
		var dbusCmd;
		if(useFireface, {
			"Setting Fireface AutoSync".postln;
			//method call sender=:1.88 -> dest=:1.89 serial=291689 path=/org/ffado/Control/DeviceManager/000a35009caf3c69/Control/Clock_mode; interface=org.ffado.Control.Element.Discrete; member=setValue
			// int32 1
			dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ firefaceID ++ "/Control/Clock_mode org.ffado.Control.Element.Discrete.setValue int32:" ++ val.asInteger.asString;
			// dbusCmd.postln;
			dbusCmd.unixCmd(postOutput: false);
		});
	}

	setFfSampleRate {|sr = 48000|
		var dbusCmd;
		if(useFireface, {
			"Setting Fireface samplerate".postln;
			// cmd = "ffado-test SetSamplerate " ++ sr.asString;
			// cmd.unixCmd; //now using dbus
			dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ firefaceID ++ "/Control/sysclock_freq org.ffado.Control.Element.Discrete.setValue int32:" ++ sr.asInteger.asString;
			// dbusCmd.postln;
			dbusCmd.unixCmd(postOutput: false);
		});
	}
}