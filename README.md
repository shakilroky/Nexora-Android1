# Nexora Android App v1.1.0

Nexora is an Android SMS bridge for the Nexora WordPress/WooCommerce payment verification system.

## Core capabilities
- Multi-website configuration (10+ sites supported by architecture)
- Multiple device credentials
- bKash / Nagad / Rocket SMS detection
- Transaction ID and amount parsing
- HMAC-SHA256 signed SMS sync
- Nexora site/device/event headers
- Per-site gateway and sender filtering
- Connection ping
- Encrypted-at-rest device secrets
- GitHub Actions APK build

## Build without Android Studio
The included GitHub Actions workflow builds `app-debug.apk` using Gradle 8.10 and uploads it as an artifact.

## Important
The WordPress Nexora plugin remains authoritative for payment verification. The Android app only forwards SMS evidence; it must not mark an order paid locally.

Before production, verify the exact `/android/pair`, `/android/sms-sync`, and `/android/ping` JSON contract against the installed Nexora plugin.
