<template>
  <div class="edit-page">
    <div class="page-title"><span>APP SETTINGS</span><h2>应用信息修改</h2><p>{{ isAdmin ? '管理员可以维护应用名称、封面和优先级。' : '在这里为你的应用换一个更合适的名字。' }}</p></div>
    <a-spin :spinning="loading">
      <a-form :model="form" layout="vertical" @finish="submit">
        <a-form-item label="应用名称" name="appName" :rules="[{ required: true, message: '请输入应用名称' }]"><a-input v-model:value="form.appName" placeholder="输入应用名称" /></a-form-item>
        <template v-if="isAdmin">
          <a-form-item label="应用封面"><a-input v-model:value="form.cover" placeholder="输入封面图片 URL" /></a-form-item>
          <a-form-item label="优先级"><a-input-number v-model:value="form.priority" :min="0" style="width: 100%" /><div class="hint">填写 99 可将应用设置为精选。</div></a-form-item>
        </template>
        <a-space><a-button type="primary" html-type="submit" :loading="saving">保存修改</a-button><a-button @click="router.back()">返回</a-button></a-space>
      </a-form>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getAppVoById, getAppVoByIdByAdmin, updateApp, updateAppByAdmin } from '@/api/appController'

const route = useRoute(), router = useRouter()
const isAdmin = computed(() => route.path.startsWith('/admin'))
const id = String(route.params.id)
const loading = ref(true), saving = ref(false)
const form = reactive<API.AppAdminUpdateRequest>({ id, appName: '', cover: '', priority: 0 })
const load = async () => {
  loading.value = true
  try {
    const res = isAdmin.value ? await getAppVoByIdByAdmin({ id }) : await getAppVoById({ id })
    if (!res.data.data) return message.error('获取应用信息失败：' + res.data.message)
    Object.assign(form, res.data.data)
  } finally { loading.value = false }
}
const submit = async () => {
  saving.value = true
  try {
    const res = isAdmin.value ? await updateAppByAdmin(form) : await updateApp({ id, appName: form.appName })
    if (res.data.code === 0) { message.success('保存成功'); router.back() } else message.error('保存失败：' + res.data.message)
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.edit-page { max-width: 620px; margin: 20px auto; padding: 28px; border: 1px solid #edf1f1; border-radius: 18px; box-shadow: 0 14px 35px rgba(29,99,103,.08); }.page-title span { color: #16aaa1; font-size: 11px; font-weight: 700; letter-spacing: 2px; }.page-title h2 { margin: 8px 0; font-size: 27px; }.page-title p, .hint { color: #90a0a2; }.page-title p { margin-bottom: 25px; }.hint { margin-top: 7px; font-size: 12px; }
</style>
