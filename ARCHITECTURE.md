# Architecture

## Module Dependency Graph

```
submodule/miuix (composite build, MIUI Compose UI toolkit)
       │
       ▼
  app (Android application) ──▶ core (shared Java library)
       │
       ▼
  desktop (Linux desktop variant, shares core/)
```

- **`core/`** — Pure Java 8 library (JVM target 17). Contains `AspectRatio.java` and `DeviceRefresh.java`, shared between Android and desktop frontends. No Android dependencies.
- **`app/`** — Main Android application. Depends on `core/` and `submodule/miuix` (via `includeBuild` composite build).
- **`desktop/`** — Linux desktop variant. Shares `core/` module.
- **`submodule/miuix`** — Upstream-locked MIUI-style Compose UI toolkit. Composite build, must not be modified.

## Package Structure

| Package | Responsibility |
|---|---|
| `io.github.togo3.scrcaster` (root) | Activity entry points, global facade |
| `.nativecore/` | ADB protocol, USB tunnel, video decoding, mDNS discovery, QR pairing |
| `.connection/` | Connection controllers, connection UI state, QR pairing flow, Handoff |
| `.scrcpy/` | scrcpy client API, session management, protocol parsing, control messages, gamepad HID |
| `.pages/` | Compose UI pages, ViewModels, navigation |
| `.services/` | Connection state management, device connection coordination, auto-reconnect, recording |
| `.storage/` | DataStore persistence, app settings, scrcpy options, quick devices |
| `.models/` | Data models: `ConnectionTarget`, `DeviceShortcut`, `DeviceShortcuts` |
| `.constants/` | UI constants, defaults, scrcpy presets |
| `.scan/` | QR code scanner |
| `.password/` | Biometric auth, password injection, password vault |
| `.scaffolds/` | Reusable Compose component scaffolds |
| `.widgets/` | Video output, virtual buttons, device cards, terminal input |
| `.ui/` | Theme config, haptics, system bars, liquid glass components |
| `.util/` | Utilities: Debouncer, IntentCompat, QrCodeEncoder |
| `com.termux.terminal` | Third-party terminal emulator engine |
| `com.termux.view` | Third-party terminal renderer view |

## Key Abstractions

### Interfaces

| Interface | Location | Purpose |
|---|---|---|
| `ConnectionBackend` | `connection/ConnectionController.kt` | Platform-neutral connection contract: `isStreaming()` + `cancelPendingConnect()` |
| `PairingConnectionBackend` | `connection/ConnectionController.kt` | TV-specific connection backend: connect/disconnect/pair/findQrService/findConnection/awaitHandoff |
| `ConnectionPreferencesStore` | `connection/ConnectionController.kt` | Connection preferences persistence contract: load/save |
| `QrPairingTransport` | `connection/QrPairingFlow.kt` | QR pairing transport contract: findQrService/pair/findConnection |

### Key Classes

#### Connection Layer

| Class | Responsibility |
|---|---|
| `NativeAdbService` | ADB service singleton. Wraps `DirectAdbTransport`, provides coroutine-based connect/disconnect/shell/pair. Holds connection mutex, manages TCP and USB connection types |
| `DirectAdbConnection` | Single ADB connection (TCP or stream). Implements ADB protocol handshake (CNXN/STLS/AUTH), stream multiplexing, shell/push/pull |
| `DirectAdbTransport` | Low-level transport helper. Manages RSA key pair (generate/import/persist), creates `DirectAdbConnection` instances, provides pairing and mDNS discovery |
| `UsbAdbTunnel` | USB ADB tunnel. Manages USB device connection (permission request, interface lookup, Bulk endpoint config), wraps as InputStream/OutputStream |
| `UsbAdbSession` | USB ADB session singleton. Manages `UsbAdbTunnel` lifecycle, shares connection lock with `NativeAdbService` |
| `AdbMdnsDiscoverer` | mDNS service discovery using `NsdManager` for ADB TLS pairing/connect services |
| `AdbSocketStream` | ADB logical stream abstraction. Maps to localId, provides blocking InputStream/OutputStream with flow control |
| `DeviceAdbConnectionCoordinator` | ADB connection coordinator. Wraps `NativeAdbService`, provides TCP/USB connection, probe, pairing, device info |
| `DeviceConnectionController` | Phone-side connection controller. Implements `ConnectionBackend`, coordinates ADB connection and scrcpy session |
| `ConnectionController` | TV-side connection controller. Manages connection UI state, pairing flow, keep-alive |

