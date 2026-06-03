<script setup lang="ts">
import { reactive } from 'vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { userRegister } from '@/api/userController.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import '@/assets/auth.css'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const router = useRouter()

const validatePassword = (_rule: unknown, value: string) => {
  if (value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致!')
  }
  return Promise.resolve()
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功，请登录')
    router.push({ path: '/user/login', replace: true })
  } else {
    message.error('注册失败：' + (res.data.message || '未知错误'))
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-bubbles">
      <span class="auth-bubble auth-bubble-a"></span>
      <span class="auth-bubble auth-bubble-b"></span>
      <span class="auth-bubble auth-bubble-c"></span>
      <span class="auth-bubble auth-bubble-d"></span>
      <span class="auth-bubble auth-bubble-e"></span>
      <span class="auth-bubble auth-bubble-f"></span>
    </div>
    <section class="auth-card">
      <div class="auth-brand-mark">
        <img src="@/assets/logo.svg" alt="Bubble AI" />
      </div>
      <h2 class="auth-title">Bubble AI 应用生成</h2>
      <div class="auth-desc">注册账号，开始创建你的第一个应用</div>
      <a-form :model="formState" name="register" autocomplete="off" @finish="handleSubmit">
      <a-form-item
        label=""
        name="userAccount"
        :rules="[{ required: true, message: '请输入账号!' }]"
      >
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large">
          <template #prefix>
            <UserOutlined class="auth-icon" />
          </template>
        </a-input>
      </a-form-item>

      <a-form-item
        label=""
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码!' },
          { min: 8, message: '密码长度不能小于8位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large">
          <template #prefix>
            <LockOutlined class="auth-icon" />
          </template>
        </a-input-password>
      </a-form-item>

      <a-form-item
        label=""
        name="checkPassword"
        :rules="[{ required: true, message: '请再次输入密码!' }, { validator: validatePassword }]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请再次输入密码" size="large">
          <template #prefix>
            <LockOutlined class="auth-icon" />
          </template>
        </a-input-password>
      </a-form-item>

      <div class="auth-tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>

      <a-form-item>
        <a-button type="primary" html-type="submit" size="large" class="auth-submit">注册</a-button>
      </a-form-item>
      </a-form>
    </section>
  </div>
</template>
