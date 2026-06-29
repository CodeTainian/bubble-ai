<template>
  <div class="admin-page chat-history-manage-page">
    <div class="page-title">
      <div>
        <h2>对话管理</h2>
        <p>管理应用对话历史、消息类型与归属信息</p>
      </div>
    </div>
    <section class="filter-card">
      <div class="filter-head">
        <div class="filter-title"><FilterOutlined /> 筛选条件</div>
        <a-space>
          <a-button @click="resetSearch"><ReloadOutlined /> 重置</a-button>
          <a-button type="primary" @click="search"><SearchOutlined /> 搜索</a-button>
        </a-space>
      </div>
      <a-form class="filter-grid" :model="searchParams" @finish="search">
        <div class="filter-field">
          <label>对话 ID：</label>
          <a-input v-model:value="searchParams.id" allow-clear placeholder="输入 ID" size="large" />
        </div>
        <div class="filter-field">
          <label>应用 ID：</label>
          <a-input v-model:value="searchParams.appId" allow-clear placeholder="输入应用 ID" size="large" />
        </div>
        <div class="filter-field">
          <label>用户 ID：</label>
          <a-input v-model:value="searchParams.userId" allow-clear placeholder="输入用户 ID" size="large" />
        </div>
        <div class="filter-field">
          <label>消息类型：</label>
          <a-input v-model:value="searchParams.messageType" allow-clear placeholder="如 user / ai / error" size="large" />
        </div>
        <div class="filter-field">
          <label>消息内容：</label>
          <a-input v-model:value="searchParams.message" allow-clear placeholder="输入关键词" size="large" />
        </div>
      </a-form>
    </section>
    <section class="table-card">
      <a-table :columns="columns" :data-source="data" :pagination="pagination" row-key="id" @change="tableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'id'">
            <span class="muted mono">{{ record.id }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'appId'">
            <span class="muted mono">{{ record.appId || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userId'">
            <span class="muted mono">{{ record.userId || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :class="getMessageTypeClass(record.messageType)">{{ getMessageTypeText(record.messageType) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'message'">
            <a-typography-paragraph class="message-preview" :ellipsis="{ rows: 2, expandable: true, symbol: '展开' }">
              {{ record.message || '-' }}
            </a-typography-paragraph>
          </template>
          <template v-else-if="column.dataIndex === 'parentId'">
            <span class="muted mono">{{ record.parentId || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">{{ formatDate(record.createTime, 'YYYY-MM-DD HH:mm') }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space class="action-group">
              <a-button type="link" :disabled="!record.appId" @click="viewApp(record.appId)">查看应用</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { listChatHistoryByPageForAdmin } from '@/api/chatHistoryController'
import { formatDate } from '@/utils/date'

const router = useRouter()
const data = ref<API.ChatHistory[]>([])
const total = ref(0)
const searchParams = reactive<API.ChatHistoryQueryRequest>({ pageNum: 1, pageSize: 10 })
const columns = [
  { title: 'ID', dataIndex: 'id', width: 180 }, { title: '应用 ID', dataIndex: 'appId', width: 180 },
  { title: '用户 ID', dataIndex: 'userId', width: 180 }, { title: '类型', dataIndex: 'messageType', width: 120 },
  { title: '消息内容', dataIndex: 'message' }, { title: '父消息 ID', dataIndex: 'parentId', width: 180 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 }, { title: '操作', key: 'action', width: 110 },
]
const pagination = computed(() => ({ current: searchParams.pageNum, pageSize: searchParams.pageSize, total: total.value, showSizeChanger: true, showTotal: (value: number) => `共 ${value} 条` }))
const fetchData = async () => {
  const res = await listChatHistoryByPageForAdmin({ ...searchParams })
  if (res.data.data) { data.value = res.data.data.records ?? []; total.value = Number(res.data.data.totalRow ?? 0) }
  else message.error('获取对话失败：' + res.data.message)
}
const search = () => { searchParams.pageNum = 1; fetchData() }
const resetSearch = () => {
  searchParams.id = undefined
  searchParams.appId = undefined
  searchParams.userId = undefined
  searchParams.message = undefined
  searchParams.messageType = undefined
  searchParams.pageNum = 1
  fetchData()
}
const tableChange = (page: { current?: number; pageSize?: number }) => { searchParams.pageNum = page.current; searchParams.pageSize = page.pageSize; fetchData() }
const normalizeMessageType = (messageType?: string) => (messageType || '').trim().toLowerCase()
const getMessageTypeText = (messageType?: string) => {
  const type = normalizeMessageType(messageType)
  if (type.includes('user') || type.includes('human') || type.includes('request') || type.includes('用户')) return '用户'
  if (type === 'ai' || type.includes('assistant') || type.includes('model') || type.includes('answer') || type.includes('response') || type.includes('助手')) return 'AI'
  if (type === 'error' || type.includes('错误') || type.includes('失败')) return '错误'
  return messageType || '-'
}
const getMessageTypeClass = (messageType?: string) => {
  const text = getMessageTypeText(messageType)
  if (text === '用户') return 'user-message-tag'
  if (text === 'AI') return 'ai-message-tag'
  if (text === '错误') return 'error-message-tag'
  return 'plain-tag'
}
const viewApp = (appId?: string) => appId && router.push(`/app/chat/${appId}`)
onMounted(fetchData)
</script>

<style scoped>
.filter-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  align-items: center;
}
.user-message-tag,
.ai-message-tag,
.error-message-tag,
.plain-tag {
  border: 0;
  border-radius: 999px;
  padding: 2px 10px;
  font-weight: 700;
}
.user-message-tag { color: #2f5f8f; background: #edf6ff; }
.ai-message-tag { color: #07866f; background: #e7fbf5; }
.error-message-tag { color: #b42318; background: #fff1f0; }
.plain-tag { color: #697586; background: #f1f4f8; }
.message-preview {
  max-width: 520px;
  margin-bottom: 0;
  color: #465160;
  font-size: 13px;
  line-height: 1.6;
}
.action-group :deep(.ant-btn-link) {
  padding-inline: 4px;
  color: #3377ff;
  font-weight: 650;
}
@media (max-width: 1100px) {
  .filter-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .filter-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