#### scrcpy Integration

| Class | Responsibility |
|---|---|
| `Scrcpy` | scrcpy client high-level API. Manages server jar extraction/deployment, session lifecycle (start/stop), audio playback, screen control, clipboard sync |
| `Scrcpy.Session` | Session manager: socket communication, video/audio/control reader threads, `ControlWriter` for control message injection |
| `ClientOptions` | Client options data class mapping all scrcpy CLI arguments, with `fix()`/`validate()` methods |
| `GamepadHid` | Gamepad HID descriptor and report builder (Xbox 360 style, 15-byte report format) |
| `GamepadInputHandler` | Captures gamepad key/motion events, forwards via UHID virtual gamepad to remote device |

#### Video Decoding/Rendering

| Class | Responsibility |
|---|---|
| `NativeCoreFacade` | Central facade for video rendering and decoder management. Coordinates `VideoDecoderController` and `PersistentVideoRenderer`, manages session lifecycle mutex |
| `VideoDecoderController` | Decoder lifecycle management: create/rebuild/release `MediaCodecVideoDecoder`, caches and replays bootstrap packets |
| `PersistentVideoRenderer` | Persistent EGL renderer: decoder always renders to SurfaceTexture-backed Surface, UI Surface as display target via EGL. Supports FIT/STRETCH/CROP/LONG_EDGE modes |
| `MediaCodecVideoDecoder` | Android MediaCodec wrapper, supports H.264/H.265/AV1/VP8/VP9 |

#### UI Layer

| Class | Responsibility |
|---|---|
| `MainScreen` | Main Compose entry. 4 bottom tabs (Devices/Terminal/Files/Settings), HorizontalPager, NavDisplay for sub-page navigation |
| `DeviceTabViewModel` | Device tab ViewModel, manages connection flow (USB/LAN/QR pairing), quick devices, scrcpy options |
| `StreamScreen` | Fullscreen streaming screen, displays scrcpy video output, supports PiP |

#### Global Runtime

| Class | Responsibility |
|---|---|
| `AppRuntime` | Global runtime singleton, holds `appContext`, `scrcpy` instance, current connection target, Snackbar management |
| `Storage` | Storage singleton entry, lazily initializes `AppSettings`/`QuickDevices`/`ScrcpyOptions`/`ScrcpyProfiles`/`AdbClientData` |

## Connection Flows

### USB Connection

```
UsbAdbDeviceWatcher detects USB ADB device (interface class 0xFF)
  → User clicks connect
  → UsbAdbSession.openTunnel(context, usbDevice)
     → UsbAdbTunnel.open(): request USB permission → openDevice → find ADB interface
       → claimInterface → find Bulk IN/OUT endpoints → create InputStream/OutputStream
  → DeviceAdbConnectionCoordinator.connectUsb(usbDevice, inputStream, outputStream)
     → NativeAdbService.connectUsb(inputStream, outputStream, deviceId)
        → DirectAdbConnection(inputStream, outputStream, ...) [STREAM mode]
        → conn.handshake() → performAdbHandshake() [CNXN → AUTH → SIGNATURE/PUBKEY]
  → DeviceConnectionController.handleAdbConnected(host, port, connectionType=USB)
  → Start scrcpy session
```

### Wireless LAN Connection

```
User enters host:port or selects quick device
  → DeviceTabViewModel triggers connection
  → DeviceConnectionController.connectWithTimeout(host, port, timeoutMs)
  → DeviceAdbConnectionCoordinator.connectWithTimeout(host, port, timeoutMs)
     → NativeAdbService.connect(host, port) [TCP mode]
        → DirectAdbConnection(host, port, ...) [TCP constructor]
        → conn.handshake(timeoutMs) → handshakeTcp() → performAdbHandshake()
  → DeviceConnectionController.handleAdbConnected(host, port, connectionType=LAN)
  → Start scrcpy session
  → DeviceAdbAutoReconnectManager starts keep-alive loop (LAN only)
```

### QR Code Pairing

```
Direction 1: Local device generates QR code for remote to scan
  → runQrPairing(transport, onPayload, onStatus)
     → buildAdbQrPairing() generates name + secret + payload
     → onPayload(payload) displays QR content in UI
     → runQrPairingWith(transport, name, secret, onStatus)
        → transport.findQrService(name) [mDNS discovery]
        → transport.pair(host, port, secret) [TLS pairing]
        → transport.findConnection(host) [mDNS discovery]
        → Returns QrPairingResult.Paired(host, connection)
  → Use connection endpoint via LAN connection flow

Direction 2: Local device scans remote's QR code
  → QrScanner scans → ScannedQr parsed → extract name + secret
  → runQrPairingWith(transport, name, secret, onStatus)
  → Same three steps: findQrService → pair → findConnection
```

