# Security Policy

Orbion turns a phone into a remote keyboard/mouse for the desktop it runs on.
Because it can inject input into the focused application, its security model is
deliberately conservative: it is a **local-network tool** and is not designed to
be exposed to the public internet.

## Threat model

**Assets**
- The desktop's keyboard/mouse input stream (the app injects real key/mouse events).
- The clipboard (text mode pastes via CTRL+V).
- The per-launch session token.

**Trust boundary**
- Trusted: the desktop running Orbion and any device that has scanned the QR code.
- Untrusted: every other host on the LAN, and any website open in a paired
  phone's browser.

**Controls**

| Threat | Control |
|--------|---------|
| Unauthorized device connects | 128-bit `SecureRandom` session token required on the WebSocket handshake; new token every launch |
| Token guessing / timing attack | Constant-time comparison via `MessageDigest.isEqual` |
| Cross-Site WebSocket Hijacking | Same-origin check on the `Origin` header during the handshake |
| Malicious/compromised client flooding input | Per-connection rate limit (200 msg/s), 64 KB frame cap, 5 min idle timeout |
| Credentials in the repo | Keystore/certs are git-ignored; the HTTPS keystore is generated per-user under `~/.orbion` with a random password (`orbion.p12.pass`) |
| Eavesdropping on voice/text | HTTPS/WSS **by default**, using a self-signed certificate auto-generated on first launch |

## Known limitations

- The session token appears in the pairing URL and is shown on the desktop
  dashboard by design; treat the screen and any QR code as sensitive.
- HTTPS uses a **self-signed certificate** generated on first launch, so phones
  show a one-time "connection is not private" warning that must be accepted.
  This is expected for a private LAN tool – a publicly-trusted certificate
  cannot be issued for a local IP address.
- The installer is **not code-signed**, so Windows SmartScreen / Smart App
  Control will warn or block it. This is a distribution/cost matter, not a code
  issue; run from source (`java -jar target/orbion.jar`) to bypass it entirely.
- Orbion is intended for a trusted LAN. Do **not** port-forward it or expose it
  to the internet.

## Reporting a vulnerability

Please report security issues privately by opening a
[GitHub security advisory](../../security/advisories/new) rather than a public
issue. Include reproduction steps and the affected version. We aim to
acknowledge reports within a few days.
