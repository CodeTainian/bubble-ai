<template>
  <div class="home-page">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">BUBBLE AI · NO CODE STUDIO</span>
        <h1>一句话 <img src="@/assets/logo.ico" alt="" /> 呈所想</h1>
        <p>与 AI 对话，轻松创建应用和网站</p>
      </div>
      <div class="prompt-panel">
        <a-textarea
          v-model:value="prompt"
          :auto-size="{ minRows: 4, maxRows: 6 }"
          :bordered="false"
          placeholder="描述你想创建的应用，例如：帮我做一个简洁的个人博客网站"
          @pressEnter="handleEnter"
        />
        <div class="prompt-actions">
          <div class="suggestion-title"><BulbOutlined /> 从一个灵感开始</div>
          <a-button type="primary" shape="circle" size="large" :loading="creating" @click="createApp">
            <ArrowUpOutlined />
          </a-button>
        </div>
      </div>
      <div class="suggestions">
        <button v-for="item in suggestions" :key="item" @click="prompt = item">{{ item }}</button>
      </div>
    </section>

    <section class="showcase">
      <div class="section-heading">
        <div><span>MY CREATIONS</span><h2>我的应用</h2></div>
        <a-input-search v-model:value="myParams.appName" allow-clear placeholder="搜索我的应用" @search="searchMine" />
      </div>
      <a-spin :spinning="loadingMine">
        <div v-if="myApps.length" class="card-grid">
          <AppCard v-for="item in myApps" :key="item.id" :app="item" editable @edit="editMine" @delete="removeMine" />
        </div>
        <a-empty v-else description="暂时还没有应用，先用一句话创造一个吧" />
      </a-spin>
      <a-pagination
        v-if="myTotal > myParams.pageSize!"
        v-model:current="myParams.pageNum"
        :page-size="myParams.pageSize"
        :total="myTotal"
        hide-on-single-page
        @change="fetchMine"
      />
    </section>

    <section class="showcase featured">
      <div class="section-heading">
        <div><span>CURATED IDEAS</span><h2>精选应用</h2></div>
        <a-input-search v-model:value="goodParams.appName" allow-clear placeholder="搜索精选应用" @search="searchGood" />
      </div>
      <a-spin :spinning="loadingGood">
        <div v-if="goodApps.length" class="card-grid">
          <AppCard v-for="item in goodApps" :key="item.id" :app="item" featured />
        </div>
        <a-empty v-else description="精选应用正在赶来的路上" />
      </a-spin>
      <a-pagination
        v-if="goodTotal > goodParams.pageSize!"
        v-model:current="goodParams.pageNum"
        :page-size="goodParams.pageSize"
        :total="goodTotal"
        hide-on-single-page
        @change="fetchGood"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpOutlined, BulbOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { addApp, deleteApp, listGoodAppVoByPage, listMyAppByPage } from '@/api/appController'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const prompt = ref('')
const creating = ref(false)
const loadingMine = ref(false)
const loadingGood = ref(false)
const myApps = ref<API.AppVO[]>([])
const goodApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const goodTotal = ref(0)
const myParams = reactive<API.AppQueryRequest>({ pageNum: 1, pageSize: 6 })
const goodParams = reactive<API.AppQueryRequest>({ pageNum: 1, pageSize: 6 })
const suggestions = ['波普风电商页面', '企业官网', '个人博客', '旅行攻略网站']

const createApp = async () => {
  if (!prompt.value.trim()) return message.warning('先告诉我你想创建什么')
  creating.value = true
  try {
    const res = await addApp({ initPrompt: prompt.value.trim() })
    if (res.data.code === 0 && res.data.data) {
      await router.push({ path: `/app/chat/${res.data.data}`, query: { auto: '1' } })
    } else message.error('创建失败：' + res.data.message)
  } finally { creating.value = false }
}
const handleEnter = (event: KeyboardEvent) => {
  if (!event.shiftKey) { event.preventDefault(); createApp() }
}
const fetchMine = async () => {
  loadingMine.value = true
  try {
    const res = await listMyAppByPage({ ...myParams })
    myApps.value = res.data.data?.records ?? []
    myTotal.value = Number(res.data.data?.totalRow ?? 0)
  } finally { loadingMine.value = false }
}
const fetchGood = async () => {
  loadingGood.value = true
  try {
    const res = await listGoodAppVoByPage({ ...goodParams })
    goodApps.value = res.data.data?.records ?? []
    goodTotal.value = Number(res.data.data?.totalRow ?? 0)
  } finally { loadingGood.value = false }
}
const searchMine = () => { myParams.pageNum = 1; fetchMine() }
const searchGood = () => { goodParams.pageNum = 1; fetchGood() }
const editMine = (app: API.AppVO) => app.id && router.push(`/app/edit/${app.id}`)
const removeMine = (app: API.AppVO) => {
  Modal.confirm({
    title: `确认删除“${app.appName || '未命名应用'}”？`,
    content: '删除后无法恢复。',
    okType: 'danger',
    async onOk() {
      const res = await deleteApp({ id: app.id })
      if (res.data.code === 0) { message.success('删除成功'); fetchMine() }
      else message.error('删除失败：' + res.data.message)
    },
  })
}
onMounted(() => { fetchMine(); fetchGood() })
</script>

<style scoped>
.home-page { margin: -24px; background: #f8fbfb; }
.hero { padding: 100px 24px 74px; text-align: center; background: radial-gradient(circle at 80% 36%, rgba(81, 227, 215, .32), transparent 30%), radial-gradient(circle at 28% 84%, rgba(134, 231, 205, .18), transparent 30%), linear-gradient(145deg, #fff 16%, #f4fbfa 66%, #e4f8fa); }
.hero-copy .eyebrow, .section-heading span { color: #1cafa6; font-size: 11px; font-weight: 700; letter-spacing: 2px; }
h1 { display: flex; align-items: center; justify-content: center; gap: 13px; margin: 16px 0 7px; color: #10212a; font-size: clamp(38px, 5vw, 62px); letter-spacing: 5px; }
h1 img { width: 58px; height: 58px; }
.hero-copy p { color: #7b888b; font-size: 18px; letter-spacing: 2px; }
.prompt-panel { max-width: 920px; margin: 42px auto 18px; padding: 16px 18px 14px; border: 1px solid rgba(120, 177, 176, .26); border-radius: 26px; background: rgba(255, 255, 255, .9); box-shadow: 0 22px 50px rgba(62, 152, 148, .12); text-align: left; }
.prompt-panel textarea { font-size: 17px; line-height: 1.8; resize: none; }
.prompt-actions, .section-heading { display: flex; align-items: center; justify-content: space-between; }
.suggestion-title { color: #9ba5a6; font-size: 13px; }
.suggestions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
.suggestions button { padding: 9px 15px; border: 1px solid #e5ecec; border-radius: 20px; color: #667476; background: rgba(255,255,255,.86); cursor: pointer; }
.showcase { padding: 58px 62px 22px; background: #fff; }
.featured { padding-bottom: 70px; }
.section-heading { margin-bottom: 24px; }
.section-heading h2 { margin: 5px 0 0; color: #15272b; font-size: 30px; }
.section-heading .ant-input-search { width: 220px; }
.card-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px; }
.ant-pagination { margin-top: 25px; text-align: center; }
@media (max-width: 800px) { .showcase { padding: 42px 22px 16px; } .card-grid { grid-template-columns: 1fr; } .section-heading { align-items: flex-end; } .section-heading .ant-input-search { width: 160px; } }
</style>
