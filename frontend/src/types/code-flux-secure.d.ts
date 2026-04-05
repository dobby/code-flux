import type { EditorLaunchRequest, EditorLaunchResponse } from '../types/workspace'

declare global {
  interface Window {
    codeFluxSecure?: {
      saveJiraToken?: (token: string) => Promise<void>
      openCommitInEditor?: (request: EditorLaunchRequest) => Promise<EditorLaunchResponse>
    }
  }
}

export {}
