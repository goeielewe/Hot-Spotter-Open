package com.benchfirewatch.source

/**
 * Attribution for code we ship. GPL and unlicensed projects were read as
 * reference and are not included.
 */
object OpenNotices {
    const val TEXT = """
Open-source notices

P2 Pro vendor command headers
LeoDJ/P2Pro-Viewer
Copyright (c) 2023 LeoDJ
MIT License. The copyright notice and this permission notice are kept with the packet layout in P2ProPackets.

PyThermalCamera
Copyright Les Wright and contributors
Apache License 2.0. The TC002C /16 temperature scale is used from that project's notes.

Thermal Master frame map
jvdillon/p3-ir-camera and ferrolho/thermal-master-viewer
Apache License 2.0. The P1 and P3 frame layout and the 1/64 kelvin scale are used from those notes.

UNI-T UTi120M
diegosainz/uni-t-thermal-uti120
Copyright (c) 2026 Diego Sainz
MIT License. This build recognizes the camera and saves a raw frame in the test report. Absolute temperatures need the factory table on the camera and are not invented here.

This app is not affiliated with or endorsed by Topdon, InfiRay, Thermal Master, UNI-T, or TOOLTOP. Those names belong to their owners.

Charger plugs
Shelly, Kasa, Tapo, Tasmota, Home Assistant, and Wemo are this app's own local clients.
python-kasa is GPL and is not included. Tuya, Govee, and Amazon cloud APIs are not included.

This build does not include a vendor camera SDK and does not include libusb.
"""
}
