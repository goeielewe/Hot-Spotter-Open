# USB-C thermal cameras for Hot Spotter Open

Research note, 2026-11-02. Prices and stock move. Links were checked on that date.

Hot Spotter Open reads a USB-C thermal camera with its own USB code. It does not ship a vendor camera library. The watch engine, alarms, profiles, and tour are the same code the demo camera uses.

The bench arm was drawn for a camera with about a 55° × 43° field of view, 610 mm above the bench, covering roughly 635 × 480 mm. A camera with that field of view can use the arm as it is. A narrow camera cannot.

## 1. Which cameras are easiest

"Easiest" means: the phone sees a normal USB device, a published note already explains where the temperatures are in the frame, and we do not have to ship a vendor library.

Two families cover most of the sticks people actually buy. A third group should wait.

### Family A — Realtek UVC, split frame (start here)

The camera is a USB Video Class device behind a Realtek bridge. A short frame is only a gray picture. A tall frame is that picture plus a temperature map.

| Camera | Sensor | Range | Field of view | What we know |
|---|---|---|---|---|
| Xinfrared / InfiRay P2 Pro | 256 × 192, 12 µm, ≤40 mK, 25 Hz | −20 °C to 600 °C, about ±2 °C | 56° × 42°, 3.2 mm lens | Best public notes. Official price $249, macro bundle $299. |
| Topdon TC001 | 256 × 192, 25 Hz | about −20 °C to 550 °C, about ±2 °C | Same class of module. Confirm the lens on the unit before trusting the arm's coverage. | Same protocol family as the P2 Pro. Topdon.us lists $289. An Amazon listing showed $199.98. |
| UNI-T UTi260M | 256 × 192, <50 mK, 25 Hz | −20 °C to 550 °C, ±2 °C or ±2% | 56.0° × 42.2°, 3.2 mm lens | Same frame layout. Amazon listed $299.99. |

Published frame layout, from the UTi260M macOS notes and the P2 Pro / TC001 discussions on EEVblog:

- USB vendor `0x0BDA` (Realtek). The UTi260M product id cited in the field is `0x5830`. Product ids differ by brand, so match the tall video mode, not one id.
- 256 × 192 mode: automatic-gain picture only. No temperatures.
- 256 × 384 mode, YUYV: rows 0–191 are the picture; rows 192–383 are little-endian 16-bit values.
- Scale used in the field: `°C = raw / 64 − 273.15`. That is 1/64 kelvin steps. It is not a promise of ±2 °C accuracy. Shiny metal still reads cold. Emissivity in the vendor app defaults to about 0.95.
- The module shutters every few tens of seconds. The picture freezes for under a second and clicks. Hot Spotter already treats a long frozen picture as a camera fault. A shutter must not count as that fault.
- The high range (up toward 550–600 °C) is a separate mode. A soldering iron can clip the low range. The first driver can read whichever range the camera is already in. Switching range is a later vendor command.

Open-source code that already speaks this family:

