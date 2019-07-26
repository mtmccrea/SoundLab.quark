# SoundLab

_Set of classes for managing sound labs in DXARTS: Raitt 113, 117, 205._

## How to use:

After installing this Quark, use following line in your `startup.scd` to start the whole setup:

```supercollider
l = SoundLab.new("CONFIG_117.scd");
```
changing the appropriate parameters. `ConfigFileName` should refer to the configuration file for the desired room.

You can free the resources and quit the server, JACK, web gui and jcovolver by typing:
```supercollider
l.cleanup;
```
The cleanup procedure is also triggered automatically when SuperCollider shuts down or then the class library is recompiled.


-----
## Requirements:
- SuperCollider
- sc3-plugins

**Quarks** (These will be automatically installed.)
- Ctk
- atk-sc3 with [kernels](http://www.ambisonictoolkit.net/download/kernels/) and [matrices](http://www.ambisonictoolkit.net/download/matrices/)
- http://github.com/dyfer/WsGUI.quark
