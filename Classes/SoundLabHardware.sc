/*
Class for managing 117 Sound Lab in Raitt hall, DXARTS, University of Washington, Seattle
Coded in 2012, 2013 by Marcin Pączkowski (marcinp@uw.edu)
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
//000a35002dd2c77a // 000a3500c1da0056 //old, dead 205
//000a35009caf3c69 // 117
*/

SoundLabHardware {
	var useSupernova, fixAudioInputGoingToTheDecoder, <>useFireface;
	var <midiDeviceName, <midiPortName, <cardNameIncludes, jackPath;
	var serverIns, serverOuts, numHwOutChToConnectTo, numHwInChToConnectTo;
	var firefaceID;
	var <whichMadiInput, <whichMadiOutput;
	var <whichMadiInputForStereo;
	var <stereoInputArrayOffset;
	var <stereoOutputArrayOffset;
	var <audioDeviceName;
	//end of copyArgs

	// var firstOutput = 66, firstInput = 66;//0 for 117, 64 for 205, at 96k!
	var <cardID, <sampleRate, /*<ins, <outs, */midiPort, <server;
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
	var <fireface;
	var ffPhantomState;
	var <maxStartTime = 20, <checkRoutine; //if audio did not start after this time, attempt starting again

	//remmeber to use update notification at some point!!!!

	//for lack of a better place, here is a method for finding PID of a given process (for linux and macos)
	*pidof {arg processName;
		var result;
		if(processName.notNil, {
			thisProcess.platform.name.switch(
				\linux, {
					result = (format("pidof \"%\"", processName).unixCmdGetStdOut);
				},
				\osx, {
					result = (format("pgrep \"%\"", processName).unixCmdGetStdOut);
				}
			)
		});
		if(result.size == 0, {result = nil}); //replace empty string with nil
		if(result.notNil, {
			^result.asInteger
		}, {
			^nil //returns nil if no pid found
		});
	}


	*new {arg useSupernova = false,
		fixAudioInputGoingToTheDecoder = true,
		useFireface = true,
		midiDeviceName = "External MIDI-MADIFXtest MIDI 1", // nil for no MIDI
		midiPortName = "External MIDI-MADIFXtest MIDI 1", // nil for no MIDI
		cardNameIncludes = "RME", // nil for OSX
		jackPath = "/usr/bin/jackd",
		serverIns = 32,
		serverOuts = 128,
		numHwOutChToConnectTo = 32,
		numHwInChToConnectTo = 32,
		firefaceID = "000a3500c1da0056",//117: 000a35009caf3c69, 205: 000a3500c1da0056
		whichMadiInput = 2, //not nil will assume 205 and turn on stereo in from fireface... Marcin 2016.02
		whichMadiOutput = 2,//nil for regular MADI, 0-2 for MADIFX
		whichMadiInputForStereo, //for permanent stereo in from Fireface (205)
		stereoInputArrayOffset, //stereo inputs from Fireface (205)
		stereoOutputArrayOffset, //stero outputs for permanent stereo in
		audioDeviceName;
		// ^super.newCopyArgs(shellCommand, receiveAction, exitAction, id).init(false);
		^super.newCopyArgs(
			useSupernova, fixAudioInputGoingToTheDecoder, useFireface,
			midiDeviceName, midiPortName, cardNameIncludes, jackPath,
			serverIns, serverOuts, numHwOutChToConnectTo, numHwInChToConnectTo,
			firefaceID,
			whichMadiInput, whichMadiOutput,
			whichMadiInputForStereo,
			stereoInputArrayOffset,
			stereoOutputArrayOffset,
			audioDeviceName
		).init;
	}

	*killJack {
		"killall -9 jackdbus".unixCmd;
		"killall -9 jackd".unixCmd;
	}

	init {//arg useSupernova, fixAudioInputGoingToTheDecoder;
		// midiPortName.postln;
		// useFireface.postln;
		// this.dump;
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
		// clientsDictionary = Dictionary.new; //not here
		//init hardware
		this.prGetCardID;
		// format("%: before midi", this.class.name).postln;
		this.prInitMIDI;
		this.changed(\updatingConfiguration, 1.0);

		format("%: init complete", this.class.name).postln;
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

				checkRoutine !? {checkRoutine.stop};
				checkRoutine = fork{
					maxStartTime.wait;
					postf("\n\n------\nChecking if server is up and running after % seconds:\n", maxStartTime);
					if(server.serverRunning.not, {
						this.audioIsRunning_(false);

						this.changed(\reportStatus,
							warn(format("Server is not running. Attempting to start again...", maxStartTime))
						);
						this.startAudio(newSR, periodSize, periodNum)
					}, {
						"Server is running.\n-----".postln;
					}
					)
				};

				this.changed(\updatingConfiguration, 0.0);
				// if(audioIsRunning, {
				//for now - do it always, just in case something was running before
				"stopping audio first...".postln;
				this.changed(\reportStatus, "Stopping audio first");

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
					newSR = 44100;
					"using default sample rate: 44100".postln;
				});
				this.changed(\reportStatus, "Setting new samplerate...");
				this.prSetSR(newSR);
				// "---- 3".postln;
				// 6.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				this.changed(\updatingConfiguration, 0.6);
				this.changed(\reportStatus, "Starting JACK...");
				// "---- 4".postln;
				this.prStartJack(periodSize, periodNum);
				// "---- after jack".postln;
				// 4.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				// "--before setting jack connections".postln;
				if(audioDeviceName.notNil,{
					//assumes using JackRouter
					server.options.device_(audioDeviceName);
					this.setJackRouterInsOuts(serverIns, serverOuts);
				}, {
					//assumes using JACK directly
					this.prSetJackConnections;
				});
				//Fireface - phantom and Routing
				// this.setFfDefaultRouting; //automatically inside fireface class
				// this.recallFfPhantom;
				//set number of channels for JackRouter on macos
				this.changed(\updatingConfiguration, 0.9);
				this.changed(\reportStatus, "Booting the server...");
				// "--before starting server".postln;
				this.prStartServer;
				// 2.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				this.changed(\reportStatus, "Server booted.");
				this.audioIsRunning_(true);
				addWaitTime = 0;
				// clientsDictionary = Dictionary.new;
				// fix for not getting analog ins in the decoder - got moved into separate method, uses env vars now
				/*				if((sampleRate > 48000) && fixAudioIn, {
				this.prDisconnectRedundantHardwareIns((16..32)); //keeping as is for now
				//this
				//"SC_JACK_DEFAULT_OUTPUTS".setenv("".ccatList(16.collect({|inc| "system:playback_" ++ (inc + 1).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1));
				//not
				//would make SC connect only to the first 16 outputs (needs appro
				});*/
				// clientsDictionary.add("PreSonus", [ins * 0.5, 0]);
				// this.prAddClient("PreSonus", [32 * 0.5, 32 * 0.5], false); //ins, because outs is fake 32 for 48k
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


	stopAudio {|force = false|
		var stopFunc = {
			if(startRoutine.isPlaying, {
				startRoutine.stop;
			});
			if(force.not, {1.wait});
			this.prStopAudioFunction(force);
		};
		if(force, {
			stopFunc.value;
		}, {
			Routine.run(stopFunc);
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

	prStopAudioFunction {|force = false|
		updatingCondition.test = true;
		this.changed(\updatingConfiguration, 0.0);
		this.changed(\stoppingAudio);
		server.quit;
		// 3.wait;
		// updatingCondition.wait;
		//clear Fireface
		this.clearFireface;
		//jack
		this.changed(\updatingConfiguration, 0.6);
		this.audioIsRunning_(false);//moved here so jack knows we're stopping
		this.prStopJack;
		// 1.wait;
		if(force.not, {
			updatingCondition.wait;
			1.wait;//just to make sure everything's off
		});
		this.changed(\updatingConfiguration, 1.0);
		// this.changed(\audioIsRunning, false);
	}


	//get alsa card number
	prGetCardID {/* arg cardNameIncludes = "RME";*/
		//get card ID
		var p, l, extractedID;
		"getting cardID...".postln;
		postf("cardNameIncludes: %\n", cardNameIncludes);
		if(cardNameIncludes.notNil, {
			thisProcess.platform.name.switch(
				\linux, {
					if(cardNameIncludes.isKindOf(SimpleNumber), {
						cardID = cardNameIncludes;
					}, {
						// thisProcess.platform.name.postln;

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
									// l.postln; "cardID: ".post; cardID.postln;
								});
							});
							l = p.getLine;
						});    // run until l = nil
						p.close; // close the pipe
					})
				},
				\osx, {
					var tempName = this.hash.asString; //this is for starting JACK (for finding the device name) with a random name, so it works even if another JACK server is running
					//on osx we use jack to list devices
					p = Pipe.new(format("% -n % -r -d coreaudio -l", jackPath, tempName), "r");
					l = p.getLine;
					while({l.notNil}, {
						var thisString, nameIndex;
						// l.postln;
						if(l.contains(cardNameIncludes), {

							//doesn't work
							// thisString = l.split($,).first;
							// nameIndex = thisString.find("name = ");
							// cardID = thisString.copyToEnd(nameIndex).split($=).last.copyToEnd(1).replace("'", "");
							//we need this id instead:
							nameIndex = l.findAll("name = ").last;
							cardID = l.copyToEnd(nameIndex).split($=).last.split($')[1];

							// l.postln; "cardID: ".post; cardID.postln;
						});
						l = p.getLine;
					});    // run until l = nil
					p.close; // close the pipe
				},
				{Error(format("%: only linux and osx platforms are supported", this.class.name)).throw}
			);
		}, {
			cardID = 0;
		});
		postf("cardID: %\n", cardID);
	}

	prInitMIDI {
		// init MIDI
		if(midiPortName.notNil && midiDeviceName.notNil, {
			MIDIClient.init;
			// "after MIDI init".postln;
			// "midiDeviceName: ".post; midiDeviceName.postln;
			// "midiPortName: ".post; midiPortName.postln;
			// MIDIClient.destinations;
			try { midiPort = MIDIOut.newByName(midiDeviceName, midiPortName)};
			// postf("midiPort: %\n", midiPort);
			if(thisProcess.platform.name == \linux, {
				// MIDI INIT!!!!!! don't forget to connect....
				try { midiPort.connect(midiPort.port); };
			});
			midiPort !? {midiPort.latency_(0)};
		});
		postf("midiPort: %\n", midiPort);
	}

	prSendSysex { arg midiDevice, data; // for DA-32 and DA-16
		var sysexHeader, manufacturerID, modelID, bankOrDeviceID, messageType, eof;
		var sysexCommand;
		if(midiDevice.notNil, {
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
			// "sending SysEx: ".post; sysexCommand.postln;
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
		thisProcess.platform.name.switch(
			\linux, {cmd = cmd ++ " -dalsa -H"},// -dhw:"++cardID.asString;
			\osx, {cmd = cmd ++ " -dcoreaudio"},
			{(this.class.name ++ ": error in prStartJack - only linux and macOS is supported").warn}
		);
		// if(cardNameIncludes.notNil, {
		// 	cmd = cmd ++ " -dalsa -H -dhw:"++cardID.asString; //assuming linux
		// 	}, {
		// 		cmd = cmd ++ " -dcoreaudio"; //assuming osx
		// });
		if(cardNameIncludes.notNil, {
			thisProcess.platform.name.switch(
				\linux, {cmd = cmd ++ " -dhw:"++cardID.asString},// -dhw:"++cardID.asString;
				\osx, {cmd = cmd ++ " -d"++cardID.asString;},
				{(this.class.name ++ ": error in prStartJack - only linux and macOS is supported").warn}
			)
		});
		cmd = cmd ++ " -r"++sampleRate.asString++
		" -p"++periodSize.asString++
		// " -n"++periodNum.asString++
		" -D";//++
		if(thisProcess.platform.name == \linux, {
			cmd = cmd ++ " -n"++periodNum.asString; //numperiods only valid on ALSA (linux)
		});
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
		"killall -9 jackdmp".unixCmd; //should be PID based....
		if(SoundLabHardware.pidof("jackd").notNil, {
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
		"SC_JACK_DEFAULT_INPUTS".setenv("".ccatList(numHwInChToConnectTo.collect({|inc| "system:capture_" ++ (inc + 1 + inOffset).asString})).replace(" ", "").replace("[", "").replace("]", "").drop(1));

		//stereo in from Fireface
		if(whichMadiInputForStereo.notNil, { //assuming 205
			this.prConnectInToOutInJack((whichMadiInputForStereo * numChannelsPerMADI) + 2 + stereoInputArrayOffset, (whichMadiOutput * numChannelsPerMADI) + 2 + stereoOutputArrayOffset)
		});
	}

	prJackConnect {|input = "system:capture_1", output = "system:playback_1"|
		("jack_connect" + input + output).unixCmd;
	}

	prJackDisonnect {|input = "system:capture_1", output = "system:playback_1"|
		("jack_disconnect" + input + output).unixCmd;
	}

	prConnectInToOutInJack {|inArray = ([0]), outArray = ([0]), inName = "system:capture_", outName = "system:playback_"| //use 0-based in/out numbers
		outArray = outArray.wrapExtend(inArray.size);
		inArray.do({|thisIn, inc|
			this.prJackConnect(inName ++ (thisIn + 1).asString, outName ++ (outArray[inc] + 1).asString)
		});
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
	prSetSR {arg sr = 44100; //valid: 44100, 48000, 88200, 96000
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
		if(thisProcess.platform.name == \linux, {
			cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'Clock Selection' " ++ "'Word Clock'");
			cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'Internal Clock' " ++ srWord);
			cmds = cmds.add("amixer -c " ++ cardID.asString ++ " sset 'MADI Speed Mode' " ++ modeWord);
		});
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
				if(cmds.size > 0, {
					// cmds.postln;
					cmds.do(_.unixCmd(postOutput: false));
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

				1.wait;//wait for clocks to get in sync - not sure if we need that much...
				// this.setFfSampleRate(sampleRate);
				if(useFireface, {this.fireface.sampleRate_(sampleRate);});
				updatingCondition.test = true;
				updatingCondition.signal;
				this.changed(\sampleRate, sampleRate);
			},{
				"no proper sampleRate provided".warn;
			}
		);
	}

	prSampleRateIsCorrectOrNil {arg newSR;
		// "newSR in prSampleRateIsCorrectOrNil: ".post; newSR.postln;
		if(newSR.notNil, {
			if([44100, 48000, 88200, 96000].includes(newSR), {
				// "prSampleRateIsCorrectOrNil: true".postln;
				^true;
			}, {
				// "prSampleRateIsCorrectOrNil: false".postln;
				^false;
			});
		}, {
			^true;
		});
	}

	//fireface
	initFireface {
		if(useFireface, {
			// fireface = Fireface.new(firefaceID, ffPhantomState)
			fireface = Fireface.new(firefaceID) //not recalling phantom
		});
	}

	clearFireface {
		if(useFireface, {
			// if(fireface.notNil, {
			// 	ffPhantomState = this.fireface.phantomState;
			// }); //not recalling phantom
			this.fireface.clear;
		});
	}

	ffPhantom_ {|channel = 0/*0-3*/, state /*bool or 0-1*/|
		if(useFireface, {
			fireface.phantom_(channel, state);
		});
	}

	ffPhantom {
		if(useFireface, {
			^this.fireface.phantomState;
		});
	}

	//JackRouter - for macOS
	setJackRouterInsOuts {arg numIns, numOuts;
		if(thisProcess.platform.name == \osx, {
			var configStr, configArr;
			var jackRouterPath = "~/Library/Preferences/JAS.jpil".standardizePath;
			File.use(jackRouterPath, "r", {|file| configStr = file.readAllString});
			// "input string: ".post; configStr.postln;
			configArr = configStr.split($\t);
			configArr[1] = numIns.asString;
			configArr[3] = numOuts.asString;
			configStr = "".catList(configArr.collect({|item| "\t " ++ item}));
			// "output string: ".post; configStr.postln;
			//save here
			File.use(jackRouterPath, "w", {|file|
				file.write(configStr);
				format("JackRouter configuration changed to % ins and % outs", numIns, numOuts).postln;
			});
		}, {
			"configuring JackRouter is supported only on macOS".warn;
		});
	}

}

//putting Fireface methods to its own class

//TODO: add methods for setting front/back inputs, possibly level?

Fireface {
	var <id, <phantomState;
	var <settingsAppPid, <autoSync = true;
	var <pollingRoutine, <lastUnixCmdTime = 0, <>dbusServerAliveTime = 60;
	var <lastUpdateTime;
	var <active = false;
	var <starting = false;
	var <messageQueue;
	var scriptPath; //for applescript
	var firefaceSettingsPath = "/Applications/Fireface Settings.app/Contents/MacOS/Fireface Settings";

	*new {|id = "000a3500c1da0056", phantomState|
		^super.newCopyArgs(id, phantomState).init;
	}

	init { //called when class is initialized
		var settingsAppName;
		phantomState ?? {phantomState = 4.collect({false})};
		messageQueue = List.new;
		if(this.active.not, {
			"initializint fireface".postln;
			// this.prStartFireface;
			// starting = true;
			thisProcess.platform.name.switch(
				\linux, {
					this.prStartDbusServer;
				},
				\osx, {
					scriptPath = File.realpath(this.class.filenameSymbol).dirname.dirname +/+ "applescript";
					this.prStartFirefaceSettings;
				}
			);
			{this.prInitDefaults}.defer(4);
		}, {
			thisProcess.platform.name.switch(
				\linux, {
					"Fireface seems already initialized, NOT starting DBus server".postln;
				},
				\osx, {
					"Fireface seems already initialized, NOT starting Fireface Settings".postln;
				}
			);
		});
	}

	reinit { //called when settings are being set after the dbus server has been shut down due to inactivity
		if(this.active.not, {
			// "restarting fireface".postln;
			starting = true;
			this.prStartDbusServer;
			{
				// this.sendQueuedMessages;
				this.prInitDefaults; //this sends queued messages as well
			}.defer(1); //how's that delay? enough?
		}, {
			"Fireface dbus server seems already running, NOT initializing".postln;
		});

	}

	// prStartDbusServer {
	// 	"Starting ffado-dbus-server for Fireface".postln;
	// 	starting = true;
	// 	settingsAppPid = "exec ffado-dbus-server".unixCmd({|msg|
	// 		settingsAppPid = nil;
	// 		"Dbus server finished".postln;
	// 	}); //needs to be run each time fireface disconnects
	// 	// settingsAppPid = "exec ffado-dbus-server".unixCmdGetStdOutThruOsc({|line|
	// 	// 	"from dbus server: ".post; line.postln;
	// 	// 	}, {|msg|
	// 	// 		settingsAppPid = nil;
	// 	// 		"Dbus server finished".postln;
	// 	// });
	// 	this.prStartPolling;//start with the server
	// }

	prStartDbusServer {
		"Starting ffado-dbus-server for Fireface".postln;
		starting = true;
		settingsAppPid = "exec ffado-mixer".unixCmd({|msg| //use ffado mixer
			settingsAppPid = nil;
			"Dbus server finished".postln;
		}); //needs to be run each time fireface disconnects
		// settingsAppPid = "exec ffado-dbus-server".unixCmdGetStdOutThruOsc({|line|
		// 	"from dbus server: ".post; line.postln;
		// 	}, {|msg|
		// 		settingsAppPid = nil;
		// 		"Dbus server finished".postln;
		// });
		// this.prStartPolling;//start with the server
	}

	prStartFirefaceSettings { //on osx only
		"Starting Fireface Settings".postln;
		starting = true;
		settingsAppPid = SoundLabHardware.pidof(PathName(firefaceSettingsPath).fileName); // check if the fireface app is running
		// "settingsAppPid: ".post; settingsAppPid.postln;
		settingsAppPid !? {
			// kill it in order to start a new one from SC and be able to know when it finishes
			"killing Fireface Settings".postln;
			thisProcess.platform.killProcessByID(settingsAppPid);
			settingsAppPid = nil;
		};
		settingsAppPid = format("exec \"%\"", firefaceSettingsPath).unixCmd({|msg|
			settingsAppPid = nil;
			"Fireface Settings finished".postln;
		}); //needs to be run each time fireface disconnects
	}

	prInitDefaults{
		"initializing defaults".postln;
		// {
		this.autoSync_(true);
		this.setDefaultRouting;
		this.setDefaultSources;
		this.recallPhantom;
		// this.prStartPolling; //moved to prStartDbusServer
		active = true;
		starting = false;
		thisProcess.platform.name.switch(
			\linux, {
				this.sendQueuedMessages;
			}
		);
		// }.defer(2); //more time?
	}

	sendQueuedMessages{ //this method used on linux only
		"Fireface: sending queued messages".postln;
		active = true;
		starting = false;
		Routine.run({
			// "in routine".postln;
			while({messageQueue.size > 0}, {
				var path, interface, member, value, post, synchronous, updateLastCmdTime;
				// "in while loop".postln;
				// "messageQueue.size: ".post; messageQueue.size.postln;
				// "messageQueue[0]: ".post; messageQueue[0].postln;
				// #path, interface, member, value, post, synchronous, updateLastCmdTime = messageQueue[0];
				#path, interface, member, value, post, synchronous, updateLastCmdTime = messageQueue.pop; //changed because of erratic errors with removeAt(0)
				// "args to be send: ".post; [path, interface, member, value, post, synchronous, updateLastCmdTime].postln;
				// if([path, interface, member, value, post, synchronous, updateLastCmdTime].includes(nil).not, {
				this.prSendDBus(path, interface, member, value, post, synchronous, updateLastCmdTime);
				0.05.wait;
			});
			"Fireface: all queued messages sent".postln;
		});
	}

	// isActive {
	// 	if(settingsAppPid.notNil, {
	// 		^true;
	// 		}, {
	// 			^false;
	// 	});
	// }

	clear { //clear and stop routine
		this.prClear;
		pollingRoutine.stop;
	}

	prClear { //clear, don't stop the routine (to be used by the routine)

		active = false;
		"clearing fireface".postln;
		// "settingsAppPid: ".post; settingsAppPid.postln;
		// "pollingRoutine: ".postln;
		// pollingRoutine.dump;
		// pollingRoutine.stop;
		// pollingRoutine.isPlaying.postln;
		// "settingsAppPid: ".post; settingsAppPid.postln;
		if(settingsAppPid.notNil, {
			"killing pid ".post; settingsAppPid.postln;
			// ("killing pid " ++ settingsAppPid).postln;
			("kill -9 " ++ settingsAppPid).unixCmd;
		});
	}

	setMatrixGain {|inbus = 6 /*mic: 6-9*/, outbus = 12/*ADAT: 12-19(27)*/, gain = 1, post = true|
		thisProcess.platform.name.switch(
			\osx, {
				// not yet implemented
			},
			\linux, {
				var dbusCmd, gainRaw;
				gainRaw = gain * 16384;
				// dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/Mixer/InputFaders org.ffado.Control.Element.MatrixMixer.setValue int32:" ++ outbus.asString ++ " int32:" ++ inbus.asString ++ " double:" ++ gainRaw.asString;
				this.sendDBus("Mixer/InputFaders", "MatrixMixer", "setValue", "int32:" ++ outbus.asString ++ " int32:" ++ inbus.asString ++ " double:" ++ gainRaw.asString, post);
				// dbusCmd.postln;
				// dbusCmd.unixCmd(postOutput: post);
				// this.prUnixCmdFfActive(dbusCmd, post: post);
			}
		)
	}

	setDefaultRouting {
		var micRoutings;
		"Setting Fireface default routing".postln;
		micRoutings = [
			[6, 12], //mic
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
					this.setMatrixGain(inInc, outInc, 1, false); //full gain for predefined routings
				}, {
					this.setMatrixGain(inInc, outInc, 0, false); //mute others
				});
			});
		});
	}

	phantom_ {|channel = 0/*0-3*/, state /*bool or 0-1*/|
		thisProcess.platform.name.switch(
			\osx, {
				var cmd;
				state = state.asBoolean.asString;
				channel = channel + 7; //the script expects the channel number consistent with Fireface input number
				cmd = format("osascript % % %", scriptPath +/+ "ff800_setPhantom.scpt", channel, state);
				// cmd.postln;
				cmd.unixCmd;
			},
			\linux, {
				var phantomRawValue, rawValuesArray, dbusCmd;
				// if(state.notNil, {
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
				//method call sender=:1.88 -> dest=:1.89 serial=220138 path=/org/ffado/Control/DeviceManager/ ++ id ++ /Control/Phantom; interface=org.ffado.Control.Element.Discrete; member=setValue

				// int32 524296

				rawValuesArray = [
					[65536, 65537],
					[131072, 131074],
					[262144, 262148],
					[524288, 524296]
				];
				phantomRawValue = rawValuesArray[channel][state.asInteger];
				// phantomRawValue.postln;
				// dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/Control/Phantom org.ffado.Control.Element.Discrete.setValue int32:" ++ phantomRawValue.asString;
				// dbusCmd.postln;
				// dbusCmd.unixCmd(postOutput: false);
				this.sendDBus("Control/Phantom", "Discrete", "setValue", "int32:" ++ phantomRawValue.asString);
				// ^[channel, state];
				// }, {
				// ^phantomState[channel];
				// });
			}
		)
	}

	phantom {|channel = 0/*0-3*/|
		if(channel.notNil, {
			^phantomState[channel];
		}, {
			^phantomState
		});
	}

	recallPhantom {
		phantomState.do({|state, inc|
			if(state.notNil, {
				this.phantom_(inc, state);
			});
		});
	}

	autoSync_ {|val = true|
		thisProcess.platform.name.switch(
			\osx, {
				// not yet implemented
			},
			\linux, {
				var dbusCmd;
				"Setting Fireface AutoSync".postln;
				//method call sender=:1.88 -> dest=:1.89 serial=291689 path=/org/ffado/Control/DeviceManager/000a35009caf3c69/Control/Clock_mode; interface=org.ffado.Control.Element.Discrete; member=setValue
				// int32 1
				// dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/Control/Clock_mode org.ffado.Control.Element.Discrete.setValue int32:" ++ val.asInteger.asString;
				// dbusCmd.postln;
				// dbusCmd.unixCmd(postOutput: false);

				this.sendDBus("Control/Clock_mode", "Discrete", "setValue", "int32:" ++ val.asInteger.asString);
				autoSync = val;
			}
		)
	}

	sampleRate_{|sr = 44100|
		thisProcess.platform.name.switch(
			\osx, {
				var cmd = format("osascript % %", scriptPath +/+ "ff800_setSR.scpt", sr.asInteger);
				// cmd.postln;
				cmd.unixCmd;
			},
			\linux, {
				var inc = 0;
				Routine.run({
					while({active.not && (inc < 10)}, { //
						"fireface sampleRate_: waiting for activation".postln;
						2.wait;
						inc = inc + 1;
					});
					if(autoSync && active, {
						"setting fireface samplerate".postln;
						//NOTE: to properly switch samplerate from "single speed" to "double speed" (48k -> 96k), switch to clock master first, then swtich sample rate, then switch back to autosync
						this.autoSync_(false);//set to master to enforce proper single speed/double speed selection
						0.5.wait;
						this.prSampleRate_(sr);
						0.5.wait;
						this.autoSync_(true); //back to proper autosync
					},{
						this.prSampleRate_(sr); //set right away if it's in master mode
					});
				});
			}
		)
	}

	prSampleRate_ {|sr = 44100| //this method used on linux only
		var dbusCmd, sampleRateNumber;
		"Setting Fireface samplerate".postln;
		// cmd = "ffado-test SetSamplerate " ++ sr.asString;
		// cmd.unixCmd; //now using dbus
		// dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/Control/sysclock_freq org.ffado.Control.Element.Discrete.setValue int32:" ++ sr.asInteger.asString;
		// dbusCmd.postln;
		// dbusCmd.unixCmd(postOutput: false);
		sampleRateNumber = [32000, 44100, 48000, 64000, 88200, 96000, 128000, 176400, 192000].indexOf(sr);
		// "sampleRateNumber: ".post; sampleRateNumber.postln;
		if(sampleRateNumber.notNil, {
			this.sendDBus("Control/sysclock_freq", "Discrete", "setValue", "int32:" ++ sr.asInteger.asString);
			this.sendDBus("Generic/SamplerateSelect", "Enum", "select", "int32:" ++ sampleRateNumber.asInteger.asString);
		}, {
			"Fireface: no proper sampleRate provided, valid are: [32000, 44100, 48000, 64000, 88200, 96000, 128000, 176400, 192000]".warn;
		});
	}

	inputSource_ {|channel = 0/*0, 6, 7*/, source = 'front' /*'front', 'rear', 'front+rear'*/| //note 0-based numbering; also inconsistent with phantom ch number...
		thisProcess.platform.name.switch(
			\osx, {
				var cmd = format("osascript % % %", scriptPath +/+ "ff800_setInputSource.scpt", (channel + 1).asInteger, source);
				// cmd.postln;
				cmd.unixCmd;
			},
			\linux, {
				var dbusCmd, chanName, sourceNumber;
				channel.switch(
					0, {chanName = "Chan1_source"},
					6, {chanName = "Chan7_source"},
					7, {chanName = "Chan8_source"},
					{"Wrong channel number, possible are 0, 6, 7".warn}
				);
				source.switch(
					'front', {sourceNumber = 0},
					'rear', {sourceNumber = 1},
					'front+rear', {sourceNumber = 2},
					{"Wrong source, possible are 'front', 'rear', 'front+rear'".warn}
				);
				if(chanName.notNil && sourceNumber.notNil, {
					// "Setting input source".postln;
					this.sendDBus("Control/" ++ chanName, "Discrete", "setValue", "int32:" ++ sourceNumber.asInteger.asString);
				});
			}
		)
	}

	setDefaultSources { //0 - rear, 6,7 - front
		[[0, 'rear'], [6, 'front'], [7, 'front']].do({|thisInput|
			this.inputSource_(thisInput[0], thisInput[1])
		});
	}


	//also uise this to detect when the interface gets disconnected and close/restart dbus server

	/*	getStatus {|path = \SamplerateSelect, interface = \Element, member = \canChangeValue, value, post = true| /*these can be Symbols or Strings; value needs to be in the format int32:0*/
	var dbusCmd;
	if(post, {"Getting Fireface status".postln;});
	dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/Generic/" ++ path.asString ++ " org.ffado.Control.Element." ++ interface.asString ++ "." ++ member.asString;
	value !? {dbusCmd = dbusCmd + value};
	// dbusCmd.postln;
	dbusCmd.unixCmd(postOutput: post);
	}*/

	//this method used on linux only
	sendDBus {|path = "Generic/SamplerateSelect", interface = "MatrixMixer", member = "setValue", value, post = false, synchronous = false, updateLastCmdTime = true| /*path, interface, member - these can be Symbols or Strings; value needs to be in the format 'int32:0 int32:1' etc; synchronous will return (synchronously) value from the command; updateLastCmdTime should be set to true for all messages except continuous polling*/
		//eventually add queuing here
		if(active, {
			this.prSendDBus(path, interface, member, value, post, synchronous, updateLastCmdTime);
		}, {
			// messageQueue.add([path, interface, member, value, post, synchronous, updateLastCmdTime]);
			messageQueue.addFirst([path, interface, member, value, post, synchronous, updateLastCmdTime]); //changed to include in front and remove from the end because of erratic errors...
			if(starting.not, {
				this.reinit;
			});
		});
	}

	//this method used on linux only
	prSendDBus {|path = "Generic/SamplerateSelect", interface = "MatrixMixer", member = "setValue", value, post = false, synchronous = false, updateLastCmdTime = true|
		var dbusCmd;
		dbusCmd = "dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/" ++ id ++ "/" ++ path.asString ++ " org.ffado.Control.Element." ++ interface.asString ++ "." ++ member.asString;
		value !? {dbusCmd = dbusCmd + value};
		if(updateLastCmdTime, {lastUnixCmdTime = thisThread.seconds});
		if(post, {"Sending DBus command: ".post; dbusCmd.postln;}); //get rid of that eventually
		if(synchronous, {
			^dbusCmd.systemCmd; //this returns exic code synchronously
		}, {
			^dbusCmd.unixCmd(postOutput: post);
		});
	}

	//for keeping dbus server alive, we need to polll its status continuously (?)
	//following methods are used for that
	//example
	//dbus-send --print-reply --dest=org.ffado.Control /org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/SamplerateSelect org.ffado.Control.Element.Element.canChangeValue
	//this method used on linux only
	prStartPolling {
		fork{
			pollingRoutine !? {pollingRoutine.stop}; //just in case
			5.wait; //needed, so we don't shut down too quickly
			pollingRoutine = Routine.run({
				var frequency = 1; //Hz - 1Hz should be enough
				var inc = 0;
				// inf.do({|inc|
				while({(thisThread.seconds - lastUnixCmdTime) < dbusServerAliveTime}, {
					if((inc % (frequency * 60)) == 0, { //post every minute
						"------".postln;
						Date.getDate.postln;
						"Continuously polling from ffado-dbus-server".postln;
						"Frequency: ".post; frequency.post; "Hz".postln;
						"Iteration: ".post; inc.postln;
						"time since dbus server start: ".post; (thisThread.seconds - lastUnixCmdTime).round(1).postln;
						"------".postln;
					});
					inc = inc + 1;//manual increment
					lastUpdateTime = Date.getDate;
					//query the backend
					[
						["Generic/SamplerateSelect", "Element", "canChangeValue"],
						["Generic/ClockSelect", "Element", "canChangeValue"],
						["Generic/Nickname", "Element", "canChangeValue"],
						["Generic/StreamingStatus", "Enum", "selected"],
						["Generic/StreamingStatus", "Enum", "getEnumLabel", "int32:0"],
						["Generic/SamplerateSelect", "Enum", "count"],
						["Generic/SamplerateSelect", "Enum", "getEnumLabel", "int32:0"],
					].do({|arr|
						// arr.postln;
						this.sendDBus(arr[0], arr[1], arr[2], arr[3], false, updateLastCmdTime: false);
					});
					// if((thisThread.seconds - lastUnixCmdTime) > dbusServerAliveTime, {
					// 	"routine timeout, clearing".postln;
					// 	this.clear;
					// 	pollingRoutine.stop;
					// 	}, {
					// 		frequency.reciprocal.wait;
					// })
					frequency.reciprocal.wait;
				});
				"routine timeout, clearing".postln;
				this.prClear;
			});
		};
	}
}
//checking ffado-dbus-server status - continuous polling from a regular ffado mixer frontend
//dump:
/*
method call sender=:1.761 -> dest=:1.762 serial=117851 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/SamplerateSelect; interface=org.ffado.Control.Element.Element; member=canChangeValue
method return sender=:1.762 -> dest=:1.761 reply_serial=117851
boolean true
method call sender=:1.761 -> dest=:1.762 serial=117852 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/ClockSelect; interface=org.ffado.Control.Element.Element; member=canChangeValue
method return sender=:1.762 -> dest=:1.761 reply_serial=117852
boolean true
method call sender=:1.761 -> dest=:1.762 serial=117853 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/Nickname; interface=org.ffado.Control.Element.Element; member=canChangeValue
method return sender=:1.762 -> dest=:1.761 reply_serial=117853
boolean false
method call sender=:1.761 -> dest=:1.762 serial=117854 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/StreamingStatus; interface=org.ffado.Control.Element.Enum; member=selected
method return sender=:1.762 -> dest=:1.761 reply_serial=117854
int32 0
method call sender=:1.761 -> dest=:1.762 serial=117855 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/StreamingStatus; interface=org.ffado.Control.Element.Enum; member=getEnumLabel
int32 0
method return sender=:1.762 -> dest=:1.761 reply_serial=117855
string "Idle"
method call sender=:1.761 -> dest=:1.762 serial=117856 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/SamplerateSelect; interface=org.ffado.Control.Element.Enum; member=count
method return sender=:1.762 -> dest=:1.761 reply_serial=117856
int32 1
method call sender=:1.761 -> dest=:1.762 serial=117857 path=/org/ffado/Control/DeviceManager/000a3500c1da0056/Generic/SamplerateSelect; interface=org.ffado.Control.Element.Enum; member=getEnumLabel
int32 0
method return sender=:1.762 -> dest=:1.761 reply_serial=117857
string "48000"

*/