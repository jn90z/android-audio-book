# Platform and integration modules

The default app remains a blank white-screen starter. Optional capabilities are
kept outside the app module so projects opt into only what they need.

## Platform
- auth: identity/session foundation
- networking: connectivity abstraction
- realtime: authenticated event transport
- storage: persistence abstraction
- diagnostics: logging abstraction

## Integrations
- bluetooth: BLE discovery/connect/read-write contract
- camera: preview/capture contract
- location: one-shot and observed location contract
- nfc: NFC payload contract
- usb: USB discovery/I/O contract
- iot: capability-based ESP32/Raspberry Pi device model

## Examples
Realtime, multiplayer, analytics and IoT controller examples exercise the
contracts without changing the blank default UI.

Hardware implementations must request Android permissions only in apps that
actually include that integration.
