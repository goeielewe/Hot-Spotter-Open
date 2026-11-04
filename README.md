# Hot Spotter Open

Hot Spotter Open is a thermal battery-bench fire watch for Android. It watches a USB-C UVC thermal camera plugged into the phone, and it can sound a siren when a cell heats up on its own or an open flame is in view. It can also alert a second phone and turn a charger plug off.

It is not a fire alarm, a smoke detector, a safety device, or a medical device. Do not rely on it as the only protection against fire or a cell running away. Keep a real smoke detector in the room, and an extinguisher you know how to use within reach.

A separate edition for FLIR ONE cameras exists and is not part of this repository.

## Supported Cameras

The phone is the USB host. A charge-only cable will not carry the picture. Temperatures from these cameras come from public frame notes, so check a known temperature on your own bench before you trust an absolute alarm.

| Camera | Status |
| --- | --- |
| InfiRay P2 Pro | Supported. 256×192 sensor, temperatures in the 256×384 frame. |
| Topdon TC001 | Supported. Same frame family as the P2 Pro. Confirm the lens on your unit. |
| TOOLTOP T7 | Beta. Same frame family. Needs testers. |
| Thermal Master P1 and P3 | Beta. Separate stream. Needs testers. |
| Topdon TC002C-class 512×484 frame | Beta. The scale is implemented. Needs testers. |
| UNI-T UTi120M | Beta. The camera is recognized and a raw frame can be saved. Temperatures are not shown, because they need the factory table on the camera. Needs testers. |

## Supported Smart Plugs

An alarm can turn a charger off on your own network. It never turns the charger back on. No vendor cloud account is required.

- Shelly, including the older relay plugs and the current RPC plugs
- TP-Link Kasa, on the local protocol
- TP-Link Tapo, with the local login
- Tasmota
- Home Assistant, any switch the phone can reach
- Wemo
- Matter, by pointing this at the Home Assistant switch that already controls the plug

Tuya, Govee, and Amazon plugs are not listed, because they need their own cloud.

## Send Test Report

Settings, then About, then **Send Test Report**. It shares one raw frame, the USB ids, the interface list, and a short log. Nothing is uploaded until you choose where to send it. Beta testers can use it when a camera does not stream or the temperatures look wrong.

## Build

You need JDK 17 or 21 and Android SDK platform 36.

```bash
./gradlew test assembleDebug
```

`test` runs the unit tests and the source guard. `assembleDebug` builds the debug APK and fails if that APK contains a blocked vendor SDK name. The APK is `app/build/outputs/apk/debug/app-debug.apk`. The installed application id is `com.hotspotter.open`.

GitHub Actions runs the same two tasks on pull requests.

There is no release keystore in this repository. Debug builds are signed with the debug key that Android Gradle Plugin creates on the machine that builds them.

## Bench Arm

Printable mount designs are in [`hardware/bench-arm/`](hardware/bench-arm/). The current arm is 20×20 mm aluminum extrusion, with OpenSCAD, STL files, and renders. An earlier EMT tube arm and a screw-down base pad are in the same folder. At about 610 mm above the bench, a camera with a 55° × 43° field of view covers roughly 635 × 480 mm.

## Camera Notes

How the USB frame families were chosen is written up in [`docs/usb-c-thermal-cameras.md`](docs/usb-c-thermal-cameras.md).

## License

Copyright 2026 goeielewe. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

This project is not affiliated with or endorsed by the camera or plug makers named above.
