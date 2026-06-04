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
            <span class="muted">{{ formatDate(record.createTime, 'YYYY-MM-DD HH:mm') }}</span>
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
import { formatDate } from '@/utils/date'

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
const data = ref<API.UserVO[]>([])
const total = ref(0)
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 3,
})
const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 3,
  total: total.value,
  showSizeChanger: true,
  showTotal: (total: number) => `共${total}条`,
}))
const doTableChange = (page: { current?: number; pageSize?: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}
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
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}
const resetSearch = () => {
  searchParams.userAccount = undefined
  searchParams.userName = undefined
  searchParams.pageNum = 1
  fetchData()
}
const getUserInitial = (user: API.UserVO) => (user.userName || user.userAccount || 'U').slice(0, 1)
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败' + res.data.message)
  }
}
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.filter-grid {
  grid-template-columns: repeat(2, minmax(240px, 320px));
}
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
