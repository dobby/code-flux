# Code Flux

Code Flux is a local engineering analytics dashboard for Git repositories. It ingests repository history, deduplicates repeated patch content, attributes work to authored time, and renders interactive throughput views with filtering, comparison periods, annotations, sync control, and configurable author/repository mapping.

## Stack

- Backend: Kotlin, Spring Boot, Flyway, SQLite
- Frontend: Vue 3, Pinia, Vite, ECharts, Chart.js
- Packaging: Spring Boot serves the built frontend

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
frontend/  Vue app, charts, state, UI tests
config/    Sample configuration
scripts/   Dev, build, and run helpers
```

## Prerequisites

- Java 21+
- Node.js + npm
- Git

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

## Build

Build the frontend and package the backend jar:

```bash
./scripts/build-release.sh
```

## Run

Run the packaged dashboard with an explicit config file:

```bash
APP_CONFIG_FILE="$PWD/config/config.yaml" ./scripts/run-dashboard.sh
```

You can override the backend port if needed:

```bash
SERVER_PORT=8086 APP_CONFIG_FILE="$PWD/config/config.yaml" ./scripts/run-dashboard.sh
```

## Verification

Backend tests:

```bash
cd backend
./gradlew test
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
