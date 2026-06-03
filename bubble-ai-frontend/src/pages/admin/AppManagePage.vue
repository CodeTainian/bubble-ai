<template>
  <div class="app-manage-page">
    <div class="page-title"><h2>应用管理</h2><p>管理站内应用、封面与精选内容</p></div>
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
          <template v-if="column.dataIndex === 'cover'">
            <a-image v-if="record.cover" :src="record.cover" :width="100" />
            <span v-else class="muted">暂无封面</span>
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag :color="record.priority === 99 ? 'cyan' : 'default'">{{ record.priority === 99 ? '精选' : record.priority ?? 0 }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">{{ formatDate(record.createTime) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
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
  { title: 'ID', dataIndex: 'id', width: 70 }, { title: '应用名称', dataIndex: 'appName' },
  { title: '封面', dataIndex: 'cover' }, { title: '生成类型', dataIndex: 'codeGenType' },
  { title: '用户 ID', dataIndex: 'userId' }, { title: '优先级', dataIndex: 'priority' },
  { title: '创建时间', dataIndex: 'createTime' }, { title: '操作', key: 'action', width: 210 },
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
.app-manage-page { min-width: 0; }
.page-title { margin-bottom: 22px; }
.page-title h2 { margin-bottom: 5px; color: #172326; font-size: 30px; font-weight: 800; }.page-title p { margin: 0; color: #8a999b; }.muted { color: #b6bfc0; }
.filter-card {
  margin-bottom: 22px;
  overflow: hidden;
  border: 1px solid #e8eff2;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(33, 96, 113, .07);
}
.filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px;
  border-bottom: 1px solid #edf3f5;
  background: linear-gradient(180deg, #fbfefe, #f7fbfb);
}
.filter-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #273a40;
  font-size: 15px;
  font-weight: 800;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: center;
  padding: 18px;
}
.filter-field {
  display: flex;
  align-items: center;
  min-width: 0;
}
.filter-field label {
  flex: 0 0 auto;
  color: #526367;
  font-size: 15px;
  font-weight: 800;
  line-height: 40px;
  white-space: nowrap;
}

.table-card {
  overflow: hidden;
  border: 1px solid #edf1f4;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(33, 96, 113, .06);
}

.table-card :deep(.ant-table-thead > tr > th) {
  color: #314146;
  background: #fafcfc;
  font-weight: 800;
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
