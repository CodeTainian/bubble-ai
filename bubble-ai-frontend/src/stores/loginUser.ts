import { defineStore } from 'pinia'
import { ref } from 'vue'
import {getLoginUser} from '@/api/userController.ts'

//  `defineStore()` 的返回值的命名是自由的
// 但最好含有 store 的名字，且以 `use` 开头，以 `Store` 结尾。
// (比如 `useUserStore`，`useCartStore`，`useProductStore`)
// 第一个参数是你的应用中 Store 的唯一 ID。
export const useLoginUserStore = defineStore('loginUser', ()=>{
  // 默认值...
  const loginUser = ref<API.LoginUserVO>({
    userName: "未登录",
  })
  const initialized = ref(false)

  //获取登录用户信息
  async function fetchLoginUser() {
    try {
      const res = await getLoginUser();
      if(res.data.code===0&&res.data.data){
        loginUser.value = res.data.data
      } else {
        loginUser.value = { userName: '未登录' }
      }
    } finally {
      initialized.value = true
    }
  }

  //更新用户登录信息
  function setLoginUser(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
    initialized.value = true
  }

  return {loginUser, initialized, fetchLoginUser, setLoginUser}
})
