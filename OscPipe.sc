/*
workaround for asynchronous access to stdOut - returns one line at a time through Ruby script and OSC
use as
"command".unixCmdGetStdOutThruOsc({|eachLine| eachLine.postln}, {"runs when command finishes".postln})
*/

OscPipe {
	var <shellCommand, <>receiveAction, <>exitAction, <id;
	var isPipe, <pipe;
	var <pid;
	var <oscFunction, slashId, rubyCmd1of3, rubyCmd2of3, rubyCmd3of3, schellCmdToOsc;
	classvar idDictionary, lastIdIndex, langPort;

	*initClass {
		idDictionary = IdentityDictionary.new;
		lastIdIndex = 0; //init...
		langPort = NetAddr.langPort;
	}

	*new {arg shellCommand, receiveAction, exitAction, id;
		^super.newCopyArgs(shellCommand, receiveAction, exitAction, id).init(false);
	}

	// *newPipe {arg shellCommand, receiveAction, exitAction, id; //use as Pipe.new(shellCommand, "w"); to access pipe: <class instance>.pipe
	// 	^super.newCopyArgs(shellCommand, receiveAction, exitAction, id).init(true);
	// }

	*removeAll {
			idDictionary({|pathAndFun, key|
			pathAndFun[1].free;
			idDictionary.removeAt(key);
		});
	}

	init {|isPipeArg|
		rubyCmd1of3 = "ruby -e \"require 'socket'; socket = UDPSocket.new; socket.connect(\\\"127.0.0.1\\\", "; //langPort here
		rubyCmd2of3 = "); oscAddress = \\\""; //slashId here
		rubyCmd3of3 = "\\\"; typeTag = \\\",s\\000\\000\\\"; def complement(inString); howMany = 4 - (inString.size % 4); howMany.times {inString = inString + \\\"\\000\\\"}; return inString; end; oscAddress = complement(oscAddress); \\$stdin.each_line do |message|; message = message.gsub(/\\n/, \\\"\\\"); message = complement(message); oscMessage = oscAddress + typeTag + message; socket.send oscMessage, 0; end\"";
		isPipe = isPipeArg;
		if(receiveAction.isNil, {receiveAction = {}});
		if(exitAction.isNil, {exitAction = {}});
		//get new ID here
		if(id.isNil, {
			id = ("oscpipe" ++ lastIdIndex).asSymbol;
			lastIdIndex = lastIdIndex + 1;
			}, {
				if(idDictionary.includesKey(id), {
					"OscPipe ID already in use! generating one instead...".warn;
					id = ("oscpipe" ++ lastIdIndex).asSymbol;
					lastIdIndex = lastIdIndex + 1;
					}, {
						lastIdIndex = lastIdIndex + 1; //just in case
				});
		});
		// ("OscPipe - adding new id: " ++ id).postln;
		//get vars
		// namedPipe = tempdir ++ id;
		slashId = "/" ++ id;
		schellCmdToOsc = shellCommand ++ " | " ++ rubyCmd1of3 ++ langPort ++ rubyCmd2of3 ++ slashId ++ rubyCmd3of3;
		//create oscFunction;
		oscFunction = OSCFunc.newMatching({|msg, time, addr, recvPort|
			// "received: ".post; msg.postln;
			receiveAction.value(msg[1]);
		}, slashId); // path matching{|msg, time, addr|
		//put it in the dictionary
		idDictionary.put(id, [shellCommand, oscFunction]);

		//now run the command:
		if(isPipe.not, {
			// "it's not a pipe".postln;
			// schellCmdToOsc.postln;
			// "OscPipe: running command and getting stdOut through OSC".postln;
			pid = schellCmdToOsc.unixCmd({|code, pid|
				// "OscPipe: process has exited, full command:".postln;
				// schellCmdToOsc.postln;
				this.free;
				exitAction.value(code, pid);
			}, true);
			^pid;
		}, {
			// "it's a pipe!".postln;
			"OscPipe: creating pipe and getting stdOut through OSC".postln;
			"OscPipe: pipe functionality is not tested, use at your own risk".warn;
			pipe = Pipe.new(schellCmdToOsc, "w");
			^pipe;
		});
	}

	//this will run automatically when used with unixCmd, but needs to be explicitly called when using pipe... can be used to force quit the process
	free {
		("OscPipe - freeing " ++ id).postln;
		if(isPipe, {
			pipe.close;
		});
		oscFunction.free;
		idDictionary.removeAt(id);
		// ("rm " ++ namedPipe).unixCmd;
	}
}


