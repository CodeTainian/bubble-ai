const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

export const APP_DEPLOY_BASE_URL = trimTrailingSlash(import.meta.env.VITE_APP_DEPLOY_BASE_URL || 'http://localhost:8080')

export const APP_PREVIEW_BASE_URL = trimTrailingSlash(import.meta.env.VITE_APP_PREVIEW_BASE_URL || 'http://localhost:8123/api/static')

