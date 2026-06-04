<template>
  <div class="home-page">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">BUBBLE AI · NO CODE STUDIO</span>
        <h1><span class="headline-text">一句话</span> <img src="@/assets/logo.svg" alt="" /> <span class="headline-text">呈所想</span></h1>
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
        <button v-for="item in suggestions" :key="item.title" class="suggestion-card" @click="prompt = item.prompt">
          {{ item.title }}
        </button>
      </div>
    </section>

    <AppShowcaseSection
      v-model:search-value="myParams.appName"
      :apps="myApps"
      editable
      empty-description="暂时还没有应用，先用一句话创造一个吧"
      eyebrow="MY CREATIONS"
      :loading="loadingMine"
      :page-num="myParams.pageNum || 1"
      :page-size="myParams.pageSize || 6"
      search-placeholder="搜索我的应用"
      title="我的应用"
      :total="myTotal"
      @delete="removeMine"
      @edit="editMine"
      @page-change="changeMinePage"
      @search="searchMine"
    />

    <AppShowcaseSection
      v-model:search-value="goodParams.appName"
      :apps="goodApps"
      empty-description="精选应用正在赶来的路上"
      eyebrow="CURATED IDEAS"
      featured
      :loading="loadingGood"
      owner-only
      :page-num="goodParams.pageNum || 1"
      :page-size="goodParams.pageSize || 6"
      search-placeholder="搜索精选应用"
      title="精选应用"
      :total="goodTotal"
      @page-change="changeGoodPage"
      @search="searchGood"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpOutlined, BulbOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { addApp, deleteApp, listGoodAppVoByPage, listMyAppByPage } from '@/api/appController'
import AppShowcaseSection from '@/components/AppShowcaseSection.vue'
import { confirmDeleteApp } from '@/utils/app'

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
const suggestions = [
  {
    title: '企业官网',
    prompt: '帮我生成一个科技服务公司的企业官网，包含首页首屏、服务介绍、核心优势、客户案例、团队介绍和联系表单；整体风格简洁专业，蓝白配色，适合展示 AI 软件定制与数字化转型能力。',
  },
  {
    title: '产品落地页',
    prompt: '帮我生成一个 SaaS 产品落地页，用于推广一款团队协作工具；需要包含产品价值主张、功能亮点、使用流程、价格套餐、用户评价和立即试用按钮，页面风格现代、有科技感。',
  },
  {
    title: '个人作品集',
    prompt: '帮我生成一个前端开发者个人作品集网站，包含个人简介、技能栈、精选项目、项目截图展示、工作经历和联系方式；整体设计要清爽高级，突出专业能力和项目成果。',
  },
  {
    title: '门店展示页',
    prompt: '帮我生成一个咖啡店品牌展示网站，包含品牌故事、门店环境、招牌饮品、菜单价格、营业时间、地图位置和预约联系入口；视觉风格温暖但不花哨，适合移动端浏览。',
  },
]

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
const changeMinePage = (page: number, pageSize: number) => {
  myParams.pageNum = page
  myParams.pageSize = pageSize
  fetchMine()
}
const changeGoodPage = (page: number, pageSize: number) => {
  goodParams.pageNum = page
  goodParams.pageSize = pageSize
  fetchGood()
}
const editMine = (app: API.AppVO) => app.id && router.push(`/app/edit/${app.id}`)
const removeMine = (app: API.AppVO) => {
  confirmDeleteApp(app, async () => {
    const res = await deleteApp({ id: app.id })
    if (res.data.code === 0) { message.success('删除成功'); fetchMine() }
    else message.error('删除失败：' + res.data.message)
  })
}
onMounted(() => { fetchMine(); fetchGood() })
</script>

<style scoped>
.home-page { position: relative; min-height: calc(100vh - 64px); overflow: hidden; padding: 86px 24px 56px; background: radial-gradient(circle at 18% 6%, rgba(255,255,255,.68), transparent 26%), radial-gradient(circle at 76% 18%, rgba(255,255,255,.22), transparent 24%), linear-gradient(180deg, rgba(255,255,255,.9) 0%, rgba(218,252,248,.72) 34%, rgba(116,210,246,.42) 76%, rgba(102,166,255,.4) 100%); }
.home-page::before { position: absolute; inset: 0; background: radial-gradient(circle at 70% 48%, rgba(37,231,218,.2), transparent 24%), radial-gradient(circle at 36% 76%, rgba(102,166,255,.24), transparent 30%); content: ""; pointer-events: none; }
.hero { position: relative; z-index: 1; max-width: 1100px; min-height: 640px; margin: 0 auto; padding: 70px 24px 36px; text-align: center; }
.hero-copy .eyebrow { color: #0eaaa0; font-size: 12px; font-weight: 900; letter-spacing: 7px; }
h1 { display: flex; align-items: center; justify-content: center; gap: 15px; margin: 18px 0 12px; color: #101d28; font-family: "YouSheBiaoTiHei", "Alimama ShuHeiTi", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; font-size: clamp(38px, 4.7vw, 66px); font-weight: 900; letter-spacing: 1px; line-height: 1.08; text-shadow: 0 10px 34px rgba(17, 54, 76, .1); }
h1 .headline-text { background: linear-gradient(110deg, #101d28 8%, #142d4f 48%, #0aaea4 100%); -webkit-background-clip: text; background-clip: text; color: transparent; }
h1 img { width: clamp(46px, 4.2vw, 58px); height: clamp(46px, 4.2vw, 58px); border-radius: 16px; box-shadow: 0 18px 36px rgba(18, 80, 152, .18); }
.hero-copy p { color: #687981; font-size: 20px; font-weight: 700; letter-spacing: 6px; }
.prompt-panel { max-width: 920px; margin: 72px auto 18px; padding: 18px 18px 16px; border: 1px solid rgba(255,255,255,.7); border-radius: 28px; background: rgba(255, 255, 255, .82); box-shadow: 0 28px 80px rgba(24, 108, 151, .18); text-align: left; backdrop-filter: blur(16px); }
.prompt-panel textarea { color: #223238; font-size: 18px; line-height: 1.8; resize: none; }
.prompt-actions { display: flex; align-items: center; justify-content: space-between; }
.suggestion-title { color: #9ba5a6; font-size: 13px; }
.suggestions { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; margin: 24px auto 0; }
.suggestion-card { min-height: 0; padding: 9px 18px; border: 1px solid rgba(255,255,255,.72); border-radius: 14px; color: #5d7178; background: rgba(255,255,255,.78); box-shadow: 0 10px 28px rgba(45, 123, 142, .08); cursor: pointer; font-size: 14px; font-weight: 700; transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease, background .2s ease; backdrop-filter: blur(8px); }
.suggestion-card:hover { border-color: rgba(28, 175, 166, .52); color: #10212a; box-shadow: 0 16px 38px rgba(45, 123, 142, .14); transform: translateY(-2px); }
@media (max-width: 800px) { .home-page { padding: 48px 14px 42px; } .hero { min-height: auto; padding: 48px 4px 36px; } h1 { flex-wrap: wrap; letter-spacing: 1px; } .hero-copy p { letter-spacing: 2px; } .prompt-panel { margin-top: 42px; } }
</style>
