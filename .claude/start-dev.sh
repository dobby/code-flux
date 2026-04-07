#!/bin/bash
cd "$(dirname "$0")/../frontend"
exec /Users/eli/.nvm/versions/node/v22.18.0/bin/node node_modules/.bin/vite --host 127.0.0.1 --port 4173 --strictPort
