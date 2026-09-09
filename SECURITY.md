# Security Policy

## Supported Versions

Titan is currently a pre-1.0 project. Security fixes are applied to the latest
released minor version. Older minor versions may not receive a backport.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Older releases | No |

## Reporting a Vulnerability

Please use GitHub's
[private vulnerability reporting](https://github.com/traffic-hunter/titan/security/advisories/new)
for suspected security issues. Include the affected Titan version, relevant
configuration, reproduction steps, and the impact you observed.

Do not open a public issue before the report has been reviewed. If private
reporting is unavailable, open an issue containing no exploit details and ask
a maintainer for a private contact channel.

You should receive an acknowledgement within seven days. The maintainers will
then assess the report, agree on disclosure timing with the reporter when
possible, and publish remediation guidance with the fix.

## Scope

Useful reports include vulnerabilities in Titan's network protocols, TLS and
WebSocket handling, authentication boundaries, queue management API, release
artifacts, and supported client libraries. Dependency vulnerabilities without
a demonstrated effect on Titan may be handled through routine dependency
updates instead.
