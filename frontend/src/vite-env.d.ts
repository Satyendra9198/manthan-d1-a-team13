/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL, e.g. http://localhost:8080/api. Defaults to "/api". */
  readonly VITE_API_BASE_URL?: string
  /** "true" to serve mock data instead of calling the backend. */
  readonly VITE_USE_MOCKS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
