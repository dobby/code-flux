declare global {
  interface Window {
    codeFluxSecure?: {
      saveJiraToken?: (token: string) => Promise<void>
    }
  }
}

export {}
