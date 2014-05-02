SoundLab

Set of classes for managing sound labs in DXARTS: Raitt 113, 117, 205

How to use:

After linking or copying these classes to SC's extensions folder, use following line in your startup.scd to start the whole setup:

l = SoundLab.new(48000, useKernels: true, configFileName: "CONFIG_117.scd");

changing the appropriate parameters. ConfigFileName should refer to the configuration file for the desired room.

If you need to restart the supercollider (not the computer), remember to cleanup everything beforehand by typing

l.cleanup

Failure to do so will result in hanging processes and inability to properly restart the whole setup.