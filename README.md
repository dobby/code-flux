# Code Flux

Code Flux is a local engineering analytics dashboard for Git repositories. It ingests repository history, deduplicates repeated patch content, attributes work to authored time, and renders interactive throughput views with filtering, comparison periods, annotations, sync control, and configurable author/repository mapping.

## Stack

- Backend: Kotlin, Spring Boot, Flyway, SQLite
- Frontend: Vue 3, Pinia, Vite, ECharts, Chart.js
- Packaging: Spring Boot serves the built frontend, with an Electron wrapper for macOS distribution

## Features

- Repository sync with progress and stop support
- Authored-date analytics instead of merge-date attribution
- Patch-id deduplication to reduce double counting across merges and cherry-picks
- Filters for authors, categories, product groups, and individual repositories
- Comparison periods with summary deltas
- Timeline annotations
- Config editor in the UI
- Optional author anonymization in the dashboard
- Switchable chart renderer: ECharts or Chart.js

## Project Layout

```text
backend/   Spring Boot API, sync engine, persistence, tests
desktop/   Electron wrapper, packaging config, app resources
frontend/  Vue app, charts, state, UI tests
config/    Sample configuration
scripts/   Dev, build, and run helpers
```

## Prerequisites

- Java 21+
- Node.js + npm
- Git
- GraalVM JDK 21+ with `native-image` for native backend and macOS app packaging

## Configuration

1. Copy `config/config.example.yaml` to `config/config.yaml`.
2. Fill in repositories, authors, and defaults for your environment.
3. If your remotes require HTTPS auth, provide credentials through environment variables instead of committing secrets.

`config/config.yaml` is intentionally gitignored and not part of the public repository.

## Development

Start the frontend and backend in development mode:

```bash
./scripts/dev.sh
```

Open the same dev stack in an app-style Chromium window:

```bash
./scripts/dev-app.sh
```

`./scripts/dev-app.sh` looks for Google Chrome, Microsoft Edge, or Brave on macOS and launches Code Flux in standalone app mode.

Open the dev stack in Electron instead of Chromium app mode:

```bash
./scripts/dev-electron.sh
```

## Build

Build the frontend and package the backend jar:

```bash
./scripts/build-release.sh
```

Build the frontend, the backend jar, and the GraalVM native executable used by the Electron wrapper:

```bash
./scripts/build-native-backend.sh
```

Build the installable macOS Electron app and DMG using the native backend:

```bash
./scripts/build-mac-app.sh
```

Build the Electron wrapper in jar fallback mode instead of native mode:

```bash
CODE_FLUX_ELECTRON_BACKEND_MODE=jar ./scripts/build-mac-app.sh
```

The jar fallback keeps the packaging flow available during native rollout, but it still requires Java 21+ on the target machine.

## Run

Run the packaged dashboard with an explicit config file:

```bash
APP_CONFIG_FILE="$PWD/config/config.yaml" ./scripts/run-dashboard.sh
```

The scripts default to backend port `8086` to avoid conflicts with local projects.

You can still override the backend port if needed:

```bash
SERVER_PORT=8086 APP_CONFIG_FILE="$PWD/config/config.yaml" ./scripts/run-dashboard.sh
```

For the packaged Electron app, user config and app data live under:

```text
~/Library/Application Support/Code Flux
```

## Verification

Backend tests:

```bash
cd backend
./gradlew test
```

Backend native image build:

```bash
./scripts/build-native-backend.sh
```

Frontend production build:

```bash
cd frontend
npm run build
```

Frontend end-to-end tests:

```bash
cd frontend
npm run test:e2e
```

## Notes

- This project is intended for local/private repository analytics.
- Throughput metrics are operational signals, not performance scores.
- Large first-time syncs can take noticeably longer than incremental updates.
