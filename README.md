# SoundLab

_Set of classes for managing sound labs in DXARTS: Raitt 113, 117, 205._

## How to use:

After installing this Quark, use following line in your `startup.scd` to start the whole setup:

```supercollider
l = SoundLab.new("CONFIG_117.scd");
```
changing the appropriate parameters. `ConfigFileName` should refer to the configuration file for the desired room.

If you need to restart the SuperCollider (not the computer), remember to cleanup everything beforehand by typing:
```supercollider
l.cleanup
```
Failure to do so will result in hanging processes and inability to properly restart the whole setup.


-----
## Requirements:
- SuperCollider
- jconvolver
- JACK

**Quarks** (These will be automatically installed.)
- Ctk
- https://github.com/dyfer/JconvolverSC.quark
- https://github.com/dyfer/OscPipe.quark
- http://github.com/dyfer/WsGUI.quark