## scrcpy Protocol Flow

```
Scrcpy.start(options):
  1. Validate options (options.validate())
  2. Generate scid (random 31-bit)
  3. Extract server jar to cache (extractAssetToCache)
  4. NativeAdbService.push(serverJar, "/data/local/tmp/scrcpy-server.jar")
  5. Build server command: CLASSPATH=... app_process / com.genymobile.scrcpy.Server <version> <params>
  6. Session.start(serverCommand, scid, options):
     a. NativeAdbService.openShellStream(serverCommand) — start scrcpy server
     b. Wait SERVER_BOOT_DELAY_MS
     c. openAbstractSocketWithRetry(socketName) — open abstract socket
     d. Allocate streams based on options.video/audio/control
     e. Read deviceName
     f. Create ActiveSession
  7. NativeCoreFacade.onScrcpySessionStarted(info, session, scrcpy, options):
     a. controller.releaseAll() + resetBootstrap()
     b. renderer.setVideoSize(width, height)
     c. If active Surface → controller.ensureDecoder(session)
     d. session.attachVideoConsumer { packet → cacheAndFeed(packet) }
  8. If audioPlayback → create ScrcpyAudioPlayer, session.attachAudioConsumer
  9. If recording → create NativeMp4Recorder/NativeWavRecorder/NativeAacRecorder
```

### Video Data Flow

```
scrcpy server → AdbSocketStream → Session.videoReaderThread
  → Parse 12-byte header (ptsAndFlags + packetSize)
  → Distinguish session packet (width/height change) vs media packet
  → videoConsumers.forEach { it(VideoPacket) }
  → NativeCoreFacade.cacheAndFeed(packet)
     → VideoDecoderController.feed(packet)
        → MediaCodecVideoDecoder.feedAnnexB(data)
        → MediaCodec decode → PersistentVideoRenderer (EGL SurfaceTexture)
           → displayEglSurface (UI Surface)
```

### Control Flow

```
UI touch/key → Scrcpy.injectTouch/injectKeycode/injectScroll
  → Session.injectTouch (mutex protected)
     → ControlWriter.writeInjectTouch
     → AdbSocketStream.outputStream → DirectAdbConnection → remote device

Gamepad:
  KeyEvent/MotionEvent → GamepadInputHandler
     → GamepadHid.buildReport(slot) → 15-byte HID report
     → Scrcpy.uhidCreate / Scrcpy.uhidInput / Scrcpy.uhidDestroy
     → ControlWriter → remote device creates virtual UHID gamepad
```

## UI Navigation Structure

```
MainActivity (phone)
  → MainScreen (Compose)
     → HorizontalPager (4 tabs):
        [0] Devices → DeviceTabScreen
        [1] Terminal → TerminalScreen (ADB shell)
        [2] Files → FileManagerScreen
        [3] Settings → SettingsScreen
     → NavDisplay (sub-page navigation):
        RootScreen.Home → main pager
        RootScreen.Advanced → ScrcpyAllOptionsScreen
        RootScreen.About → AboutScreen
        RootScreen.ThemeSettings → ThemeSettingsScreen
        RootScreen.VirtualButtonOrder → VirtualButtonOrderScreen
        RootScreen.FullscreenControl → FullscreenControlRoute
        RootScreen.ScrcpyOptionRecord(profileId) → RecordPreferencesScreen

StreamActivity (fullscreen playback)
  → StreamScreen → VideoOutputTarget

TvActivity (TV)
  → ConnectionScreen (TV connection UI, single-device mode)
  → StreamActivity (tvReceiver=true)
```

## core/ Module

Pure Java 8 library with two cross-platform shared classes:

- **`AspectRatio.java`** — Aspect ratio/cropping math. Provides `Ratio` enum (DEVICE/SQUARE/CLASSIC/WIDE/CUSTOM), `targetRatio()`, `cropForRatio()`, `fillCrop()`, `coverCrop()`. Used by `PersistentVideoRenderer` and `ClientOptions` in Android, and by desktop frontend.
- **`DeviceRefresh.java`** — Device list snapshot coordination. `reconcile()` compares before/after device snapshots, returns added/removed/current device lists. Used by `ConnectionController` and `DeviceTabViewModel`.
