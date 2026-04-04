# Agent Notes

## Startup Scripts

- Development entrypoint: `./scripts/dev.sh`
- Release build: `./scripts/build-release.sh`
- Packaged app runner: `APP_CONFIG_FILE="$PWD/config/config.yaml" ./scripts/run-dashboard.sh`

## Ports

- The project defaults to backend port `8086`.
- If a command needs an explicit port, prefer `SERVER_PORT=8086`.

## Config

- Treat `config/config.yaml` as private/local and do not commit it.
- Use `config/config.example.yaml` as the public template.

## Desktop App (Electron)

### Build Paths

The full mac app build (`scripts/build-mac-app.sh`) runs: generate icons, build native backend (GraalVM, ~3 min), and package Electron. The native binary embeds the frontend static assets, so a backend rebuild is needed when frontend code changes for a production build.

**Choose the right build path based on what changed:**

| What changed | Build command | Notes |
|---|---|---|
| Frontend CSS/JS/Vue only | `scripts/build-mac-app.sh` | Must rebuild native binary because frontend is embedded in it |
| Electron shell only (`desktop/src/main.js`) | `cd desktop && npm run package` | Repackages Electron without touching the backend; reuses existing `desktop/resources/` |
| Backend Kotlin code | `scripts/build-mac-app.sh` | Full rebuild required |
| Frontend + Electron (no backend) | `scripts/build-mac-app.sh` | Full rebuild required due to embedded assets |

**For quick iteration on shell/CSS changes**, use `scripts/dev-electron.sh` which points Electron at a live dev server — no backend or native build needed.

### Install After Build

```bash
osascript -e 'tell application "Code Flux" to quit' 2>/dev/null
sleep 1
rm -rf "/Applications/Code Flux.app"
cp -R desktop/dist/mac-arm64/Code\ Flux.app /Applications/
open "/Applications/Code Flux.app"
```
