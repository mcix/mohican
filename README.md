# Mohican

The client either connects over 3 serial ports over USB to the DeltaProto microPlacer, or to the DeltaProto hybrid placer using the Teknic Clearpath driver.

The client starts a socket on port 8008 which is used by the protoflow webinterface to control the microPlacer or hybridPlacer

## Which machine

The client finds the machine by itself. A Teknic ClearPath-SC hub among the serial ports selects the hybrid placer's Teknic driver (`deltateknic.dll` + `sFoundation20.dll` next to the jar); otherwise every serial port is probed for the microPlacer's motor boards. While nothing answers it keeps looking (5 s, 10 s, … up to a minute) and the window's *Motors* panel says what it found; *Reconnect* starts the search over. `motor.driver=teknic|serial` in `configuration.properties` pins the choice, `teknic.hub.match` widens how the hub is recognised.

The operator pages at https://deltaproto.com and https://mohican.deltaproto.com (and any `*.deltaproto.com` host, and `localhost` on any port) are allowed to drive the client; `allowed.origins` overrides that list.

## The default branch has been renamed!
master is now named **main**

If you have a local clone, you can update it by running the following commands.

```
git branch -m master main
git fetch origin
git branch -u origin/main main
git remote set-head origin -a
```
