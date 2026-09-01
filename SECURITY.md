# Security Policy

Only the latest release is supported.

Do not open a public issue for vulnerabilities that could expose E-POST credentials, document contents, certificate material, or enable unauthorized mail submission. Use GitHub private vulnerability reporting.

Transport is HTTPS/IPPS only with TLS 1.2/1.3, system trust and hostname verification, plus optional SPKI pinning. Profile data is AES-256-GCM encrypted using Android Keystore. Backups are disabled. There is no certificate-bypass option.
