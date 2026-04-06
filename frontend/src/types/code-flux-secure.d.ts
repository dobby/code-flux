import type {
  EditorLaunchRequest,
  EditorLaunchResponse,
  FileOpenRequest,
  FileOpenResponse,
} from '../types/workspace'

declare global {
  interface Window {
    codeFluxSecure?: {
      saveJiraToken?: (token: string) => Promise<void>
      openCommitInEditor?: (request: EditorLaunchRequest) => Promise<EditorLaunchResponse>
      openCommitFile?: (request: FileOpenRequest) => Promise<FileOpenResponse>
    }
  }
}

export {}
