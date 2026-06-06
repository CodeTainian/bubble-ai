<template>
  <a-tooltip :title="chatPermissionTip || undefined">
    <article class="app-card" :class="{ forbidden: !canOpenChat }" @click="openApp">
      <AppCover :cover="app.cover" :alt="app.appName" placeholder="等待你的下一句灵感">
        <template v-if="app.deployKey" #overlay>
          <a-button type="primary" @click.stop="openDeployedApp"><EyeOutlined /> 查看作品</a-button>
        </template>
      </AppCover>
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
            <span class="author-name">
              {{ app.user?.userName || '我的应用' }}
              <span>{{ formatDate(app.createTime, 'YYYY-MM-DD', '刚刚创建') }}</span>
            </span>
          </div>
        </div>
        <p>{{ app.initPrompt || '还没有应用描述' }}</p>
        <div v-if="editable" class="card-footer">
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppCover from '@/components/AppCover.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { APP_DEPLOY_BASE_URL } from '@/config/env'
import { formatDate } from '@/utils/date'

const props = defineProps<{ app: API.AppVO; editable?: boolean; featured?: boolean; ownerOnly?: boolean }>()
defineEmits<{ edit: [app: API.AppVO]; delete: [app: API.AppVO] }>()

const router = useRouter()
const loginUserStore = useLoginUserStore()
const isOwner = computed(() => Boolean(props.app.userId && loginUserStore.loginUser.id && String(props.app.userId) === String(loginUserStore.loginUser.id)))
const canOpenChat = computed(() => !props.ownerOnly || isOwner.value)
const chatPermissionTip = computed(() => props.ownerOnly && !isOwner.value ? '无法在别人的作品下对话哦~' : '')
const authorInitial = computed(() => (props.app.user?.userName || props.app.user?.userAccount || '我').slice(0, 1))
const openApp = () => canOpenChat.value && props.app.id && router.push(`/app/chat/${props.app.id}`)
const openDeployedApp = () => props.app.deployKey && window.open(`${APP_DEPLOY_BASE_URL}/${encodeURIComponent(props.app.deployKey)}/`, '_blank', 'noopener,noreferrer')
</script>

<style scoped>
.app-card {
  cursor: pointer;
  display: block;
  min-width: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  transition: transform .25s ease, box-shadow .25s ease;
}
.app-card.forbidden { cursor: not-allowed; }
.app-card:hover { transform: translateY(-4px); }
.card-body { padding: 16px 2px 0; }
.app-meta { display: flex; min-width: 0; align-items: center; gap: 12px; }
.author-avatar { flex: 0 0 auto; color: #fff; background: #1d9bf0; font-weight: 700; box-shadow: 0 8px 18px rgba(15, 23, 42, .08); }
.app-info { min-width: 0; flex: 1; }
.title-row, .card-footer { display: flex; align-items: center; justify-content: flex-start; gap: 10px; }
.title-row :deep(.ant-tag) { flex: 0 0 auto; margin-inline-end: 0; }
h3 { overflow: hidden; margin: 0; color: #142226; font-size: 17px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.author-name { display: flex; overflow: hidden; margin-top: 3px; color: #6f7f82; font-size: 13px; gap: 8px; text-overflow: ellipsis; white-space: nowrap; }
.author-name span { color: #8a949c; }
p { overflow: hidden; height: 22px; margin: 10px 0 0 54px; color: #718083; font-size: 13px; line-height: 22px; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; }
.card-footer { display: flex; justify-content: flex-end; margin-top: 4px; color: #99a6a8; font-size: 12px; }
</style>
