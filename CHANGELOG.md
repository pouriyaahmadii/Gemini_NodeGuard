# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Full Privacy-First Per-Fork CI/CD Pipeline**: GitHub Actions workflows designed specifically for zero-metadata-leakage via masking, Secret Gist integrations, and `age` encrypted artifacts.
- Custom Probe Definitions via JSON mapping with exact redirect, keyword, and auth status evaluation support.
- Hysteria2, WebSocket Early-Data (`ed`), VMess `type=http` obfs, and Shadowsocks plugin compatibility parsing.
- Robust round-trip export fidelity preventing loss of custom config variables (e.g. SNI or fingerprints).
- TCP Pre-filter worker pool that accurately captures `dns_error` vs `conn_error` before heavy dialing.
- Embedded runtime E2E test `-selftest` pipeline utilizing HTTP mocks and isolated `net.Listen` stubs.
- Adaptive node concurrency reservoir sampling scaling up to 32 parallel instances per box.

### Changed
- Re-architected core `main.go` monolith into an orchestrated, observable `pipeline` package tracking rich structured report metadata.
- Re-routed URL generation in `exporter` to use standard `url.QueryEscape` solving URL whitespace issues across proxy clients.
- Hardened Sing-Box and Xray dialers removing "2s wait loops" replacing them with deterministic process exit checks.

### Fixed
- Fixed blindly trusted 302 redirects leading to "Google Sorry" interstitials reporting incorrectly as `confirmed_compatible`.
- Remedied silent drops when `dialers` were empty, exiting abruptly now.
- Solved false negative read lockups on slow runners via separate dial and probe timeouts (`-dial-timeout`, `-probe-timeout`).
- Fixed Shadowsocks legacy full-base64 fallback parsers.
