# Mohican

The client either connects over 3 serial ports over USB to the DeltaProto microPlacer, or to the DeltaProto hybrid placer using the Teknic Clearpath driver.

The client starts a socket on port 8008 which is used by the protoflow webinterface to control the microPlacer or hybridPlacer

## The default branch has been renamed!
master is now named **main**

If you have a local clone, you can update it by running the following commands.

```
git branch -m master main
git fetch origin
git branch -u origin/main main
git remote set-head origin -a
```
