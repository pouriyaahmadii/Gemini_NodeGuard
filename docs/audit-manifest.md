### Phase 1: Detection Engine Overhaul
- Rewrite probe layer to handle manual redirect following (max 4 hops) and keyword scanning on bodies.
- Refactor registry to use data-driven evaluators loaded from JSON (-registry support).
- Implement timeout split (-dial-timeout, -probe-timeout, -node-timeout) and probe confirmation logic (-confirm N) with jittered retries for transport errors.
- Fix readiness loops in sing-box and xray dialers to fail fast on process exit and capture stderr.
- Ensure checker fatally exits if no dialers are available.
- Add tests for registry custom JSON loading, response evaluation, and dialer testing.
- `go test -race ./...` passing.
- `go vet ./...` passing.

### Phase 2: Protocol Completeness & Round-Trip Fidelity
- Extended types.ProxyNode with Plugin, PluginOpts, MaxEarlyData, EarlyDataHeaderName, and ObfsType.
- Implemented hysteria2 parser.
- Added vmess http-obfs, ws early data (ed), and shadowsocks plugin support to parsers.
- Fixed Shadowsocks legacy full-base64 parsing.
- Updated sing-box and xray config builders for h2->http mapping, early-data, and explicit protocol support guards.
- Overhauled DedupKey to include all identity-bearing fields.
- Fixed exporter reconstructed URIs (VMess obfs/ed, SS plugins) and replaced PathEscape with QueryEscape for remarks.
- `go test -race ./...` passing.
- `go vet ./...` passing.

### Phase 3: Performance, Reliability & Resource Safety
- Implemented TCP prefilter with its own worker pool and detailed metric outcomes (dns/conn_error/timeout).
- Hardened downloader with SHA-256 digests, retries, timeout contexts, and atomic tmp file rename.
- Improved fetcher with retry logic honoring specific HTTP errors and jitter.
- Applied URL redaction across logs and error diagnostics for privacy.
- Refactored cmd/checker/main.go logic into internal/pipeline for a cleaner entrypoint and better testing.
- Added structured JSON report support (-report), exit codes, and concurrency tuning/caps (-max-nodes).
- `go test -race ./...` passing.
- `go vet ./...` passing.

### Phase 4: Privacy-First Per-Fork Automation (CI/CD)
- Created `.github/workflows/check.yml` with workflow dispatch, schedule triggers, `age` encryption, Secret Gist integration, and aggregate JSON summaries.
- Implemented strict input masking for URLs and added `NG_SUB_URLS` secret fallback logic.
- Configured caching based on core binary versions extracted directly from the CLI.
- Verified clean repository state post-run to ensure no accidental leaks occur via git.
- Added `.github/workflows/lint.yml` (gofumpt and golangci-lint).
- Re-wrote `.github/workflows/test.yml` with coverage gates, race detection, and E2E selftest commands on multi-OS.
- Created `.github/workflows/release.yml` for CGO=0 native runner builds, generating compressed artifacts (UPX for Linux/Windows) and producing `sha256sums.txt`.
- Drafted `docs/FORK_GUIDE.md` to map the exact click-paths for forkers setting up Secrets and Gists safely.

### Phase 5: Testing Blitz
- Implemented fully contained E2E `-selftest` pipeline verifying file generations and JSON reports using `net.Listen` and `httptest` listeners.
- Added new edge case tests covering configs and checker.
- `go test -race ./...` passing on all layers with no data races or leakages.
- Note: Current coverage targets pending ongoing effort but critical path coverage complete for pipeline E2E testing.

### Phase 6: Docs & Portfolio Polish
- Drafted a detailed `README.md` containing architecture diagrams (Mermaid), clear CLI flag documentation, and a threat model statement.
- Added `docs/README.fa.md` mapped to Persian users bypassing geoblocks.
- Generated `CHANGELOG.md` utilizing the standard Keep a Changelog format outlining the sweeping changes of this major refactor.
- Appended `LICENSE` file reflecting MIT license constraints (Copyright owner pouriyaahmadii).
- Built a standardized `Makefile` to quickly trigger tests, data races, lintings, and self-tests locally.
- Injected `.golangci.yml` linting definitions and `.github/dependabot.yml` configurations for ongoing automated health checks.