+ String {
	unixCmdGetStdOutThruOsc {
		arg receiveAction, exitAction;
		var pid;
		pid = OscPipe.new(this, receiveAction, exitAction);
		^pid;
	}
}



/*
Ruby tests:

>> require 'socket'
=> true
>> u1 = UDPSocket.new
=> #<UDPSocket:0x101125a78>

>> 0x00.chr
=> "\000"
>> 0x20.chr
=> " "
>> 0x4c.chr
=> "L"

>> "/cos" + 0x00.chr + 0x00.chr + 0x00.chr+ 0x00.chr + ",s" + 0x00.chr + 0x00.chr + "wiadomo" + 0x00.chr
=> "/cos\000\000\000\000,s\000\000wiadomo\000"

>> u1
=> #<UDPSocket:0x101125a78>
>> m = "/cos" + 0x00.chr + 0x00.chr + 0x00.chr+ 0x00.chr + ",s" + 0x00.chr + 0x00.
=> "/cos\000\000\000\000,s\000\000wiadomo\000"
>> m
=> "/cos\000\000\000\000,s\000\000wiadomo\000"
>> u1
=> #<UDPSocket:0x101125a78>
>> u1.connect("127.0.0.1", 57120)
=> 0
>> u1.send m, 0
=> 20


>> def complement(inString)
>> howMany = 4 - (inString.size % 4)
>> howMany.times {inString = inString + "\000"}
>> return inString
>> end

	//one test message
require 'socket'; socket = UDPSocket.new; socket.connect(\"127.0.0.1\", 57120); oscAddress = \"/cos\"; typeTag = \",s\000\000\"; message = \"wysle se cos\"; def complement(inString); howMany = 4 - (inString.size % 4); howMany.times {inString = inString + \"\000\"}; return inString; end; oscAddress = complement(oscAddress); message = complement(message); oscMessage = oscAddress + typeTag + message; socket.send oscMessage, 0

	//message from stdin
ping google.com | ruby -e "require 'socket'; socket = UDPSocket.new; socket.connect(\"127.0.0.1\", 57120); oscAddress = \"/cos\"; typeTag = \",s\000\000\"; def complement(inString); howMany = 4 - (inString.size % 4); howMany.times {inString = inString + \"\000\"}; return inString; end; oscAddress = complement(oscAddress); \$stdin.each_line do |message|; message = message.gsub(/\\n/, \"\"); message = complement(message); oscMessage = oscAddress + typeTag + message; socket.send oscMessage, 0; end"

	//escaped for sc
"ping google.com | ruby -e \"require 'socket'; socket = UDPSocket.new; socket.connect(\\\"127.0.0.1\\\", 57120); oscAddress = \\\"/cos\\\"; typeTag = \\\",s\\000\\000\\\"; def complement(inString); howMany = 4 - (inString.size % 4); howMany.times {inString = inString + \\\"\\000\\\"}; return inString; end; oscAddress = complement(oscAddress); \\$stdin.each_line do |message|; message = message.gsub(/\\n/, \\\"\\\"); message = complement(message); oscMessage = oscAddress + typeTag + message; socket.send oscMessage, 0; end\"".unixCmd


	//-------- sc test
(
f = { |time, addr, msg|
	"time: % sender: %\nmessage: %\n".postf(time, addr, msg);
};
thisProcess.addOSCRecvFunc(f);
);

// stop posting.
thisProcess.removeOSCRecvFunc(f);

NetAddr.langPort
*/
