const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

const isLoopbackHost = (host: string) => ['localhost', '127.0.0.1', '0.0.0.0', '[::1]', '::1'].includes(host)

const toSameOriginPreviewBaseUrl = (value: string) => {
  const normalizedValue = trimTrailingSlash(value)
  try {
    const previewUrl = new URL(normalizedValue, window.location.origin)
    const isAbsoluteUrl = /^[a-z][a-z\d+\-.]*:\/\//i.test(normalizedValue)
    const canUseDevProxy =
      isAbsoluteUrl &&
      previewUrl.origin !== window.location.origin &&
      previewUrl.pathname.startsWith('/api/static') &&
      (previewUrl.hostname === window.location.hostname ||
        (isLoopbackHost(previewUrl.hostname) && isLoopbackHost(window.location.hostname)))

    return canUseDevProxy ? trimTrailingSlash(`${previewUrl.pathname}${previewUrl.search}${previewUrl.hash}`) : normalizedValue
  } catch {
    return normalizedValue
  }
}

export const APP_API_BASE_URL = trimTrailingSlash(import.meta.env.VITE_APP_API_BASE_URL || '/api')

export const APP_DEPLOY_BASE_URL = trimTrailingSlash(import.meta.env.VITE_APP_DEPLOY_BASE_URL || 'http://localhost:8080/api/static')

export const APP_PREVIEW_BASE_URL = toSameOriginPreviewBaseUrl(import.meta.env.VITE_APP_PREVIEW_BASE_URL || '/api/static')

export const APP_MONITOR_GRAFANA_URL = trimTrailingSlash(import.meta.env.VITE_APP_MONITOR_GRAFANA_URL || 'http://49.235.165.209:3000')

export const APP_MONITOR_PROMETHEUS_URL = trimTrailingSlash(import.meta.env.VITE_APP_MONITOR_PROMETHEUS_URL || 'http://49.235.165.209:9090')

export const APP_MONITOR_ARMS_URL = trimTrailingSlash(import.meta.env.VITE_APP_MONITOR_ARMS_URL || 'https://arms.console.aliyun.com')
