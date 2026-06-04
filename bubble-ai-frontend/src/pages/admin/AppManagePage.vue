<template>
  <div class="admin-page app-manage-page">
    <div class="page-title">
      <div>
        <h2>应用管理</h2>
        <p>管理站内应用、封面与精选内容</p>
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
          <label>应用 ID：</label>
          <a-input v-model:value="searchParams.id" allow-clear placeholder="输入 ID" size="large" />
        </div>
        <div class="filter-field">
          <label>应用名称：</label>
          <a-input v-model:value="searchParams.appName" allow-clear placeholder="输入应用名称" size="large" />
        </div>
        <div class="filter-field">
          <label>用户 ID：</label>
          <a-input v-model:value="searchParams.userId" allow-clear placeholder="输入用户 ID" size="large" />
        </div>
        <div class="filter-field">
          <label>生成类型：</label>
          <a-input v-model:value="searchParams.codeGenType" allow-clear placeholder="如 html" size="large" />
        </div>
      </a-form>
    </section>
    <section class="table-card">
      <a-table :columns="columns" :data-source="data" :pagination="pagination" row-key="id" @change="tableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'id'">
            <span class="muted mono">{{ record.id }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'appName'">
            <span class="name-cell">{{ record.appName || '未命名应用' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'cover'">
            <AppCover v-if="record.cover" :cover="record.cover" :alt="record.appName" preview variant="table" />
            <span v-else class="muted">暂无封面</span>
          </template>
          <template v-else-if="column.dataIndex === 'codeGenType'">
            <a-tag class="type-tag">{{ record.codeGenType || '-' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'userId'">
            <span class="muted mono">{{ record.userId || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag :class="record.priority === 99 ? 'featured-tag' : 'plain-tag'">{{ record.priority === 99 ? '精选' : record.priority ?? 0 }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">{{ formatDate(record.createTime, 'YYYY-MM-DD HH:mm') }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space class="action-group">
              <a-button type="link" @click="edit(record.id)">编辑</a-button>
              <a-button type="link" @click="feature(record)">精选</a-button>
              <a-button danger type="link" @click="remove(record)">删除</a-button>
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
import AppCover from '@/components/AppCover.vue'
import { deleteAppByAdmin, listAppVoByPageByAdmin, updateAppByAdmin } from '@/api/appController'
import { confirmDeleteApp } from '@/utils/app'
import { formatDate } from '@/utils/date'

const router = useRouter()
const data = ref<API.AppVO[]>([])
const total = ref(0)
const searchParams = reactive<API.AppQueryRequest>({ pageNum: 1, pageSize: 10 })
const columns = [
  { title: 'ID', dataIndex: 'id', width: 180 }, { title: '应用名称', dataIndex: 'appName', width: 220 },
  { title: '封面', dataIndex: 'cover', width: 130 }, { title: '生成类型', dataIndex: 'codeGenType', width: 120 },
  { title: '用户 ID', dataIndex: 'userId', width: 190 }, { title: '优先级', dataIndex: 'priority', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', width: 150 }, { title: '操作', key: 'action', width: 190 },
]
const pagination = computed(() => ({ current: searchParams.pageNum, pageSize: searchParams.pageSize, total: total.value, showSizeChanger: true, showTotal: (value: number) => `共 ${value} 条` }))
const fetchData = async () => {
  const res = await listAppVoByPageByAdmin({ ...searchParams })
  if (res.data.data) { data.value = res.data.data.records ?? []; total.value = Number(res.data.data.totalRow ?? 0) }
  else message.error('获取应用失败：' + res.data.message)
}
const search = () => { searchParams.pageNum = 1; fetchData() }
const resetSearch = () => {
  searchParams.id = undefined
  searchParams.appName = undefined
  searchParams.userId = undefined
  searchParams.codeGenType = undefined
  searchParams.pageNum = 1
  fetchData()
}
const tableChange = (page: { current?: number; pageSize?: number }) => { searchParams.pageNum = page.current; searchParams.pageSize = page.pageSize; fetchData() }
const edit = (id?: string) => id && router.push(`/admin/app/edit/${id}`)
const feature = async (app: API.AppVO) => {
  const res = await updateAppByAdmin({ id: app.id, priority: 99 })
  if (res.data.code === 0) { message.success('已设为精选应用'); fetchData() } else message.error('操作失败：' + res.data.message)
}
const remove = (app: API.AppVO) => {
  confirmDeleteApp(app, async () => {
    const res = await deleteAppByAdmin({ id: app.id })
    if (res.data.code === 0) { message.success('删除成功'); fetchData() }
    else message.error('删除失败：' + res.data.message)
  })
}
onMounted(fetchData)
</script>

<style scoped>
.filter-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: center;
}
.type-tag,
.plain-tag,
.featured-tag {
  border: 0;
  border-radius: 999px;
  padding: 2px 10px;
  font-weight: 700;
}
.type-tag { color: #2f5f8f; background: #edf6ff; }
.plain-tag { color: #697586; background: #f1f4f8; }
.featured-tag { color: #07866f; background: #e7fbf5; }
.action-group :deep(.ant-btn-link) {
  padding-inline: 4px;
  color: #3377ff;
  font-weight: 650;
}
.action-group :deep(.ant-btn-link.ant-btn-dangerous) {
  color: #ee6b6e;
}
@media (max-width: 900px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .filter-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
