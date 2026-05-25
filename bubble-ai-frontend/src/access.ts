import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { message } from 'ant-design-vue'

let firstFetchLoginUser = true


/**
 * 为了确‌保页面刷新时，从后端拿到用户信息后再进行权限校验，
 * 使用‌ await 等待后端接口返回，并重新赋值给 loginUser。
 * 同时，为了防止每次切换路由都从远程获取用户‌信息，
 * 定义了 firstFetchLoginUser ‌变量，用于控制在刷新页面后只会请求后端一次
 */

/**
 * 利用 Vue Router 的路由守卫实现，
 * 每次切换并进入页面前，都会检查一下当前用户是否具有特定页面的权限。
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()

  let loginUser = loginUserStore.loginUser

  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser();
    loginUser = loginUserStore.loginUser;
    firstFetchLoginUser = false
  }

  const toUrl = to.fullPath
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  next()
})
