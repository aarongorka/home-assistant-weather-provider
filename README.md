# HA Weather Provider

Android [Gadgetbridge weather provider](https://gadgetbridge.org/basics/integrations/weather/) sourcing data from Home Assistant.

## Use

1. Create a long-lived token in Home Assistant (Profile → Security).
2. Open the app, enter URL and token, tap **Load entities**, pick your entities
3. **Save and enable sync** (periodic, hourly minimum, for battery life).
4. In Gadgetbridge, enable weather and set a location (Settings → Weather).

Delivery is via Gadgetbridge's generic weather broadcast (`ACTION_GENERIC_WEATHER`).

## Build

Locally: `gradle ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`.

## Release

Conventional commits on `main` drive release-please. Merging its PR tags and attaches a signed APK.
Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (unsigned-to-debug-key fallback if absent).
