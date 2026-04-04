const { app, BrowserWindow, dialog, shell } = require('electron')
const { spawn, spawnSync } = require('node:child_process')
const fs = require('node:fs')
const http = require('node:http')
const net = require('node:net')
const path = require('node:path')

const PRODUCT_NAME = 'Code Flux'
const HEALTH_TIMEOUT_MS = 30_000
const HEALTH_POLL_INTERVAL_MS = 500
const BACKEND_LOG_NAME = 'backend.log'

app.setName(PRODUCT_NAME)
app.setPath('userData', path.join(app.getPath('appData'), PRODUCT_NAME))

let mainWindow = null
let backendProcess = null
let backendLogStream = null
let quittingForShutdown = false
let startupLogPath = null
let currentTargetUrl = null
let backendReady = false

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
  await bootstrapApplication()
})

async function bootstrapApplication() {
  try {
    const launchTarget = currentTargetUrl ?? (await resolveLaunchTarget())
    currentTargetUrl = launchTarget
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

function createWindow(targetUrl) {
  if (mainWindow) {
    mainWindow.focus()
    return
  }

  mainWindow = new BrowserWindow({
    title: PRODUCT_NAME,
    width: 1440,
    height: 960,
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

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  if (process.platform === 'win32' && typeof mainWindow.setBackgroundMaterial === 'function') {
    mainWindow.setBackgroundMaterial('mica')
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  void mainWindow.loadURL(targetUrl)
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
