<template>
  <a-tooltip :title="chatPermissionTip || undefined">
    <article class="app-card" :class="{ forbidden: !canOpenChat }" @click="openApp">
      <div class="cover-wrap">
        <img v-if="app.cover" :src="app.cover" :alt="app.appName" class="cover" />
        <div v-else class="cover-placeholder">
          <img src="@/assets/logo.svg" alt="" />
          <span>等待你的下一句灵感</span>
        </div>
        <div v-if="app.deployKey" class="cover-actions">
          <a-button type="primary" @click.stop="openDeployedApp"><EyeOutlined /> 查看作品</a-button>
        </div>
      </div>
      <div class="card-body">
        <div class="app-meta">
          <a-avatar :size="46" :src="app.user?.userAvatar" class="author-avatar">
            {{ authorInitial }}
          </a-avatar>
          <div class="app-info">
            <div class="title-row">
              <h3>{{ app.appName || '未命名应用' }}</h3>
              <a-tag v-if="featured" color="cyan">精选</a-tag>
            </div>
            <span class="author-name">{{ app.user?.userName || '我的应用' }}</span>
          </div>
        </div>
        <p>{{ app.initPrompt || '还没有应用描述' }}</p>
        <div class="card-footer">
          <span>{{ formatDate(app.createTime) }}</span>
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
  </a-tooltip>
</template>

<script setup lang="ts">
import { EyeOutlined, MoreOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser'

const props = defineProps<{ app: API.AppVO; editable?: boolean; featured?: boolean; ownerOnly?: boolean }>()
defineEmits<{ edit: [app: API.AppVO]; delete: [app: API.AppVO] }>()

const router = useRouter()
const loginUserStore = useLoginUserStore()
const isOwner = computed(() => Boolean(props.app.userId && loginUserStore.loginUser.id && String(props.app.userId) === String(loginUserStore.loginUser.id)))
const canOpenChat = computed(() => !props.ownerOnly || isOwner.value)
const chatPermissionTip = computed(() => props.ownerOnly && !isOwner.value ? '无法在别人的作品下对话哦~' : '')
const authorInitial = computed(() => (props.app.user?.userName || props.app.user?.userAccount || '我').slice(0, 1))
const openApp = () => canOpenChat.value && props.app.id && router.push(`/app/chat/${props.app.id}`)
const openDeployedApp = () => props.app.deployKey && window.open(`http://localhost:8080/${encodeURIComponent(props.app.deployKey)}/`, '_blank', 'noopener,noreferrer')
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
.app-card.forbidden { cursor: not-allowed; }
.app-card:hover { transform: translateY(-5px); box-shadow: 0 18px 42px rgba(26, 113, 119, .13); }
.cover-wrap { position: relative; aspect-ratio: 16 / 9; overflow: hidden; background: #eff9f8; }
.cover { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { display: flex; height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: #86a4a3; background: linear-gradient(135deg, #f3fbfa, #e5f5f6); }
.cover-placeholder img { width: 52px; height: 52px; border-radius: 14px; opacity: .75; }
.cover-actions { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(12, 37, 42, .24); opacity: 0; transition: opacity .2s ease; visibility: hidden; }
.app-card:hover .cover-actions, .app-card:focus-within .cover-actions { opacity: 1; visibility: visible; }
.card-body { padding: 15px 16px 13px; }
.app-meta { display: flex; min-width: 0; align-items: center; gap: 13px; }
.author-avatar { flex: 0 0 auto; color: #fff; background: #1d9bf0; font-weight: 700; }
.app-info { min-width: 0; flex: 1; }
.title-row, .card-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
h3 { overflow: hidden; margin: 0; color: #142226; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.author-name { display: block; overflow: hidden; margin-top: 4px; color: #6f7f82; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
p { overflow: hidden; height: 42px; margin: 8px 0 12px; color: #718083; font-size: 13px; line-height: 21px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.card-footer { color: #99a6a8; font-size: 12px; }
</style>
