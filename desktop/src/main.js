const { app, BrowserWindow, dialog, ipcMain, safeStorage, shell } = require('electron')
const { spawn, spawnSync } = require('node:child_process')
const fs = require('node:fs')
const http = require('node:http')
const net = require('node:net')
const path = require('node:path')

const PRODUCT_NAME = 'Code Flux'
const HEALTH_TIMEOUT_MS = 30_000
const HEALTH_POLL_INTERVAL_MS = 500
const BACKEND_LOG_NAME = 'backend.log'
const WINDOW_WIDTH = parseIntegerEnv('CODE_FLUX_WINDOW_WIDTH', 1440)
const WINDOW_HEIGHT = parseIntegerEnv('CODE_FLUX_WINDOW_HEIGHT', 960)
const CAPTURE_DELAY_MS = parseIntegerEnv('CODE_FLUX_CAPTURE_DELAY_MS', 1500)
const CAPTURE_PATH = process.env.CODE_FLUX_CAPTURE_PATH || null
const CAPTURE_AND_EXIT = process.env.CODE_FLUX_CAPTURE_AND_EXIT === '1'

app.setName(PRODUCT_NAME)
app.setPath('userData', path.join(app.getPath('appData'), PRODUCT_NAME))

let mainWindow = null
let backendProcess = null
let backendLogStream = null
let quittingForShutdown = false
let startupLogPath = null
let currentTargetUrl = null
let backendReady = false
const JIRA_SECRET_NAME = 'jira-token.bin'

if (!app.requestSingleInstanceLock()) {
  app.quit()
  process.exit(0)
}

