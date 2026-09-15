# Gemini Sub Checker

## Overview
Gemini Sub Checker is a Go utility designed for V2Ray users who need reliable connectivity to Google AI services. It automatically probes V2Ray nodes to ensure they can connect to Gemini and AI Studio without hitting geoblocks or HTTP 403 errors, outputting a clean subscription list of verified nodes.

## Features
- **Automated V2Ray Parsing**: Fetches, decodes, and parses vmess, vless, trojan, ss, and ssr URIs.
- **Gemini Compatibility Probing**: Verifies connectivity against Google AI endpoints using `sing-box`.
- **Clean Base64 Export**: Generates a standardized, Base64-encoded subscription file compatible with all major clients.
- **Automated CI/CD**: Fully automated via GitHub Actions to continuously update subscriptions.

## Setup & Secrets
To configure this tool for your private V2Ray subscriptions using GitHub Actions:

1. Fork this repository.
2. Go to your repository's **Settings > Secrets and variables > Actions**.
3. Create a new repository secret named `SUB_URLS`.
4. Paste your private subscription links (comma-separated if multiple) into the value.

The workflow will prioritize this secret over the local `config.json`.

## Client Subscription
Once the automated pipeline runs successfully, your curated subscription files will be hosted on the `sub` branch of your repository.

You can subscribe to these permanent raw links in your preferred V2Ray client (like Happ, V2Box, or v2rayN):

**1. Gemini-Compatible Nodes**
Contains ONLY the verified nodes that work with Google AI (prefixed with `[Gemini]`).
`https://raw.githubusercontent.com/<USERNAME>/<REPO>/sub/gemini-sub.txt`

**2. General Nodes**
Contains the remaining working nodes that were geoblocked or gave HTTP 403 to Google AI, but still successfully function as a proxy (prefixed with `[General]`).
`https://raw.githubusercontent.com/<USERNAME>/<REPO>/sub/general-sub.txt`

*(Replace `<USERNAME>` and `<REPO>` with your GitHub username and repository name)*

## Automated Scanning
A built-in GitHub Actions workflow (`.github/workflows/check-and-publish.yml`) runs **every 4 hours** automatically.

During the scan, it checks the connectivity of your subscription nodes and tests them against Google AI services. The results are separated into two distinct subscription files as detailed above. Dead nodes that fail to connect entirely are discarded.
