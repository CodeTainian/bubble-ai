import axios from 'axios'
import { message } from 'ant-design-vue'
import { APP_API_BASE_URL } from '@/config/env'

const myAxios = axios.create({
  baseURL: APP_API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

const redirectToLogin = (responseUrl = '') => {
  if (responseUrl.includes('user/get/login') || window.location.pathname.includes('/user/login')) return
  message.warning('请先登录')
  const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}${window.location.hash}`)
  window.location.href = `/user/login?redirect=${redirect}`
}

myAxios.interceptors.request.use(
  function (config) {
    return config
  },
  function (error) {
    return Promise.reject(error)
  },
)

myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    if (data.code === 40100) {
      redirectToLogin(response.request.responseURL)
    }
    return response
  },
  function (error) {
    if (error?.response?.status === 401) {
      redirectToLogin(error?.response?.request?.responseURL || error?.config?.url || '')
    }
    return Promise.reject(error)
  },
)

export default myAxios