app.on('second-instance', () => {
  if (!mainWindow) {
    return
  }

  if (mainWindow.isMinimized()) {
    mainWindow.restore()
  }

  mainWindow.focus()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('activate', () => {
  if (!mainWindow) {
    void bootstrapApplication()
  }
})

app.on('before-quit', (event) => {
  if (quittingForShutdown || !backendProcess) {
    return
  }

  event.preventDefault()
  quittingForShutdown = true
  void stopBackend().finally(() => {
    app.quit()
  })
})

app.whenReady().then(async () => {
  installSecureIpc()
  await bootstrapApplication()
})

async function bootstrapApplication() {
  try {
    const launchTarget = currentTargetUrl ?? (await resolveLaunchTarget())
    currentTargetUrl = launchTarget
    await hydrateStoredSecrets()
    createWindow(launchTarget)
  } catch (error) {
    await stopBackend()
    const detail = error instanceof Error ? error.message : 'Unknown startup failure'
    const message = startupLogPath
      ? `${detail}\n\nLogs: ${startupLogPath}`
      : detail
    dialog.showErrorBox(PRODUCT_NAME, message)
    app.exit(1)
  }
}

function installSecureIpc() {
  ipcMain.handle('jira-secret-save', async (_event, token) => {
    if (typeof token !== 'string' || token.trim().length === 0) {
      throw new Error('Token is required')
    }
    storeSecret(JIRA_SECRET_NAME, token.trim())
    await postToLocalApi('/api/v2/settings/jira/secret', { token: token.trim() })
  })

  ipcMain.handle('editor-launch-open', async (_event, request) => {
    if (!currentTargetUrl) {
      throw new Error('Local API is not available yet')
    }

    const response = await requestLocalApi('/api/v2/explorer/open-in-editor', request)
    if (!response.available) {
      return response
    }

    const child = spawn(response.editorCommand, response.filePaths ?? [], {
      detached: true,
      stdio: 'ignore',
      shell: false,
      env: process.env,
    })
    child.unref()
    return response
  })

  ipcMain.handle('shell-open-commit-file', async (_event, request) => {
    if (!currentTargetUrl) {
      throw new Error('Local API is not available yet')
    }

    const response = await requestLocalApi('/api/v2/explorer/open-file', request)
    if (!response.available || !response.resolvedPath) {
      return response
    }

    const errorMessage = await shell.openPath(response.resolvedPath)
    if (errorMessage) {
      return {
        ...response,
        available: false,
        opened: false,
        reason: errorMessage,
      }
    }

    return {
      ...response,
      opened: true,
    }
  })
}

function createWindow(targetUrl) {
  if (mainWindow) {
    mainWindow.focus()
    return
  }

  mainWindow = new BrowserWindow({
    title: PRODUCT_NAME,
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    minWidth: 1100,
    minHeight: 720,
    show: false,
    titleBarStyle: 'hidden',
    frame: process.platform !== 'linux' ? undefined : false,
    titleBarOverlay:
      process.platform === 'win32' || process.platform === 'linux'
        ? {
            color: '#00000000',
            symbolColor: '#e9eefc',
            height: 52,
          }
        : undefined,
    trafficLightPosition: process.platform === 'darwin' ? { x: 16, y: 14 } : undefined,
    autoHideMenuBar: true,
    vibrancy: process.platform === 'darwin' ? 'under-window' : undefined,
    acceptFirstMouse: process.platform === 'darwin' ? true : undefined,
    visualEffectState: process.platform === 'darwin' ? 'active' : undefined,
    backgroundMaterial: process.platform === 'win32' ? 'mica' : undefined,
    backgroundColor:
      process.platform === 'darwin'
        ? '#00000000'
        : process.platform === 'win32'
          ? '#0f1115'
          : '#eef3fb',
    roundedCorners: true,
    icon: resolveWindowIconPath(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith(targetUrl)) {
      return
    }

    event.preventDefault()
    void shell.openExternal(url)
  })

  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (!isReloadShortcut(input)) {
      return
    }

    event.preventDefault()
    void reloadWindowContents()
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
    if (CAPTURE_PATH) {
      void captureWindow(mainWindow)
    }
  })

  if (process.platform === 'win32' && typeof mainWindow.setBackgroundMaterial === 'function') {
    mainWindow.setBackgroundMaterial('mica')
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  void mainWindow.loadURL(targetUrl)
}

function isReloadShortcut(input) {
  const key = typeof input.key === 'string' ? input.key.toLowerCase() : ''
  const usesPrimaryModifier = process.platform === 'darwin' ? input.meta : input.control
  return (
    (usesPrimaryModifier && key === 'r' && !input.shift && !input.alt) ||
    key === 'f5'
  )
}

async function reloadWindowContents() {
  if (!mainWindow) {
    return
  }

  const nextUrl = resolveReloadUrl(mainWindow.webContents.getURL())
  await mainWindow.loadURL(nextUrl)
}

function resolveReloadUrl(currentUrl) {
  if (!currentTargetUrl) {
    return currentUrl
  }

  if (!currentUrl) {
    return currentTargetUrl
  }

  try {
    const current = new URL(currentUrl)
    const target = new URL(currentTargetUrl)
    return current.origin === target.origin ? current.toString() : currentTargetUrl
  } catch {
    return currentTargetUrl
  }
}

async function captureWindow(windowRef) {
  await new Promise((resolve) => setTimeout(resolve, CAPTURE_DELAY_MS))
  const image = await windowRef.webContents.capturePage()
  fs.mkdirSync(path.dirname(CAPTURE_PATH), { recursive: true })
  fs.writeFileSync(CAPTURE_PATH, image.toPNG())
  if (CAPTURE_AND_EXIT) {
    setTimeout(() => app.quit(), 100)
  }
}

async function resolveLaunchTarget() {
  const targetUrl = process.env.CODE_FLUX_ELECTRON_TARGET_URL
  if (targetUrl) {
    return targetUrl
  }

  const userDataDir = app.getPath('userData')
  const logsDir = path.join(userDataDir, 'logs')
  fs.mkdirSync(logsDir, { recursive: true })
  startupLogPath = path.join(logsDir, BACKEND_LOG_NAME)
  backendLogStream = fs.createWriteStream(startupLogPath, { flags: 'a' })

  const resourcesDir = resolveResourcesDir()
  const configTemplatePath = path.join(resourcesDir, 'config', 'config.example.yaml')
  const configDir = path.join(userDataDir, 'config')
  const dataDir = path.join(userDataDir, 'data')
  const configPath = path.join(configDir, 'config.yaml')

  fs.mkdirSync(configDir, { recursive: true })
  fs.mkdirSync(dataDir, { recursive: true })

  if (!fs.existsSync(configPath)) {
    if (!fs.existsSync(configTemplatePath)) {
      throw new Error(`Config template not found at ${configTemplatePath}`)
    }
    fs.copyFileSync(configTemplatePath, configPath)
  }

  const port = await findOpenPort()
  const appBaseUrl = `http://127.0.0.1:${port}`
  await startBackend({
    resourcesDir,
    port,
    appBaseUrl,
    configPath,
    dataDir,
  })

  return appBaseUrl
}

async function hydrateStoredSecrets() {
  const token = readSecret(JIRA_SECRET_NAME)
  if (!token) {
    return
  }
  await postToLocalApi('/api/v2/settings/jira/secret', { token })
}

function resolveResourcesDir() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'resources')
  }

  return path.join(__dirname, '..', 'resources')
}

