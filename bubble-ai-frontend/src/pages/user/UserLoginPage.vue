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
      <div class="auth-desc">登录后继续把想法变成完整应用</div>
      <a-form
        :model="formState"
        name="basic"
        autocomplete="off"
        @finish="handleSubmit"
      >
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
          :rules="[{ required: true, message: '请输入密码!' },{
            min:8,message: '密码长度不能小于8位'
          }]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large">
            <template #prefix>
              <LockOutlined class="auth-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="auth-tips">
          没有账号？
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" class="auth-submit">登录</a-button>
        </a-form-item>
      </a-form>
    </section>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import '@/assets/auth.css'


const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
});

const router = useRouter();
const loginUserStore = useLoginUserStore();

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserLoginRequest) => {
  const res = await userLogin(values);
  if (res.data.code===0&&res.data.data
  ) {
await loginUserStore.fetchLoginUser();
message.success("登录成功")
    router.push({
      path: '/',
      replace: true,
    })

  }else {
    message.error("登录失败"+res.data.message)
  }
};

</script>
