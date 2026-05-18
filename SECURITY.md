# Security Policy

## Reporting a Vulnerability

Please do not report security issues through public GitHub issues.

Send the report privately to the maintainer with:

- affected version or commit
- impact
- reproduction steps
- any relevant logs or screenshots

Avoid sharing Marketplace tokens, private keys, certificate passwords, or user data in public channels.

## Secret Handling

The following values must never be committed:

- `PUBLISH_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`
- local signing files

Use GitHub Secrets or a protected GitHub Environment for release credentials.