function resolveWindowIconPath() {
  const packagedIconPath = path.join(process.resourcesPath, 'app.asar.unpacked', 'assets', 'icon.png')
  if (app.isPackaged && fs.existsSync(packagedIconPath)) {
    return packagedIconPath
  }

  const devIconPath = path.join(__dirname, '..', 'assets', 'icon.png')
  return fs.existsSync(devIconPath) ? devIconPath : undefined
}

function parseIntegerEnv(name, fallback) {
  const raw = process.env[name]
  const parsed = Number.parseInt(raw || '', 10)
  return Number.isFinite(parsed) ? parsed : fallback
}

async function startBackend({ resourcesDir, port, appBaseUrl, configPath, dataDir }) {
  const backendMode = detectBackendMode(resourcesDir)
  const environment = {
    ...process.env,
    SERVER_PORT: String(port),
    APP_BASE_URL: appBaseUrl,
    APP_CONFIG_FILE: configPath,
    APP_DATA_DIR: dataDir,
    APP_OPEN_BROWSER_ON_START: 'false',
  }

  const childProcess =
    backendMode === 'native'
      ? spawnNativeBackend(resourcesDir, environment)
      : spawnJarBackend(resourcesDir, environment)

  backendProcess = childProcess
  backendReady = false
  childProcess.stdout?.pipe(backendLogStream, { end: false })
  childProcess.stderr?.pipe(backendLogStream, { end: false })

  childProcess.once('exit', (code, signal) => {
    if (!quittingForShutdown && backendReady) {
      const reason = signal ? `signal ${signal}` : `code ${code ?? 0}`
      dialog.showErrorBox(
        PRODUCT_NAME,
        `The backend process exited unexpectedly (${reason}).${startupLogPath ? `\n\nLogs: ${startupLogPath}` : ''}`,
      )
      app.quit()
    }
  })

  await waitForHealth(appBaseUrl)
  backendReady = true
}

function secretPath(name) {
  const secretsDir = path.join(app.getPath('userData'), 'secrets')
  fs.mkdirSync(secretsDir, { recursive: true })
  return path.join(secretsDir, name)
}

function storeSecret(name, value) {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('Desktop encryption is unavailable on this machine.')
  }
  const encrypted = safeStorage.encryptString(value)
  fs.writeFileSync(secretPath(name), encrypted)
}

function readSecret(name) {
  const filePath = secretPath(name)
  if (!fs.existsSync(filePath)) {
    return null
  }
  if (!safeStorage.isEncryptionAvailable()) {
    return null
  }
  const encrypted = fs.readFileSync(filePath)
  return safeStorage.decryptString(encrypted)
}

async function postToLocalApi(pathname, payload) {
  await requestLocalApi(pathname, payload)
}

async function requestLocalApi(pathname, payload) {
  if (!currentTargetUrl) {
    throw new Error('Local API is not available yet')
  }

  const target = new URL(pathname, currentTargetUrl).toString()
  const response = await fetch(target, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const detail = await response.text().catch(() => response.statusText)
    throw new Error(`Local API request failed: ${response.status} ${detail}`)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    return response.json()
  }

  return response.text()
}

