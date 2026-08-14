# Nexora Android v1.2.0

This version is aligned with the Nexora plugin handshake response:
- Reads `/wp-json/wpct/v1/android/ping` using GET, with POST fallback.
- Parses `status`, `handshake`, `ready`, `site_id`, `site_name`, `site_url`, `version`, and server-provided `endpoints`.
- Uses the server-provided `pair`, `sync`, and `ping` URLs instead of guessing them.
- Supports single-use pairing code flow.
- Stores the returned device ID/secret for authenticated SMS sync.
- Supports multiple websites on one phone.
- SMS sync remains HMAC-SHA256 signed and server-authoritative.

## GitHub build
Use `.github/workflows/build-apk.yml`, then Actions -> Build Nexora APK -> Run workflow.
