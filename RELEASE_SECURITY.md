# BlendCraft owner release and Premium architecture

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
an admin password into an APK or a public repository. The older passkey posted
in chat is **not** used for release signing and should not be reused as a secret.

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
