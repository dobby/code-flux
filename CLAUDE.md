# Code Flux

Local-first development telemetry dashboard. Kotlin/Spring Boot backend, Vue 3 frontend, Electron desktop shell.

## Tech Stack

- **Backend**: Kotlin 2.3, Spring Boot 3.5, Java 21, SQLite (JDBC), GraalVM native image
- **Frontend**: Vue 3, TypeScript, Vite, Tailwind CSS, ECharts/Chart.js, Pinia
- **Desktop**: Electron 36, electron-builder
- **Tests**: JUnit 5 (backend), Playwright (frontend e2e)

## Development

```bash
./scripts/dev.sh              # Backend (port 8086) + Vite dev server (port 4173)
./scripts/dev-electron.sh     # Same, but inside Electron shell
```

## Build & Install Desktop App

The native binary embeds the frontend, so frontend changes require a full rebuild.

| What changed | Command |
|---|---|
| Anything in `frontend/` or `backend/` | `./scripts/build-mac-app.sh` |
| Only `desktop/src/main.js` | `cd desktop && npm run package` |
| Quick iteration on CSS/shell | Use `./scripts/dev-electron.sh` instead |

Install after build:
```bash
osascript -e 'tell application "Code Flux" to quit' 2>/dev/null; sleep 1
rm -rf "/Applications/Code Flux.app"
cp -R desktop/dist/mac-arm64/Code\ Flux.app /Applications/
open "/Applications/Code Flux.app"
```

## Testing

```bash
cd backend && ./gradlew test           # Backend unit/integration tests
cd frontend && npm run test:e2e        # Playwright e2e (kills port 8085 first)
```

## Project Layout

```
backend/    Kotlin/Spring Boot API + sync engine + SQLite persistence
frontend/   Vue 3 SPA (dashboard views, charts, config editor)
desktop/    Electron wrapper (launches backend binary, loads frontend)
scripts/    Build, dev, and packaging scripts
config/     config.example.yaml (template — config.yaml is gitignored)
```

## Conventions

- Config: `config/config.yaml` is local/private, never committed. Use `config/config.example.yaml` as the public template.
- Default backend port: `8086` (override with `SERVER_PORT` env var)
- Frontend proxies API calls to backend via Vite config in dev mode
- After major completions or verification stages, send a short Telegram update to the `Code Flux` Telegram group with the configured `telegram` CLI.
- Telegram updates should include milestone status, verification outcome, and blockers if any.
- When visual changes are reviewed with screenshots, send those screenshots or their paths through Telegram as well so the user can provide visual feedback.
