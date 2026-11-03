# IoT Controller

Generic controller architecture for ESP32 and Raspberry Pi devices.

Devices expose capabilities rather than board-specific UI. A DeviceTransport
can be implemented for BLE, local LAN or cloud relay. The same screen/model can
therefore control an ESP32 directly over BLE, a Raspberry Pi over LAN, or a
remote device through the cloud.

Start with capabilities such as relay, PWM, RGB/RGBW, sensor, motor and custom.
