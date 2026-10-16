# BlendCraft owner release and Premium architecture

## Permanent BlendCraft signing identity (created 2026-10-16)
The owner has a 3072-bit RSA private signing key in a password-encrypted PKCS#12 backup named `BlendCraft-SnoPlay-permanent-signing-key.p12` (held privately by the owner, **never in Git**). The key entry alias is `blendcraft-release`; Android signing certificate SHA-256 fingerprint:

`E1:03:D0:0D:6B:C8:09:00:22:9B:B4:31:78:3A:E0:E2:08:A8:CC:26:11:67:63:06:82:3A:F9:82:71:A8:AE:6C`

Use the **same exact PKCS#12 file**, not a newly generated file, for every subsequent release. The owner-selected phrase protects the keystore: it is **not** the actual cryptographic signing certificate, and it must never be committed to source control. A new key with the same password will not update the old app. Early debug-signed APKs may need to be replaced **once**; thereafter the same package name, signing cert, and incremented versionCode enable install-over-the-top updates.

The 1.3.0 SnoPlay and Play test APKs were re-signed with this certificate, with Android APK Signature Scheme v2 RSA/SHA-256 and local signature/content checks; these were **not** installed or tested on a physical device.

## Owner-only software updates
Android does **not** accept an arbitrary text password as proof of a legitimate software update.
Updates must be signed by the **same signing certificate** as the installed application.
The `release-android.yml` workflow is restricted to GitHub user `lordglyth` and
is configured to use a protected GitHub Actions environment, `blendcraft-production`.
For an actual deployment, require environment approval / 2FA and store the
keystore and passwords exclusively in **GitHub Actions encrypted secrets**:

- `BLENDCRAFT_KEYSTORE_BASE64` — entire Java signing keystore encoded as base64.
- `BLENDCRAFT_STORE_PASSWORD` — keystore password.
- `BLENDCRAFT_KEY_ALIAS` — signing alias.
- `BLENDCRAFT_KEY_PASSWORD` — key password.

Never put any of those values, a human passkey, PayPal client secret, or
an admin password into an APK or a public repository. Never hardcode any owner password or passphrase in the app, documentation, a commit, or build logs. Use GitHub protected secrets for production signing.

This workflow has not yet run as a signed production release: the requisite
secrets and a protected deployment environment are not currently configured.
The workflow must live on the **default branch of a dedicated app repository**
before manual dispatch is generally available. Keep the current development
branch separate from the existing GitHub profile site.

## Distribution
- **Google Play:** `com.snoplay.blendcraft` (Play flavor). Premium digital
  purchases use **Google Play Billing**, and a Play Developer API backend
  verifies purchase tokens before entitlements are granted.
- **SnoPlay direct:** `com.snoplay.blendcraft.snoplay` (SnoPlay flavor).
  Premium purchases use **PayPal Checkout**, with orders created and captured
  server-side and a verified entitlement stored in the backend.
- The two package names are intentionally different so a Play build signed
  by Google cannot overwrite a separately signed SnoPlay build. User identity
  and an entitlement service will be needed if paid access should transfer
  between distributions.

Both binaries can be installed freely and have basic editing and an enforced
bottom-right BlendCraft watermark. Paying will unlock watermark-free exports
and premium features only after server-side verification is built and enabled.

## Content and settings updates
If the owner wants to push new meme templates or filter packs without publishing
a new APK, create an owner-only dashboard protected by a hosted identity provider
and a second factor. Authorize every mutation server-side. A static passkey in
the distributed Android client is not an access-control mechanism.

## Development builds vs production builds
The regular workflow builds **debug** APKs to verify compilation. Each GitHub
runner may generate a distinct debug signing key; those are not durable release
keys, and customers may have to uninstall old debug versions. For a real launch,
use the same production signing credentials for every SnoPlay update.
Google Play's signing service protects its signing key and the Play upload key
must be retained for subsequent uploads.

## Next merchant activation requirements
- Google Play Console developer app + managed product `premium_lifetime`.
- PayPal business/developer REST app + Checkout credentials (server-side only).
- Backend identity, paid entitlement records, and purchase-token validation.
- A chosen price and terms; no checkout should charge a user until entitlement
  restoration and payment verification are tested.

## Planned premium features
Watermark-free exports, custom watermark/logo, batch export, premium filters,
templates and high-resolution rendering. The current 1.3 test artifacts
still enforce the **Free** watermark for everybody until actual purchase
verification exists. They are not commercial-ready.
