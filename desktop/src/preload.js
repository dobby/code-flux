const { contextBridge } = require('electron')

const CHROME_HEIGHT = 52
const MAC_TRAFFIC_LIGHTS = { x: 16, y: 14, width: 72 }

contextBridge.exposeInMainWorld('shellChrome', {
  platform: process.platform,
  chromeHeight: CHROME_HEIGHT,
  macTrafficLights: process.platform === 'darwin' ? MAC_TRAFFIC_LIGHTS : null,
})