| Project | License | Use it? |
|---|---|---|
| [LeoDJ/P2Pro-Viewer](https://github.com/LeoDJ/P2Pro-Viewer) | MIT, copyright 2023 LeoDJ | Yes, with the MIT notice, if we take code. The README is also the cleanest public description of the vendor commands. It is a desktop viewer, not an Android library. Shutter, range, and emissivity commands are only partly done. |
| [yomboprime/yombir](https://github.com/yomboprime/yombir) (now on Codeberg) | GPL-2.0 | Do not copy. This repository is Apache-2.0, and GPL-2.0 is not compatible with that license. The README is still useful as a description: YUYV, 256 × 192, 16-bit, 24 fps, Topdon TC001 and InfiRay P2. |
| [jambd/android_infiray_viewer](https://github.com/jambd/android_infiray_viewer) | No license file found | Do not copy. It is an Android UVC app that saves the 16-bit frame and sends palette commands. Treat it as evidence that Android can do this, not as code to paste. |
| InfiRay Tiny1-B / Tiny1-C vendor SDK | Proprietary | Do not ship. See section 2. |

Android already has `UsbManager`. These cameras use bulk or isochronous video. Bulk is the path to write first. We do not need libuvc, and we should not take a GPL build of it.

### Family B — HIKMICRO, a different USB device (later)

| Camera | Sensor | Range | Field of view | Price checked |
|---|---|---|---|---|
| HIKMICRO Mini2 / Mini2Plus, including V2 | 256 × 192, <40 mK, 25 Hz | Mini2Plus V2 datasheet: −20 °C to 150 °C and 100 °C to 400 °C, ±2 °C or ±2% | 25° × 18.8° on the V2 datasheet (6.9 mm, manual focus) | Amazon listed a Mini2Plus at $199. HIKMICRO UK listed Mini2 V2 at £199.99 and it was sold out. |
| Vevor / HTI HT-203U | Same core | Same class | Same class | Rebrand. USB `2bdf:0102`, product string "HikCamera". |
| Some Topdon TC002C Duo units | 256 × 192 plus a visible camera | Same class | Narrower than the P2 Pro | Topdon.us listed the TC002C Duo at $299. EEVblog traffic captures say the thermal path is Hik, not InfiRay. |

This is not the Realtek split frame. [cfbird/HT203U-Thermal](https://github.com/cfbird/HT203U-Thermal) (GPL-3.0) reports that libuvc fails every mode with `UVC_ERROR_INVALID_MODE`. The app talks UVC over bulk transfers in Kotlin: claim the streaming interface, PROBE/COMMIT, read the bulk endpoint, reassemble frames from the FID/EOF bits. A full radiometric frame is on the order of 200,704 bytes. [3ricj/IRPanoView](https://github.com/3ricj/IRPanoView) documents a similar Hik control plane for the TC002C class.

The official software is the HIKMICRO Viewer app and HIKMICRO Analyzer for Windows. No public Android SDK for the Mini2 showed up in the download center. Do not copy the GPL-3 app. The frame layout can be reimplemented after we own a unit.

The narrow field of view is the practical reason to wait. From 610 mm, 25° horizontal covers about 270 mm of bench, not the 635 mm the arm was drawn for. The P2 Pro's 56° lens covers that patch. A Mini2 on this arm would need a new height or a new head.

### Do not start with these

| Camera | Why not first |
|---|---|
| Seek Thermal Compact / CompactPRO | Not a normal UVC temperature stream. Seek's SDK is a separate commercial license. |
| Topdon TC001 Mini and other 96 × 96 sticks | Too coarse for the flame watch and the cell-sized blob tracker. |
| InfiRay Tiny1 core modules bought as bare sensors | SPI or parallel video, plus a vendor SDK under a sales contract. Wrong shape for a phone stick. |

## 2. Maker terms

Buying a camera lets the owner use that camera. It does not let us redistribute the vendor's app, SDK, or manuals.

- **InfiRay / Xinfrared.** The consumer P2 Pro is sold as a finished stick with a free Android app. The Tiny1 module SDK is different. People who asked InfiRay for it have described a sales contract, export-control clauses, and a duty not to pass on material that could be treated as confidential. Do not request that SDK "just in case," and do not commit any PDF or header that came from it. The MIT P2 Pro viewer was written to avoid those libraries. Stay on that side of the line.
- **Topdon and UNI-T.** Their phone apps are closed. Nothing found grants a license to embed those apps or their native libraries. Talking USB to a device the user plugged in, with our own code, is the path that does not need their permission. Do not use their logos, and do not say the app is theirs or approved by them.
- **HIKMICRO.** Viewer and Analyzer are their software. No redistributable Mini2 SDK was published. Same rule: our own USB code, no vendor library, no claim of approval.
- **GPL viewers** (yombir GPL-2, HT-301 hacklib GPL-3, HT203U-Thermal GPL-3). Reading them to learn the frame layout is fine. Pasting them into this repository is not. This repository is Apache-2.0. A GPL file is not compatible with that license.
- **MIT** (LeoDJ/P2Pro-Viewer). Safe to adapt if the copyright and permission notice stay with the files we take. The packet layout in this repo is a Kotlin rewrite, and the MIT notice is in `NOTICE`.
- **Trademarks.** The app can say it works with a P2 Pro or a TC001. It should not say "official" or "approved."
- **Export.** These sticks are already sold at retail in the US and the EU. The app should not rewrite firmware, raise the frame rate, or unlock a range the device does not already expose. That keeps the app on the civilian side of the cameras people can already buy.

The end-user license shipped with this app does not name a camera vendor's SDK. It says the app is a monitoring aid, not a fire alarm.

## 3. What to buy first

Buy two sticks, one driver.

1. **Xinfrared / InfiRay P2 Pro (Android, USB-C).** First camera. The protocol notes are the best, the field of view matches the bench arm, and the temperature range covers a bench. About **$249** from [Xinfrared](https://www.xinfrared.com/products/infiray_p2_pro_thermal_camera), or **$299** with the macro lens. InfiRay's own page listed the same prices. The Xinfrared page was out of stock on 2026-11-02, and the Amazon P2 Pro listing was unavailable. Check Xinfrared, the InfiRay store, and AliExpress sellers that name the P2 Pro and 256 × 192. Skip listings that only say "512 × 384" with no native resolution. That number is often an upscale.
2. **Topdon TC001 (Android, USB-C).** Second camera, same driver. It is the widely sold twin. About **$200 to $289**: Topdon.us listed **$289**, and an Amazon TC001 listing showed **$199.98**. Buy from [Topdon](https://www.topdon.us/collections/mobile-device-thermal-imagers) or Amazon so the return is easy if a unit turns out to be a different board.

Do not buy the TC001 Mini. Do not buy an iOS / Lightning version.

A UNI-T UTi260M (about **$300** on Amazon) is a third label on the same frame layout, with the same 56° lens. It is optional. One P2 Pro and one TC001 are enough to prove the driver.

Hold the HIKMICRO Mini2Plus (about **$199** on Amazon when listed, narrower lens, different USB chip) until the first driver is streaming. It is the right second *protocol*, not the right first camera.

## 4. How the open app is built

`ThermalSource` is the camera. `WatchEngine` only sees `ThermalFrame` temperatures. USB Video Class code lives in `OpenThermalSource`, and the temperature scales live in `Radiometry`. There is one application, `com.hotspotter.open`. Demo mode uses the same detector with synthetic frames.

`CameraModule` is the small door the service calls:

```text
interface CameraModule {
    fun create(context: Context, emissivity: Float): ThermalSource
}
```

Family A is the Realtek split frame (P2 Pro, TC001, TOOLTOP T7, and any camera that offers 256×384). A 512×484 frame uses the TC002C scale. Thermal Master P1 and P3 are a separate stream. UTi120M is recognized, and a raw frame can be saved, but absolute temperatures are not invented.

Emissivity stays a user setting. If the stick's temperatures are already scaled for 0.95, the driver applies the user's emissivity as a correction on top of that. Absolute alarms (a 55 °C runaway) are only as good as that scale. Flame detection looks at contrast with the bench, so it is less sensitive to a small offset.

The shutter click is a short frozen frame. The frozen-frame fault timer should ignore gaps under about two seconds while a UVC camera is live.

A Gradle check scans the source tree and the debug APK. The build fails if a blocked vendor SDK class, file, or library name is present. The names are assembled in the build script.

## 5. Effort

The watch is already written. The new work is a build split and one USB driver.

| Piece | Size | Notes |
|---|---|---|
| Single app, `CameraModule`, APK and source scan, open license text | Small | This is the tree in the repository. |
| Family A session: enumerate, claim the interface, select the 256 × 384 mode, reassemble frames, split picture from temperatures, `°C = raw / 64 − 273.15` | The large piece | Prove it on a phone. Record one frame fixture so the scale has a unit test. |
| Emissivity correction, shutter pause vs the frozen-frame fault, preview bitmap | Medium | Needed before the alarms mean anything. |
| Second stick on the same protocol (TC001, maybe UTi260M) | Small | An allow-list entry and one evening with the device, if the first stick already streams. |
| High/low range and shutter commands | Later | Vendor control transfers. LeoDJ mapped some of them. Not required to read temperatures. |
| HIKMICRO / Mini2 backend | Another driver of the same size | Different USB chip, narrower lens, new arm height. |

Family A is implemented for a P2 Pro and the same frame on a TC001. It still needs a phone and those cameras before the temperatures can be called proven. Demo mode and the unit tests cover the watch. The camera itself cannot be proven without the hardware.

## Sources

Checked 2026-11-02.

- P2 Pro product page, $249 / $299 macro: https://www.xinfrared.com/products/infiray_p2_pro_thermal_camera and https://www.infiray.com/p2-pro-thermal-camera-for-smartphone
- LeoDJ/P2Pro-Viewer, MIT: https://github.com/LeoDJ/P2Pro-Viewer
- yombir, GPL-2.0, TC001 and P2: https://github.com/yomboprime/yombir
- UTi260M frame layout and `raw / 64 − 273.15`: https://github.com/mamapapa1/uti260m-macos
- UNI-T UTi260M manual specs, 56° × 42.2°, −20 °C to 550 °C: https://manualspro.net/188241-uni-t-uti260m-thermal-imager-user-manual
- UNI-T UTi260M Amazon listing, $299.99: https://www.amazon.com/UNI-T-Thermal-MicroUSB-Infrared-Smartphones/dp/B0BC125VTQ
- Topdon phone cameras, TC001 $289, TC002C Duo $299, TC001 Mini $139: https://www.topdon.us/collections/mobile-device-thermal-imagers
- Topdon TC001 Amazon listing, $199.98: https://www.amazon.com/TOPDON-Thermal-Imaging-Android-Resolution/dp/B0HDC9QQN8
- HIKMICRO Mini2Plus V2 datasheet, 256 × 192, 25° × 18.8°, two ranges: https://webassets.hikmicrotech.com/global/asset/0c3b29f4474843948befdd8e442d6c03.pdf
- HIKMICRO Mini2 V2 UK, £199.99: https://hikmicro.co.uk/products/mini2-v2-thermal-imager
- HIKMICRO Mini2Plus Amazon listing, $199: https://www.amazon.com/HIKMICRO-Mini2Plus-Thermal-Imaging-Resolution/dp/B0CP9FCFJJ
- HT-203U as HIKMICRO `2bdf:0102`, GPL-3.0, libuvc does not negotiate: https://github.com/cfbird/HT203U-Thermal
- Hik-family notes for TC002C: https://github.com/3ricj/IRPanoView and https://www.eevblog.com/forum/thermal-imaging/tomdon-tc001/
- Tiny1 SDK is under a sales contract, not a public license: https://www.eevblog.com/forum/thermal-imaging/playing-with-with-the-infiray-tiny1-c-core-anyone-else-have-experience/
