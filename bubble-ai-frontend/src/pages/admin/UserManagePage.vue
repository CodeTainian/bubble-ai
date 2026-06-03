<template>
  <div class="admin-page user-manage-page">
    <div class="page-title">
      <div>
        <h2>用户管理</h2>
        <p>管理平台用户、角色与账号状态</p>
      </div>
    </div>
    <section class="filter-card">
      <div class="filter-head">
        <div class="filter-title"><FilterOutlined /> 筛选条件</div>
        <a-space>
          <a-button @click="resetSearch"><ReloadOutlined /> 重置</a-button>
          <a-button type="primary" @click="doSearch"><SearchOutlined /> 搜索</a-button>
        </a-space>
      </div>
      <a-form class="filter-grid" :model="searchParams" @finish="doSearch">
        <div class="filter-field">
          <label>账号</label>
          <a-input v-model:value="searchParams.userAccount" allow-clear placeholder="输入账号" size="large" />
        </div>
        <div class="filter-field">
          <label>用户名</label>
          <a-input v-model:value="searchParams.userName" allow-clear placeholder="输入用户名" size="large" />
        </div>
      </a-form>
    </section>
    <section class="table-card">
      <a-table
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        row-key="id"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'id'">
            <span class="muted mono">{{ record.id }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userAvatar'">
            <a-avatar :size="42" :src="record.userAvatar" class="user-avatar">
              {{ getUserInitial(record) }}
            </a-avatar>
          </template>
          <template v-else-if="column.dataIndex === 'userName'">
            <span class="name-cell">{{ record.userName || '未命名用户' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userAccount'">
            <span class="muted">{{ record.userAccount || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userProfile'">
            <span class="muted">{{ record.userProfile || '暂无简介' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag :class="record.userRole === 'admin' ? 'admin-tag' : 'user-tag'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="muted">{{ formatDate(record.createTime) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button danger type="link" class="delete-action" @click="doDelete(record.id)">删除</a-button>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>
<script lang="ts" setup>
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUser, listUserVoByPage } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 180,
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    width: 180,
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    width: 180,
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
    width: 100,
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
    ellipsis: true,
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
    width: 120,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 170,
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
  },
]
//数据
const data = ref<API.UserVO[]>([])
const total = ref(0)
//搜素条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 3,
})
//分页参数
const pagination = computed(() => {
  return{
    current: searchParams.pageNum??1,
    pageSize: searchParams.pageSize?? 3,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total:number) => `共${total}条`
  }
})
//用户切换页号和页面大小，更新searcParam参数，并出发搜索
const doTableChange = (page: { current?: number; pageSize?: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize= page.pageSize
  fetchData();
}
//获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = Number(res.data.data.totalRow ?? 0)
  } else {
    message.error('获取数据失败' + res.data.message)
  }
}
//搜索
const doSearch = () => {
  searchParams.pageNum=1
  fetchData()
}
const resetSearch = () => {
  searchParams.userAccount = undefined
  searchParams.userName = undefined
  searchParams.pageNum = 1
  fetchData()
}
const formatDate = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
const getUserInitial = (user: API.UserVO) => (user.userName || user.userAccount || 'U').slice(0, 1)
//删除
const doDelete =async (id:string) =>{
  if (!id){
    return
  }
const res = await deleteUser({id})
  if (res.data.code === 0) {
    message.success("删除成功")
    fetchData();
  }else {
    message.error("删除失败"+res.data.message)
  }
}
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.admin-page { min-width: 0; color: #17212b; font-family: "Inter", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; }
.page-title { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 24px; }
.page-title h2 { margin: 0 0 7px; color: #111827; font-size: 28px; font-weight: 850; letter-spacing: -.3px; }
.page-title p { margin: 0; color: #8a96a8; font-size: 14px; }
.muted { color: #8d99a8; }
.mono { font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 12px; }
.filter-card,
.table-card {
  overflow: hidden;
  border: 1px solid #edf0f5;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, .045);
}
.filter-card { margin-bottom: 22px; }
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
  grid-template-columns: repeat(2, minmax(240px, 320px));
  gap: 16px;
  padding: 20px 22px 22px;
}
.filter-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.filter-field label {
  color: #5f6b7a;
  font-size: 13px;
  font-weight: 700;
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
.user-avatar { color: #fff; background: linear-gradient(135deg, #35d7c1, #4d8dff); font-weight: 800; }
.admin-tag,
.user-tag {
  border: 0;
  border-radius: 999px;
  padding: 2px 10px;
  font-weight: 700;
}
.admin-tag { color: #07866f; background: #e7fbf5; }
.user-tag { color: #2f5f8f; background: #edf6ff; }
.delete-action {
  color: #ee6b6e;
  font-weight: 650;
}
.table-card :deep(.ant-pagination) {
  margin: 18px 22px;
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
