import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatHistoryManagePage from '@/pages/admin/ChatHistoryManagePage.vue'
import ProjectMonitorPage from '@/pages/admin/ProjectMonitorPage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
      meta: { fullBleed: true },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
      meta: { fullBleed: true },
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
      meta: { fullBleed: true },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
    },
    {
      path: '/admin/chatHistoryManage',
      name: '对话管理',
      component: ChatHistoryManagePage,
    },
    {
      path: '/admin/projectMonitor',
      name: '项目监控',
      component: ProjectMonitorPage,
    },
    {
      path: '/app/chat/:id',
      name: '应用生成',
      component: AppChatPage,
      meta: { fullscreen: true, keepAlive: true },
    },
    {
      path: '/app/edit/:id',
      name: '编辑应用',
      component: AppEditPage,
    },
    {
      path: '/admin/app/edit/:id',
      name: '管理员编辑应用',
      component: AppEditPage,
    },
  ],
})

export default router
