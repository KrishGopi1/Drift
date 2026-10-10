# Security policy

Drift stores sleep and health-related data on your phone, so security problems matter even though the app
never connects to the internet.

## Supported versions

Only the latest release gets security fixes.

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Report it privately through GitHub:
[**Report a vulnerability**](https://github.com/KrishGopi1/Drift/security/advisories/new)
(Security tab → Report a vulnerability). Only the maintainer can see the report.

Please include:

- What the problem is and what an attacker could do with it
- Steps to reproduce, or a proof of concept
- The Drift version and Android version you tested on

You can expect an acknowledgement within 7 days. Once a fix is released, you'll be credited in the advisory
unless you'd rather not be.

## In scope

- Access to Drift's data by other apps (exported components, intents, content providers, widgets)
- Weaknesses in the encrypted database (SQLCipher) or how its key is stored (Android Keystore)
- Backups or exports that leak data in plain text
- Anything that makes the app send data off the device

## Out of scope

- Attacks that need a rooted or already-compromised phone
- Data you chose to share in a public issue or discussion
- Vulnerabilities in Health Connect, Sleep as Android or Android itself (report those to their owners)