function detectBackendMode(resourcesDir) {
  const requestedMode = process.env.CODE_FLUX_ELECTRON_BACKEND_MODE ?? 'auto'
  const nativeBinaryPath = path.join(resourcesDir, 'backend', 'native', 'code-flux-dashboard')
  const jarPath = path.join(resourcesDir, 'backend', 'jar', 'code-flux-dashboard.jar')
  const hasNative = fs.existsSync(nativeBinaryPath)
  const hasJar = fs.existsSync(jarPath)

  switch (requestedMode) {
    case 'native':
      if (!hasNative) {
        throw new Error(`Native backend not found at ${nativeBinaryPath}`)
      }
      return 'native'
    case 'jar':
      if (!hasJar) {
        throw new Error(`Jar backend not found at ${jarPath}`)
      }
      return 'jar'
    case 'auto':
      if (hasNative) {
        return 'native'
      }
      if (hasJar) {
        return 'jar'
      }
      throw new Error('No packaged backend artifact is available.')
    default:
      throw new Error(`Unsupported backend mode: ${requestedMode}`)
  }
}

function spawnNativeBackend(resourcesDir, environment) {
  const nativeBinaryPath = path.join(resourcesDir, 'backend', 'native', 'code-flux-dashboard')
  return spawn(nativeBinaryPath, [], {
    env: environment,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function spawnJarBackend(resourcesDir, environment) {
  const jarPath = path.join(resourcesDir, 'backend', 'jar', 'code-flux-dashboard.jar')
  const javaBinary = resolveJavaBinary()
  return spawn(javaBinary, ['-jar', jarPath], {
    env: environment,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function resolveJavaBinary() {
  const candidates = []
  if (process.env.JAVA_BIN) {
    candidates.push(process.env.JAVA_BIN)
  }

  const javaHome = spawnSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' })
  if (javaHome.status === 0) {
    candidates.push(path.join(javaHome.stdout.trim(), 'bin', 'java'))
  }

  const whichJava = spawnSync('which', ['java'], { encoding: 'utf8' })
  if (whichJava.status === 0) {
    candidates.push(whichJava.stdout.trim())
  }

  for (const candidate of candidates) {
    if (!candidate || !fs.existsSync(candidate)) {
      continue
    }

    const versionCheck = spawnSync(candidate, ['-version'], { encoding: 'utf8' })
    const output = `${versionCheck.stdout ?? ''}\n${versionCheck.stderr ?? ''}`
    const match = output.match(/version "(\d+)/)
    if (match && Number(match[1]) >= 21) {
      return candidate
    }
  }

  throw new Error('Jar backend fallback requires Java 21+, but no compatible java executable was found.')
}

async function findOpenPort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.unref()
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close(() => reject(new Error('Failed to allocate a localhost port.')))
        return
      }

      const { port } = address
      server.close((error) => {
        if (error) {
          reject(error)
          return
        }
        resolve(port)
      })
    })
  })
}

async function waitForHealth(appBaseUrl) {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  const healthUrl = new URL('/api/health', appBaseUrl)

  while (Date.now() < deadline) {
    if (backendProcess?.exitCode != null) {
      throw new Error(`Backend process exited before becoming healthy. Exit code ${backendProcess.exitCode}.`)
    }

    const healthy = await probeHealth(healthUrl)
    if (healthy) {
      return
    }

    await new Promise((resolve) => setTimeout(resolve, HEALTH_POLL_INTERVAL_MS))
  }

  throw new Error(`Timed out waiting for backend health at ${healthUrl.toString()}.`)
}

async function probeHealth(healthUrl) {
  return new Promise((resolve) => {
    const request = http.get(healthUrl, (response) => {
      response.resume()
      resolve(response.statusCode === 200)
    })

    request.on('error', () => resolve(false))
    request.setTimeout(2_000, () => {
      request.destroy()
      resolve(false)
    })
  })
}

async function stopBackend() {
  const processToStop = backendProcess
  backendProcess = null
  currentTargetUrl = null
  backendReady = false

  if (!processToStop) {
    backendLogStream?.end()
    backendLogStream = null
    return
  }

  await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      processToStop.kill('SIGKILL')
    }, 5_000)

    processToStop.once('exit', () => {
      clearTimeout(timeout)
      resolve()
    })

    processToStop.kill('SIGTERM')
  })

  backendLogStream?.end()
  backendLogStream = null
}
