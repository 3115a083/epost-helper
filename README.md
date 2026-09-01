# E-POST Helper

Native Android app for submitting PDF documents to configured **E-POST MAILER** collection baskets (WebDAV) or network printers (IPP). Designed for F-Droid and built only from open-source Android dependencies.

> **Independent project.** This is not an official Deutsche Post AG app and is not affiliated with or endorsed by Deutsche Post AG.

## Features

- Open/select PDFs and submit them through a configured E-POST target.
- Android PrintService: each active profile appears as a virtual printer.
- Multiple profiles for server-side variants such as one-sided, duplex, colour, or registered mail.
- Sammelkorb via HTTPS WebDAV PUT.
- Network printer via IPP 2.0 Print-Job over IPPS/HTTPS.
- Optional SHA-256 SPKI certificate pinning.
- AES-256-GCM encrypted profile/credential storage using Android Keystore.
- Light/dark Material 3 UI.

## Security model

Cleartext `http://` and `ipp://` are rejected. Android cleartext networking is disabled. TLS 1.2/1.3, normal Android CA validation and hostname verification are enforced. The app has no “ignore certificate errors” switch. Optional per-profile SPKI pinning is available. Credentials, URLs and profile metadata are encrypted at rest with AES-256-GCM and a non-exportable Android Keystore key. Backups/device transfer of the profile store are disabled.

Certificate pinning is optional because pins must be rotated before the service certificate changes.

## E-POST configuration model

E-POST MAILER 7.0 documents the Sammelkorb as a WebDAV folder and the network printer as an IPP printer. Versandoptionen such as duplex, colour, registered mail and address-window repositioning belong to the E-POST target configured by the administrator. E-POST Helper maps one local profile to one such server-side target.

The optional address-window fields in the Android profile are therefore identification/documentation values. The app deliberately does not rasterize or rewrite arbitrary PDFs because that can reduce print quality or change document content. Configure the effective sender/recipient repositioning on the corresponding E-POST target.

## Build

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds and uploads a test APK on every push and pull request.

## First setup

1. Install the APK.
2. Add one profile per E-POST target.
3. Choose Sammelkorb/WebDAV or Netzwerkdrucker/IPP.
4. Enter the HTTPS/IPPS URL and credentials supplied by the E-POST administrator.
5. Optionally add an SPKI pin as `sha256/BASE64_HASH`.
6. Enable **E-POST Helper** in Android print settings if you want the app to appear as a printer.

## F-Droid

The app has no Play Services, analytics, ads, proprietary push service or non-free runtime dependency. Before F-Droid submission, create a signed tag/release, bump `versionCode`/`versionName`, publish matching source, and keep signing keys outside this repository.

License: GPL-3.0-or-later.

## Repository hardening

Included: Android CI, CodeQL, Dependabot, signing-key exclusions and a security policy. Repository-owner settings should additionally protect `main`, require CI before merge, block force-push/delete, enable secret scanning/push protection/private vulnerability reporting, and keep signing keys outside Git.

## Limitations

E-POST may change endpoints, authentication, IPP behaviour, or validation rules. Test each target with E-POST’s own test mode before production use. Paid services are determined by the E-POST target configuration, not by local labels.

See `SECURITY.md` for vulnerability reporting.
