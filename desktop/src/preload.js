const { contextBridge, ipcRenderer } = require('electron')

const CHROME_HEIGHT = 0
const MAC_TRAFFIC_LIGHTS = { x: 16, y: 14, width: 72 }

contextBridge.exposeInMainWorld('shellChrome', {
  platform: process.platform,
  chromeHeight: CHROME_HEIGHT,
  macTrafficLights: process.platform === 'darwin' ? MAC_TRAFFIC_LIGHTS : null,
})

contextBridge.exposeInMainWorld('codeFluxSecure', {
  saveJiraToken: async (token) => {
    await ipcRenderer.invoke('jira-secret-save', token)
  },
})
