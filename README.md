# Orbion

**A mobile-first remote control for AI tools and desktop applications.**

Control Claude Code, Cursor, VS Code, terminals and browsers from your phone. Accept, reject, navigate and type prompts without touching your keyboard.

- 📱 **No app install** – runs in your mobile browser
- 🔒 **No account, no cloud** – everything stays on your local network
- ⚡ **Instant pairing** – scan a QR code and you're connected

---

## Download

Grab the latest Windows installer from the **[Releases page](https://github.com/bhaveshsoni/remote-pc/releases/latest)**:

➡️ **[Download `OrbionSetup.exe`](https://github.com/bhaveshsoni/remote-pc/releases/latest)**

The installer bundles its own Java runtime, so **end users do not need Java installed**.
Each release is built automatically on a Windows runner by the
[release workflow](.github/workflows/release.yml). Prefer to build it yourself or
run from source? See [Build & run](#build--run-development) below.

> Releases are published when a version tag (e.g. `v1.0.0`) is pushed, so the
> link above is empty until the first release is cut.

---

## How it works

1. Build and install Orbion on Windows (`OrbionSetup.exe`), or run it from source (see below).
2. Orbion launches into the system tray and opens its dashboard.
3. The dashboard shows a **QR code** containing a secure pairing URL.
4. Scan it with your phone – the mobile UI opens in your browser.
5. Control your PC with **buttons**, **voice commands**, or **typed text**.

```
Phone (mobile web UI)  ⇄  WebSocket  ⇄  Orbion desktop (Spring Boot)  ⇄  Java Robot API  ⇄  Focused app
```

> **Note:** `OrbionSetup.exe` is not shipped in this repository – it is a build
> artifact. Produce it yourself with [`build-installer.bat`](#build-the-windows-installer)
> (output lands in `dist/`, which is git-ignored), or skip the installer entirely
> and run from source with Maven (see [Build & run](#build--run-development)).

## Features

| Mode | What it does |
|------|-------------|
| **Buttons** | ENTER, ESC, UP, DOWN, LEFT, RIGHT, TAB, SPACE sent as real key presses |
| **Voice** | "accept" → ENTER, "reject" → ESC, "go up" → UP, "go down" → DOWN, "left"/"right" → arrows |
| **Text** | Typed text is pasted into the focused app via clipboard + CTRL+V |

**Desktop dashboard** shows the QR code, local IP, session token, connected device count and recent commands. A system tray icon keeps Orbion running in the background.

## Security

- A unique **session token** is generated on every launch (128-bit secure random).
- The QR code embeds the token; WebSocket connections without a valid token are rejected.
- Token validation uses a **constant-time comparison** (`MessageDigest.isEqual`) to avoid timing side-channels.
- The handshake enforces a **same-origin check** on the `Origin` header, mitigating Cross-Site WebSocket Hijacking.
- Per-connection **rate limiting** and a **64 KB frame cap** bound the impact of a misbehaving client; idle sockets time out after 5 minutes.
- Local network only – nothing leaves your LAN.
- The HTTPS keystore (`orbion.p12`) is **generated locally** by `start-https.bat` and is **excluded from version control** via `.gitignore`. Its password is read from the `ORBION_KEYSTORE_PASSWORD` environment variable. Never commit certificates, keystores or `.env` files to the repository.

See [SECURITY.md](SECURITY.md) for the full threat model and reporting process.

## Tech stack

- Java 21 · Spring Boot 3 · Maven
- Spring WebSocket · Java AWT Robot · ZXing (QR codes)
- Vanilla HTML/CSS/JS mobile UI · Web Speech API

## Build & run (development)

Requirements: **JDK 21+** and **Maven**.

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/orbion.git
cd orbion

# 2. Build and run
mvn clean package
java -jar target/orbion.jar
```

The dashboard window opens with the QR code. The pairing URL is also printed to the console:

```
Pairing URL: http://192.168.1.x:8080/?token=<session-token>
```

Make sure your phone is on the **same Wi-Fi network**, then scan the QR code.

## Build the Windows installer

Requirements: JDK 21+ (includes `jpackage`) and the [WiX Toolset 3.x](https://wixtoolset.org/) on PATH.

```bat
build-installer.bat
```

This produces `dist\OrbionSetup.exe` with a **bundled Java runtime** – end users do not need Java installed.

## Project structure

```
src/main/java/io/orbion/
├── OrbionApplication.java        # Entry point (non-headless Spring Boot)
├── core/
│   ├── SessionService.java       # Token, device count, command log
│   ├── CommandExecutor.java      # Robot key presses + clipboard paste
│   ├── QrCodeService.java        # QR code image generation
│   └── NetworkUtils.java         # LAN IP detection
├── ws/
│   ├── WebSocketConfig.java      # /ws endpoint registration
│   ├── TokenHandshakeInterceptor.java  # Token auth on handshake
│   └── ControlWebSocketHandler.java    # Command protocol
└── ui/
    └── TrayManager.java          # System tray + desktop dashboard

src/main/resources/static/        # Mobile web UI (index.html, css, js)
build-installer.bat               # jpackage installer script
```

## WebSocket protocol

Endpoint: `ws://<ip>:8080/ws?token=<session-token>`

```jsonc
// Phone → Desktop
{ "type": "key",  "command": "ENTER" }
{ "type": "text", "value": "Run Java tests" }

// Desktop → Phone
{ "type": "status", "connected": true, "devices": 1 }
{ "type": "ack", "command": "ENTER", "ok": true }
```

## Gestures

- **Tap** an arrow – single key press
- **Hold** an arrow – auto-repeat (like holding a real key)
- **Double-tap RIGHT** – switch to next window (ALT+TAB)
- **Double-tap LEFT** – switch to previous window (ALT+SHIFT+TAB)

## Enable voice mode (HTTPS)

Browsers only allow microphone access on **secure origins**, so voice does not work over plain `http://<wifi-ip>`. To enable it:

```bat
start-https.bat
```

This generates a self-signed certificate (`orbion.p12`) on first run and starts Orbion on `https://<ip>:8443`. Re-scan the QR code, accept the browser's certificate warning once (Advanced → Proceed), then tap the mic – the permission prompt will now appear.

Alternative without HTTPS (Chrome on Android only): open `chrome://flags/#unsafely-treat-insecure-origin-as-secure`, add `http://<your-pc-ip>:8080`, and relaunch Chrome.

## Troubleshooting

- **Phone can't connect** – ensure both devices are on the same network and Windows Firewall allows Java. Orbion prefers port `8080` (or `8443` in HTTPS mode); if that port is already in use it **automatically falls back to a free port**, so always use the exact pairing URL/QR code shown on the dashboard rather than assuming `8080`.
- **Voice not working** – use `start-https.bat` (see "Enable voice mode" above); the Web Speech API requires Chrome/Edge on Android or Safari on iOS.
- **Keys not registering** – the target app must have focus on the desktop.
- **Headless server** – the web server still runs; tray, dashboard and key simulation are disabled.

## Contributing

Contributions are welcome!

1. Fork the repository and create a feature branch: `git checkout -b feature/my-change`
2. Make your changes and verify the build: `mvn clean package`
3. Commit with a clear message and open a pull request.

Please do **not** commit generated artifacts or secrets (`target/`, `dist/`, `orbion.p12`, `.env`) – these are already covered by `.gitignore`.

## License

Released under the [MIT License](LICENSE).

