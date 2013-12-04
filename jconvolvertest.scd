
//how to use Jconvolver class
//1. create configuration file
//2. create an instance (starts processing)

//creating config file from folder of files
//part size that equls to jack period size gives no latency supposedly
//maxSize must be larger than numFrames of the kernel
// autoConnect.. if simple number, will connect starting on that channel (with respective number of ins/outs), if array, will map to specified input...
//not providing configFilePath creates the file in the same folder as kernels and assumes relative paths to kernels
//num ins and outs equals number of found kernels, also uses 1:1 matrix, assumes single channel kernels,

~path = "~/Documents/SoundLabBrain/SoundLabKernelsNew/48000/decor".standardizePath;
Jconvolver.createSimpleConfigFileFromFolder(partSize: 256, maxSize: 100000, folderPath: ~path, matchFileName: "*.wav", autoConnectToScChannels: 100, autoConnectToSoundcardChannels: 0);

j = Jconvolver.newFromFolder(~path); //we don't care for compensating latency (cutting kernels...) so that's it

//now you can look in qjackctl at the connections

//when done:
j.free;