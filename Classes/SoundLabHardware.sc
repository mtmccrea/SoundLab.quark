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
*/

/*
Changelog....?

2013.01.14
-added sampleRate method

2013.01.11
-added Condition
-updated notifications (.changed)
*/

SoundLabHardware {
	var <midiPortName = "External MIDI- MIDI 1", <cardNameIncludes = "RME";
	var cardID, sampleRate, <ins, <outs, midiPort, <server;
	// var getCardID, setSR, initMIDI, sendSysex, startJack, stopJack, initAll, prStartServer;
	var <audioIsRunning, parametersSetting;//bools for status
	var updatingCondition, isUpdating;
	var startRoutine;
	var addWaitTime;
	var jackWasStartedBySC;
	var <clientsDictionary; //name -> [outs, ins]
	var newJackName, newJackIns, newJackOuts;
	var serverInputNameInJack;
	var fixAudioIn;

	//remmeber to use update notification at some point!!!!

	*new {arg useSupernova = true, fixAudioInputGoingToTheDecoder = true;
		// ^super.newCopyArgs(shellCommand, receiveAction, exitAction, id).init(false);
		^super.new.init(useSupernova, fixAudioInputGoingToTheDecoder);
	}

	init {arg useSupernova, fixAudioInputGoingToTheDecoder;
		//notification
		this.changed(\updatingConfiguration, 0.0);
		//set default synth
		if(useSupernova, {
			Server.supernova;
			serverInputNameInJack = "supernova:input_";
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
		this.prInitMIDI;
		this.changed(\updatingConfiguration, 1.0);
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
				this.changed(\updatingConfiguration, 0.9);
				// "--before starting server".postln;
				this.prStartServer;
				// 2.wait;
				updatingCondition.wait;
				addWaitTime.wait;
				this.audioIsRunning_(true);
				addWaitTime = 0;
				clientsDictionary = Dictionary.new;
				// fix for not getting analog ins in the decoder
				if((sampleRate > 48000) && fixAudioIn, {
					this.prDisconnectRedundantHardwareIns((16..32));
				});
				// clientsDictionary.add("PreSonus", [ins * 0.5, 0]);
				this.prAddClient("PreSonus", [ins * 0.5, ins * 0.5], false); //ins, because outs is fake 32 for 48k
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
		if(startRoutine.isPlaying, {
			startRoutine.stop;
		});
		Routine.run({
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
		p = Pipe.new("cat /proc/asound/cards", "r");
		l = p.getLine;
		while({l.notNil}, {
			//l.postln;
			if(l.contains(cardNameIncludes), {
				extractedID = l.split($ )[1];
				//"l.split($ ): ".post; l.split($ ).postln;
				//extractedID.postln;
				if(extractedID.size > 0, { //use only lines, where there is something as the second argument
					cardID = extractedID.asInteger;
					l.postln; "cardID: ".post; cardID.postln;
				});
			});
			l = p.getLine;
		});    // run until l = nil
		p.close; // close the pipe
	}

	prInitMIDI {
		// init MIDI
		MIDIClient.init;
		// MIDIClient.destinations;
		// MIDI INIT!!!!!! don't forget to connect.... blah
		midiPort = MIDIOut.newByName(midiPortName, midiPortName);
		midiPort.connect(midiPort.port);
	}

	prSendSysex { arg midiDevice, data; // for DA-32
		var sysexHeader, manufacturerID, modelID, bankOrDeviceID, messageType, eof;
		var sysexCommand;
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
	}
	//setting clock on RME - trial and error...
	/*
	sendSysex.value(m, [0x01, 2r00000000]);//44.1k
	sendSysex.value(m, [0x01, 2r00010000]);//48k
	sendSysex.value(m, [0x01, 2r00000001]);//88.2k
	sendSysex.value(m, [0x01, 2r00010001]);//96k
	*/

	prStartJack { arg periodSize = 256, periodNum = 2, jackPath = "/usr/bin/jackd";
		var cmd, options;
		updatingCondition.test = false;
/*		if("pidof jackd".unixCmdGetStdOut.size > 0, {
			"jack was running -
			this.prStopJack;
		});
		while({"pidof jackd".unixCmdGetStdOut.size > 0}, {"waiting for jack to stop...".postln; 0.1.wait});*/
		cmd = jackPath ++
		" -R -dalsa -r"++sampleRate.asString++
		" -p"++periodSize.asString++
		" -n"++periodNum.asString++
		" -D -H -dhw:"++cardID.asString++
		" -i"++ins.asString++
		// " -o"++outs.asString;
		" -o"++ins.asString;
		"run jack command ".post; cmd.postln;
		cmd.unixCmdGetStdOutThruOsc({|line|
			"from jack: ".post; line.postln;
			this.prParseJackOutput(line);
			}, {
				if(audioIsRunning, {
					"Jack crashed, restarting!".warn;
					"killall scsynth".unixCmd;
					"killall supernova".unixCmd;
					this.changed(\message, "Jack crashed, restarting!");
					{this.startAudio;}.defer(1);//to give extra time
					}, {
						//when exits, signal routine
						updatingCondition.test = true;
						updatingCondition.signal;
						jackWasStartedBySC = false;
						"oscpipe: jack finished.".postln;
				});
		});

		// {"jack_load netmanager".unixCmd;}.defer(6); //load netmanager later - went to prParseJackOutput
	}

	prStopJack {
		if("pidof jackd".unixCmdGetStdOut.size > 0, {
			// "killall -9 jackdbus".unixCmd;
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

	prDisconnectRedundantHardwareIns {arg channelArrayToDisconnect; //should be 0-based
		SCJConnection.disconnect(channelArrayToDisconnect, channelArrayToDisconnect, "system:capture_", serverInputNameInJack);
	}

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
		var modeWord, srWord, modeByte, cmd1, cmd2, msgBack1, msgBack2;
		("setting samplerate to " ++ sr).postln;
		switch(sr,
			44100, {
				modeWord = "'Single'";
				ins = 64;
				// outs = 64;
				outs = 32; //always 32, just in case - we don't have more converters anyway
				modeByte = 2r00000000;
				srWord = "'44.1 kHz'";
			},
			48000, {
				modeWord = "'Single'";
				ins = 64;
				// outs = 64;
				outs = 32; //always 32, just in case - we don't have more converters anyway
				modeByte = 2r00010000;
				srWord = "'48 kHz'";
			},
			88200, {
				modeWord = "'Double'";
				ins = 32;
				outs = 32;
				modeByte = 2r00000001;
				srWord = "'88.2 kHz'";
			},
			96000, {
				modeWord = "'Double'";//would be "'Quad'" for 176/192kHz
				ins = 32;
				outs = 32;
				modeByte = 2r00010001;
				srWord = "'96 kHz'";
			}
		);
		// set commands for card parameters
		cmd1 = "amixer -c " ++ cardID.asString ++ " sset 'Internal Clock' " ++ srWord;
		cmd2 = "amixer -c " ++ cardID.asString ++ " sset 'MADI Speed Mode' " ++ modeWord;
		if(modeWord.notNil,
			{
				updatingCondition.test = false;
				sampleRate = sr;
				// set sampleRate on RME
				this.prSendSysex(midiPort, [0x01, modeByte]);
				// 0.1.wait; // to stabilize - propbably not needed... it's destabilized for some time anyway
				// "before cmds".postln;
				msgBack1 = cmd1.unixCmdGetStdOut; // set proper sample rate on madi
				msgBack1.postln;
				msgBack2 = cmd2.unixCmdGetStdOut; // set proper speed mode on madi
				msgBack2.postln;
				// "after cmds".postln;
				// server params
				server.options.numOutputBusChannels = outs;
				server.options.numInputBusChannels = ins;
				server.options.numAudioBusChannels = (ins + outs) * 8;
				server.options.sampleRate = sampleRate;
				6.wait;//wait for clocks to get in sync
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
}