# E-POST Helper

Native Android app for sending PDF documents through existing accounts at **Deutsche Post E-POST MAILER** or **LetterXpress**. Supported transports are E-POST WebDAV/IPP and LetterXpress API/SFTP. Designed for F-Droid and built only from open-source Android dependencies.

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

Cleartext `http://` and `ipp://` are rejected. Android cleartext networking is disabled. TLS 1.2/1.3, normal Android CA validation and hostname verification are enforced. The app has no “ignore certificate errors” switch. Optional per-profile SPKI pinning is available. Credentials, URLs and profile metadata are encrypted at rest with AES-256-GCM and a non-exportable Android Keystore key. Manual backups are password-protected with PBKDF2-HMAC-SHA256 plus AES-256-GCM. Android cloud/device-transfer backup is restricted to a sanitized portable state and explicitly strips LetterXpress API keys.

Certificate pinning is optional because pins must be rotated before the service certificate changes.

## E-POST MAILER 7.0 integration

The implementation follows Deutsche Post's E-POST MAILER 7.0 user manual, version 7.0, March 2026.

- **Sammelkorb:** WebDAV target using the administrator-provided URL and optional username/password. The connection test performs a WebDAV probe and reads `Info.txt` for the configured shipping options and `README.txt` for collection-basket instructions/series-letter separator when available.
- **Netzwerkdrucker:** secure IPP request to the administrator-provided printer URL. The connection test performs an IPP Get-Printer-Attributes request before a profile is used.
- **Shipping options:** colour, simplex/duplex and registered-mail variants are properties of the configured E-POST target. Local profile labels are selection aids; they do not pretend to override server configuration.
- **Address correction:** E-POST MAILER 7.0 uses its address repositioning tool and saved correction templates. E-POST Helper therefore does not apply arbitrary local X/Y offsets to PDFs.

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


## LetterXpress

Profiles can use either the LetterXpress REST API v3 or SFTP.

### API

Create an API key in the LetterXpress customer area and enter the LetterXpress username and API key in the profile. The Android advanced print options expose simplex/duplex, colour, registered mail and C4. When the document page count is known, the app queries the LetterXpress price endpoint and displays the expected price before sending. Print jobs are submitted to `POST /v3/printjobs`.

### SFTP

LetterXpress SFTP uses `sftp.letterxpress.de` on port `279`. The app uses FILECODE filenames to transfer simplex/duplex, colour, national/international, C4 and registered-mail options. A pinned SSH host-key fingerprint is required before document upload. The connection test can discover the server fingerprint without accepting it for production transfer; compare it independently before storing it.

## Android printing and outbox

Every active profile appears as an Android printer. Android's standard print controls own color/monochrome and simplex/duplex. Provider-specific options such as registered mail and LetterXpress C4 are exposed through the advanced print options activity.

The in-app outbox uses a three-step workflow:

1. Select one or more PDFs already in the outbox.
2. Reorder them, remove files from the current shipment and review the merged first-page preview.
3. Choose print options, inspect or correct the address layout, compare compatible profiles and estimated prices, then send.

A Storage Access Framework folder can be configured as an import root. The app does not scan it on every start. Import is triggered manually from the prepared-output cart. Optional one-level subfolders encode common print presets such as color/monochrome, simplex/duplex, national/international, registered mail and address correction. The `debug` folder is never imported. Auto-imported source files are deleted only after successful submission.

## Address layout correction

Profiles can store sender and recipient source rectangles from a representative PDF. During the final outbox step the app can locally reposition those areas before sending. Only the two small address regions are rasterized at print resolution; the rest of the PDF remains unchanged.

The address editor has separate source-selection and target-position modes. Target mode renders the actual moved sender/recipient content and overlays the provider's visible window plus postage/DV-franking zone. Collisions or clipping are shown in red. The same coordinates are used by the final PDF transformation before send.

## Shipment history

LetterXpress API profiles can show recent jobs, status, balance and reported costs. The home hero provides paged statistics for the current month, previous month and current year. Per API profile, statistics can use only locally recorded sends or replace that profile's local LetterXpress events with server history to avoid duplicate counting.

E-POST MAILER 7.0 documents its Journal only inside the Post & DHL business customer portal. No public Journal endpoint is documented for the WebDAV/IPP client workflow, so this app does not scrape the portal or store portal session credentials. A mixed Post/LetterXpress history will only be added if Deutsche Post provides a supported machine-readable Journal interface.

## Android advanced print options

Each active profile appears as a printer. Android's advanced print options activity is used for per-document provider settings instead of creating a separate virtual printer for every option combination. Currently implemented per-job options are simplex/duplex, colour, supported registered-mail modes and LetterXpress C4. LetterXpress API profiles can show a provider price quote; SFTP profiles show a public-list-price estimate.

The PDF address helper stores sender and recipient source areas for the user's layout. Automatic PDF repositioning is intentionally not enabled yet, so the UI does not claim that stored address regions are already applied to outgoing documents.


## Backup and migration

Settings, profiles and local send statistics can be exported to a password-protected `.epostbackup` file. The export includes credentials, including API keys, and is encrypted with PBKDF2-HMAC-SHA256 plus AES-256-GCM. Keep the backup password separately.

For Android encrypted cloud backup and device-transfer workflows such as Android device migration or vendor transfer tools, the app writes a separate portable state. It includes settings, statistics and profile configuration, but LetterXpress API keys are removed before that state becomes eligible for platform backup. Prepared PDFs are never included.

## Prepared output cart

The bottom navigation contains a prepared-output cart. A prepared letter can be edited, deleted or sent later. Multiple documents can either remain separate letters or be merged for the same recipient. When duplex is selected, keeping source documents on separate physical sheets is optional; if enabled, an odd-page source document gets a blank separator page before the next document.
