/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_API_BASE_URL?: string
  readonly VITE_APP_DEPLOY_BASE_URL?: string
  readonly VITE_APP_PREVIEW_BASE_URL?: string
  readonly VITE_APP_MONITOR_GRAFANA_URL?: string
  readonly VITE_APP_MONITOR_PROMETHEUS_URL?: string
  readonly VITE_APP_MONITOR_ARMS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
