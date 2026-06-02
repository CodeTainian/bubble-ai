<template>
  <article class="app-card" @click="openApp">
    <div class="cover-wrap">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" class="cover" />
      <div v-else class="cover-placeholder">
        <img src="@/assets/logo.svg" alt="" />
        <span>等待你的下一句灵感</span>
      </div>
    </div>
    <div class="card-body">
      <div class="title-row">
        <h3>{{ app.appName || '未命名应用' }}</h3>
        <a-tag v-if="featured" color="cyan">精选</a-tag>
      </div>
      <p>{{ app.initPrompt || '还没有应用描述' }}</p>
      <div class="card-footer">
        <span>{{ app.user?.userName || '我的应用' }} · {{ formatDate(app.createTime) }}</span>
        <a-dropdown v-if="editable" :trigger="['click']">
          <a-button type="text" size="small" @click.stop><MoreOutlined /></a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="$emit('edit', app)">编辑信息</a-menu-item>
              <a-menu-item danger @click="$emit('delete', app)">删除应用</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { MoreOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'

const props = defineProps<{ app: API.AppVO; editable?: boolean; featured?: boolean }>()
defineEmits<{ edit: [app: API.AppVO]; delete: [app: API.AppVO] }>()

const router = useRouter()
const openApp = () => props.app.id && router.push(`/app/chat/${props.app.id}`)
const formatDate = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD') : '刚刚创建'
</script>

<style scoped>
.app-card {
  cursor: pointer;
  overflow: hidden;
  border: 1px solid #edf1f4;
  border-radius: 18px;
  background: #fff;
  transition: transform .25s ease, box-shadow .25s ease;
}
.app-card:hover { transform: translateY(-5px); box-shadow: 0 18px 42px rgba(26, 113, 119, .13); }
.cover-wrap { aspect-ratio: 16 / 9; overflow: hidden; background: #eff9f8; }
.cover { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { display: flex; height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: #86a4a3; background: linear-gradient(135deg, #f3fbfa, #e5f5f6); }
.cover-placeholder img { width: 52px; height: 52px; border-radius: 14px; opacity: .75; }
.card-body { padding: 15px 16px 13px; }
.title-row, .card-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
h3 { overflow: hidden; margin: 0; color: #142226; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
p { overflow: hidden; height: 42px; margin: 8px 0 12px; color: #718083; font-size: 13px; line-height: 21px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.card-footer { color: #99a6a8; font-size: 12px; }
</style>
