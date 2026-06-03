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
            <a-image v-if="record.cover" :src="record.cover" :width="92" class="cover-preview" />
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
          <template v-else-if="column.dataIndex === 'createTime'">{{ formatDate(record.createTime) }}</template>
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
import { Modal, message } from 'ant-design-vue'
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { deleteAppByAdmin, listAppVoByPageByAdmin, updateAppByAdmin } from '@/api/appController'

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
const remove = (app: API.AppVO) => Modal.confirm({ title: `确认删除“${app.appName || '未命名应用'}”？`, okType: 'danger', async onOk() { const res = await deleteAppByAdmin({ id: app.id }); if (res.data.code === 0) { message.success('删除成功'); fetchData() } else message.error('删除失败：' + res.data.message) } })
const formatDate = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
onMounted(fetchData)
</script>

<style scoped>
.admin-page { min-width: 0; color: #17212b; font-family: "Inter", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; }
.page-title { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 24px; }
.page-title h2 { margin: 0 0 7px; color: #111827; font-size: 28px; font-weight: 850; letter-spacing: -.3px; }
.page-title p { margin: 0; color: #8a96a8; font-size: 14px; }
.muted { color: #9aa6b2; }
.mono { font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 12px; }
.filter-card {
  margin-bottom: 22px;
  overflow: hidden;
  border: 1px solid #edf0f5;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, .045);
}
.filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-bottom: 1px solid #f0f3f7;
  background: linear-gradient(180deg, #fff, #fbfcfe);
}
.filter-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f2a37;
  font-size: 15px;
  font-weight: 750;
}
.filter-head :deep(.ant-btn) {
  height: 38px;
  border-radius: 999px;
  padding: 0 18px;
  font-weight: 700;
}
.filter-head :deep(.ant-btn-primary) {
  border-color: #1677ff;
  background: #1677ff;
  box-shadow: 0 10px 22px rgba(22, 119, 255, .18);
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: center;
  padding: 20px 22px 22px;
}
.filter-field {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}
.filter-field label {
  color: #5f6b7a;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}
.filter-field :deep(.ant-input-affix-wrapper),
.filter-field :deep(.ant-input) {
  height: 40px;
  border-color: #e5eaf1;
  border-radius: 12px;
  background: #fbfcfe;
}
.filter-field :deep(.ant-input-affix-wrapper) {
  display: flex;
  align-items: center;
  padding-top: 0;
  padding-bottom: 0;
}
.filter-field :deep(.ant-input),
.filter-field :deep(.ant-input-affix-wrapper > input.ant-input) {
  height: 38px;
  line-height: 38px;
}

.table-card {
  overflow: hidden;
  border: 1px solid #edf0f5;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 16px 42px rgba(15, 23, 42, .05);
}

.table-card :deep(.ant-table-thead > tr > th) {
  border-bottom: 1px solid #edf0f5;
  color: #5d6b7c;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 750;
}
.table-card :deep(.ant-table-tbody > tr > td) {
  height: 70px;
  border-bottom: 1px solid #f1f4f8;
  color: #1f2937;
}
.table-card :deep(.ant-table-tbody > tr:hover > td) {
  background: #f7fbff;
}
.name-cell { color: #111827; font-weight: 750; }
.cover-preview :deep(img) { border-radius: 12px; object-fit: cover; }
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
.table-card :deep(.ant-pagination) {
  margin: 18px 22px;
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
