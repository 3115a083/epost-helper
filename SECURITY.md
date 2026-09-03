# Security Policy

Only the latest release is supported.

Do not open a public issue for vulnerabilities that could expose E-POST or LetterXpress credentials, document contents, certificate material, backup material, or enable unauthorized mail submission. Use GitHub private vulnerability reporting.

Transport is HTTPS/IPPS or SSH/SFTP only, with TLS 1.2/1.3 system trust and hostname verification where applicable, strict SSH host-key verification for SFTP, plus optional HTTPS SPKI pinning. Cleartext transport and certificate-bypass switches are not supported.

Profile data is AES-256-GCM encrypted at rest using a non-exportable Android Keystore key.

Manual backup files are password protected with PBKDF2-HMAC-SHA256 and AES-256-GCM. They may contain authentication credentials including API keys, so they must be treated as sensitive even though encrypted.

Android cloud backup and device-transfer are allowlisted to a separate portable-state preference. LetterXpress API keys are removed before that state is written. Prepared PDFs, the Android-Keystore-encrypted profile store, cache files and app-private document files are excluded from platform backup.
