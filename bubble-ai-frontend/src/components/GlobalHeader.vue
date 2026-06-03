<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.svg" alt="Logo" />
            <h1 class="site-title">Bubble IA</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { logout } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const menuItems = computed<MenuProps['items']>(() => filterMenus(originalItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const doLogout = async () => {
  const res = await logout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败' + res.data.message)
  }
}

//过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

//菜单配置
const originalItems = [
  {
    key: '/',
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
  {
    key: '/others',
    label: h('a', { href: 'https://github.com/CodeTainian/bubble-ai', target: '_blank' }, '项目源码'),
    title: '项目源码',
  },
]
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 56px;
  border-bottom: 1px solid rgba(229, 233, 240, .86);
  background: rgba(255, 255, 255, .86);
  padding: 0 22px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, .04);
  backdrop-filter: blur(16px);
}

.header :deep(.ant-row) {
  height: 100%;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 40px;
  width: 40px;
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(20, 85, 135, .14);
}

.site-title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #1890ff;
}

.header :deep(.ant-menu) {
  background: transparent;
}

.header :deep(.ant-menu-horizontal) {
  border-bottom: none !important;
}

.header :deep(.ant-menu-horizontal > .ant-menu-item) {
  color: #1f333a;
  line-height: 56px;
  font-weight: 600;
}

.header :deep(.ant-menu-horizontal > .ant-menu-item-selected) {
  color: #1677ff;
}

.header :deep(.ant-menu-horizontal > .ant-menu-item::after) {
  border-bottom-width: 3px;
  border-radius: 999px;
  inset-inline: 18px;
}

.user-login-status {
  display: flex;
  align-items: center;
}

.user-login-status :deep(.ant-space) {
  color: #1f333a;
  font-weight: 600;
}

.user-login-status :deep(.ant-btn-primary) {
  border-radius: 9px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(24, 144, 255, .2);
}
</style>
