<template>
  <div class="userLoginPage">
    <div class="bubble-field">
      <span class="bubble bubble-a"></span>
      <span class="bubble bubble-b"></span>
      <span class="bubble bubble-c"></span>
      <span class="bubble bubble-d"></span>
    </div>
    <section class="login-card">
      <div class="brand-mark">
        <img src="@/assets/logo.svg" alt="Bubble AI" />
      </div>
      <h2 class="title">Bubble AI 应用生成</h2>
      <div class="desc">登录后继续把想法变成完整应用</div>
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
              <UserOutlined class="site-form-item-icon" />
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
              <LockOutlined class="site-form-item-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="tips">
          没有账号？
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" class="login-button">登录</a-button>
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

<style scoped>
.userLoginPage {
  position: relative;
  display: grid;
  min-height: calc(100vh - 104px);
  place-items: center;
  overflow: hidden;
  padding: 56px 24px 72px;
  background:
    radial-gradient(circle at 14% 26%, rgba(31, 132, 255, .2), transparent 24%),
    radial-gradient(circle at 80% 18%, rgba(96, 228, 210, .24), transparent 23%),
    radial-gradient(circle at 68% 82%, rgba(42, 120, 255, .12), transparent 25%),
    linear-gradient(135deg, #f7fbff 0%, #ffffff 42%, #edfafa 100%);
}
.bubble-field {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.bubble {
  position: absolute;
  border: 1px solid rgba(58, 149, 255, .22);
  border-radius: 50%;
  background: radial-gradient(circle at 30% 28%, rgba(255,255,255,.95), rgba(71, 181, 255, .2) 42%, rgba(62, 224, 207, .08));
  box-shadow: inset 0 0 22px rgba(255,255,255,.72), 0 20px 60px rgba(47, 143, 190, .12);
}
.bubble-a { top: 10%; left: 8%; width: 118px; height: 118px; }
.bubble-b { right: 10%; top: 16%; width: 172px; height: 172px; }
.bubble-c { bottom: 10%; left: 17%; width: 154px; height: 154px; }
.bubble-d { right: 26%; bottom: 14%; width: 76px; height: 76px; }
.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 430px);
  padding: 34px 36px 30px;
  border: 1px solid rgba(207, 227, 235, .82);
  border-radius: 24px;
  background: rgba(255, 255, 255, .88);
  box-shadow: 0 24px 70px rgba(31, 111, 148, .14);
  backdrop-filter: blur(14px);
}
.brand-mark {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 0 auto 18px;
  place-items: center;
  border-radius: 18px;
  background: #111b32;
  box-shadow: 0 14px 28px rgba(20, 85, 135, .18);
}
.brand-mark img {
  width: 46px;
  height: 46px;
}
.title {
  margin: 0 0 8px;
  color: #172326;
  font-size: 24px;
  font-weight: 800;
  text-align: center;
}
.desc {
  margin-bottom: 28px;
  color: #7b8a8f;
  font-size: 14px;
  text-align: center;
}
.site-form-item-icon {
  color: #6c7f86;
}
.tips {
  margin: -2px 0 18px;
  color: #9aa7ab;
  font-size: 13px;
  text-align: right;
}
.login-button {
  width: 100%;
  border-radius: 10px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(24, 144, 255, .22);
}

@media (max-width: 640px) {
  .userLoginPage {
    min-height: calc(100vh - 104px);
    padding: 36px 18px 58px;
  }
  .login-card {
    padding: 28px 22px 24px;
  }
  .bubble-b {
    right: -36px;
  }
  .bubble-c {
    left: -24px;
  }
}
</style>
