<template>
  <div class="chat-page">
    <header class="studio-header">
      <RouterLink to="/" class="app-title">
        <img src="@/assets/logo.svg" alt="Bubble AI" />
        <div><strong>{{ app.appName || 'AI 应用工作台' }}</strong><span>Bubble AI Studio</span></div>
      </RouterLink>
      <div class="header-center"><span class="status-dot" :class="{ active: generating }"></span>{{ generating ? '正在生成应用' : '已同步最新版本' }}</div>
      <a-space>
        <a-tooltip title="打开生成页面"><a-button :disabled="!previewReady" @click="openPreview"><CodeOutlined /></a-button></a-tooltip>
        <a-tooltip title="下载 / 打开代码资源"><a-button :disabled="!previewReady" @click="downloadCode"><DownloadOutlined /></a-button></a-tooltip>
        <a-button type="primary" :loading="deploying" :disabled="!previewReady" @click="deploy"><RocketOutlined /> 部署</a-button>
      </a-space>
    </header>

    <main ref="workspace" class="workspace" :class="{ resizing: resizingPane }" :style="{ gridTemplateColumns }">
      <section class="conversation">
        <div ref="messageList" class="message-list" @scroll="handleMessageListScroll">
          <div class="welcome">
            <div class="ai-mark"><img src="@/assets/logo.svg" alt="" /></div>
            <div><h2>和 AI 一起把想法变成作品</h2><p>描述越具体，生成效果越贴近你的设想。你也可以继续对话，一步一步完善页面。</p></div>
          </div>
          <article v-for="(item, index) in messages" :key="index" class="message-row" :class="item.role">
            <div v-if="item.role === 'assistant'" class="avatar"><img src="@/assets/logo.svg" alt="" /></div>
            <div class="bubble">
              <div class="message-role">{{ item.role === 'user' ? '你' : 'Bubble AI' }}</div>
              <div v-if="item.role === 'assistant'" class="message-content">
                <template v-if="item.content">
                  <template v-for="(block, blockIndex) in parseMessageBlocks(item.content)" :key="blockIndex">
                    <div v-if="block.type === 'text'" class="message-text">{{ block.content }}</div>
                    <section v-else class="code-block">
                      <div class="code-header">
                        <span>{{ block.language || 'code' }}</span>
                        <span v-if="!block.closed" class="streaming-label">实时生成中</span>
                      </div>
                      <pre><code v-html="highlightCode(block.content, block.language)"></code></pre>
                    </section>
                  </template>
                </template>
                <span v-else>正在思考...</span>
              </div>
              <div v-else class="message-content">{{ item.content }}</div>
              <span v-if="item.pending" class="typing"><i></i><i></i><i></i></span>
            </div>
            <a-avatar v-if="item.role === 'user'" :size="30" :src="loginUserStore.loginUser.userAvatar" class="avatar user-avatar">
              {{ loginUserInitial }}
            </a-avatar>
          </article>
        </div>
        <button v-if="!followingOutput" class="scroll-to-latest" type="button" @click="resumeFollowingOutput">查看最新内容</button>
        <div class="composer-wrap">
          <div class="composer">
            <div class="composer-input" :class="{ disabled: !canChat }" :title="chatPermissionTip || undefined">
              <a-textarea v-model:value="input" :bordered="false" :disabled="!canChat" :auto-size="{ minRows: 3, maxRows: 5 }" placeholder="描述得越详细，页面越具体。可以继续提出修改意见..." @pressEnter="handleEnter" />
            </div>
            <div class="composer-footer">
              <span><MessageOutlined /> 继续对话完善页面</span>
              <a-button type="primary" shape="circle" :loading="generating" :disabled="!canChat || !input.trim()" @click="sendMessage"><ArrowUpOutlined /></a-button>
            </div>
          </div>
        </div>
      </section>

      <button
        class="resize-handle"
        type="button"
        aria-label="调整对话区宽度"
        @pointerdown="startResize('conversation', $event)"
        @keydown.left.prevent="resizeWithKeyboard('conversation', -RESIZE_STEP)"
        @keydown.right.prevent="resizeWithKeyboard('conversation', RESIZE_STEP)"
      ></button>

      <section class="preview-pane">
        <div class="preview-toolbar">
          <span><DesktopOutlined /> 网页预览</span>
          <span class="url">{{ previewUrl }}</span>
          <a-button type="text" :disabled="!previewReady" @click="refreshPreview"><ReloadOutlined /></a-button>
        </div>
        <div class="preview-stage">
          <iframe v-if="previewReady" :key="previewKey" :src="previewUrl" title="生成应用预览"></iframe>
          <div v-else class="empty-preview">
            <div class="preview-illustration"><CodeOutlined /></div>
            <h2>{{ generating ? '正在搭建你的应用' : '等待生成网页' }}</h2>
            <p>{{ generating ? 'AI 正在编写页面文件，完成后会自动在这里展示。' : '在左侧输入你的想法，生成结果会出现在这里。' }}</p>
          </div>
        </div>
      </section>

      <button
        class="resize-handle"
        type="button"
        aria-label="调整版本栏宽度"
        @pointerdown="startResize('versions', $event)"
        @keydown.left.prevent="resizeWithKeyboard('versions', RESIZE_STEP)"
        @keydown.right.prevent="resizeWithKeyboard('versions', -RESIZE_STEP)"
      ></button>

      <aside class="version-bar">
        <h3>版本</h3>
        <button class="version-card active">
          <span>v1</span>
          <div class="version-thumb">
            <img v-if="versionCoverUrl" :src="versionCoverUrl" alt="应用封面" />
            <img v-else src="@/assets/logo.svg" alt="" class="fallback-logo" />
          </div>
        </button>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { ArrowUpOutlined, CodeOutlined, DesktopOutlined, DownloadOutlined, MessageOutlined, ReloadOutlined, RocketOutlined } from '@ant-design/icons-vue'
import hljs from 'highlight.js/lib/core'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import xml from 'highlight.js/lib/languages/xml'
import 'highlight.js/styles/github.css'
import { deployApp, getAppVoById } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { APP_PREVIEW_BASE_URL } from '@/config/env'

type ChatMessage = { role: 'user' | 'assistant'; content: string; pending?: boolean }
type MessageBlock =
  | { type: 'text'; content: string }
  | { type: 'code'; content: string; language: string; closed: boolean }

hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)

const route = useRoute(), router = useRouter()
const loginUserStore = useLoginUserStore()
const id = String(route.params.id)
const app = ref<API.AppVO>({})
const appLoaded = ref(false)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const generating = ref(false), deploying = ref(false), previewReady = ref(false)
const followingOutput = ref(true)
const previewKey = ref(0)
const versionCoverRefreshKey = ref(0)
const messageList = ref<HTMLElement>()
const workspace = ref<HTMLElement>()
const conversationWidth = ref(390), versionWidth = ref(116)
const resizingPane = ref<ResizePane>()
let eventSource: EventSource | undefined
let activeAssistantIndex: number | undefined
let typewriterTimer: number | undefined
let typewriterQueue = ''
let streamEnded = false
let coverSyncId = 0
const previewUrl = computed(() => `${APP_PREVIEW_BASE_URL}/${app.value.codeGenType || 'html'}_${id}/`)
const versionCoverUrl = computed(() => {
  if (!app.value.cover) return ''
  if (!versionCoverRefreshKey.value) return app.value.cover
  const separator = app.value.cover.includes('?') ? '&' : '?'
  return `${app.value.cover}${separator}t=${versionCoverRefreshKey.value}`
})
const gridTemplateColumns = computed(() => `${conversationWidth.value}px 7px minmax(${MIN_PREVIEW_WIDTH}px, 1fr) 7px ${versionWidth.value}px`)
const canChat = computed(() => Boolean(appLoaded.value && app.value.userId && loginUserStore.loginUser.id && String(app.value.userId) === String(loginUserStore.loginUser.id)))
const chatPermissionTip = computed(() => appLoaded.value && !canChat.value ? '无法在别人的作品下对话哦~' : '')
const loginUserInitial = computed(() => (loginUserStore.loginUser.userName || loginUserStore.loginUser.userAccount || '我').slice(0, 1))

type ResizePane = 'conversation' | 'versions'
const MIN_CONVERSATION_WIDTH = 280
const MAX_CONVERSATION_WIDTH = 680
const MIN_PREVIEW_WIDTH = 420
const MIN_VERSION_WIDTH = 92
const MAX_VERSION_WIDTH = 280
const RESIZE_STEP = 16
const HANDLE_WIDTH = 14
const TYPEWRITER_INTERVAL = 18
const AUTO_SCROLL_THRESHOLD = 24
const COVER_SYNC_RETRY_COUNT = 5
const COVER_SYNC_RETRY_DELAY = 800
const SUMMARY_OUTPUT_INSTRUCTION = '生成完成后，请在所有代码块结束后追加“文件结构与说明”和“功能说明”两个总结章节。“文件结构与说明”需要按文件逐项说明，“功能说明”需要使用有序列表概括本次实际实现的功能。总结必须根据本次生成内容动态编写，不要省略，不要使用固定文案。'
let resizeStartX = 0
let resizeStartWidth = 0

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max))
const availablePaneWidth = () => (workspace.value?.clientWidth || 0) - HANDLE_WIDTH - MIN_PREVIEW_WIDTH
const resizeConversation = (width: number) => {
  conversationWidth.value = clamp(width, MIN_CONVERSATION_WIDTH, Math.min(MAX_CONVERSATION_WIDTH, availablePaneWidth() - versionWidth.value))
}
const resizeVersions = (width: number) => {
  versionWidth.value = clamp(width, MIN_VERSION_WIDTH, Math.min(MAX_VERSION_WIDTH, availablePaneWidth() - conversationWidth.value))
}
const resizeWithKeyboard = (pane: ResizePane, delta: number) => {
  if (pane === 'conversation') resizeConversation(conversationWidth.value + delta)
  else resizeVersions(versionWidth.value + delta)
}
const resizeOnPointerMove = (event: PointerEvent) => {
  if (resizingPane.value === 'conversation') resizeConversation(resizeStartWidth + event.clientX - resizeStartX)
  if (resizingPane.value === 'versions') resizeVersions(resizeStartWidth - event.clientX + resizeStartX)
}
const stopResize = () => {
  resizingPane.value = undefined
  window.removeEventListener('pointermove', resizeOnPointerMove)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
}
const startResize = (pane: ResizePane, event: PointerEvent) => {
  event.preventDefault()
  resizingPane.value = pane
  resizeStartX = event.clientX
  resizeStartWidth = pane === 'conversation' ? conversationWidth.value : versionWidth.value
  window.addEventListener('pointermove', resizeOnPointerMove)
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

const isMessageListNearBottom = () => {
  const list = messageList.value
  return !list || list.scrollHeight - list.scrollTop - list.clientHeight <= AUTO_SCROLL_THRESHOLD
}
const handleMessageListScroll = () => {
  followingOutput.value = isMessageListNearBottom()
}
const scrollToBottom = (force = false) => nextTick(() => {
  if (!force && !followingOutput.value) return
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
})
const resumeFollowingOutput = () => {
  followingOutput.value = true
  scrollToBottom(true)
}
const loadApp = async () => {
  const res = await getAppVoById({ id })
  if (!res.data.data) return message.error('获取应用失败：' + res.data.message)
  app.value = res.data.data
  appLoaded.value = true
  previewReady.value = Boolean(app.value.codeGenType)
  if (route.query.auto === '1' && app.value.initPrompt) {
    await router.replace({ path: route.path })
    send(app.value.initPrompt)
  }
}
const sleep = (duration: number) => new Promise((resolve) => window.setTimeout(resolve, duration))
const syncGeneratedAppInfo = async () => {
  const currentSyncId = ++coverSyncId
  const previousCover = app.value.cover
  for (let attempt = 0; attempt <= COVER_SYNC_RETRY_COUNT; attempt++) {
    if (attempt) await sleep(COVER_SYNC_RETRY_DELAY)
    if (currentSyncId !== coverSyncId) return
    try {
      const res = await getAppVoById({ id })
      if (currentSyncId !== coverSyncId) return
      if (res.data.code !== 0 || !res.data.data) continue
      app.value = { ...app.value, ...res.data.data }
      if (res.data.data.cover) versionCoverRefreshKey.value = Date.now()
      if (res.data.data.cover && res.data.data.cover !== previousCover) return
    } catch {
      // 生成结果已经可预览，封面同步失败时保持当前封面，下一次进入页面仍会加载最新数据。
    }
  }
}
const send = (content: string) => {
  if (!content.trim() || generating.value || !canChat.value) return
  const userMessage = content.trim()
  const aiMessage = `${userMessage}\n\n${SUMMARY_OUTPUT_INSTRUCTION}`
  eventSource?.close()
  resetTypewriter()
  messages.value.push({ role: 'user', content: userMessage })
  activeAssistantIndex = messages.value.push({ role: 'assistant', content: '', pending: true }) - 1
  input.value = ''
  generating.value = true
  previewReady.value = false
  const source = new EventSource(`http://localhost:8123/api/app/chat/gen/code?appId=${id}&message=${encodeURIComponent(aiMessage)}`, { withCredentials: true })
  eventSource = source
  source.onmessage = (event) => {
    const assistant = getActiveAssistant()
    const chunk = normalizeChunk(event.data)
    if (!assistant || !chunk) return
    assistant.pending = false
    typewriterQueue += chunk
    if (typewriterTimer === undefined) flushTypewriterQueue()
  }
  source.addEventListener('done', () => finishGeneration(source))
  source.onerror = () => finishGeneration(source)
  resumeFollowingOutput()
}
const normalizeChunk = (chunk: string) => {
  try {
    const parsed: unknown = JSON.parse(chunk)
    if (typeof parsed === 'string') return parsed
    if (typeof parsed === 'object' && parsed !== null && 'd' in parsed && typeof parsed.d === 'string') return parsed.d
    return chunk
  } catch {
    return chunk
  }
}
const parseMessageBlocks = (content: string): MessageBlock[] => {
  const blocks: MessageBlock[] = []
  const openingFence = /```([^\n`]*)\n?/g
  let cursor = 0
  let match: RegExpExecArray | null
  while ((match = openingFence.exec(content)) !== null) {
    if (match.index > cursor) blocks.push({ type: 'text', content: content.slice(cursor, match.index) })
    const codeStart = match.index + match[0].length
    const closingFence = content.indexOf('```', codeStart)
    if (closingFence === -1) {
      blocks.push({ type: 'code', language: match[1].trim(), content: content.slice(codeStart), closed: false })
      cursor = content.length
      break
    }
    blocks.push({ type: 'code', language: match[1].trim(), content: content.slice(codeStart, closingFence), closed: true })
    cursor = closingFence + 3
    openingFence.lastIndex = cursor
  }
  if (cursor < content.length) blocks.push({ type: 'text', content: content.slice(cursor) })
  return blocks
}
const normalizeLanguage = (language: string) => {
  const normalized = language.toLowerCase()
  return normalized === 'htm' ? 'html' : normalized
}
const highlightCode = (code: string, language: string) => {
  const normalizedLanguage = normalizeLanguage(language)
  return normalizedLanguage && hljs.getLanguage(normalizedLanguage)
    ? hljs.highlight(code, { language: normalizedLanguage }).value
    : hljs.highlightAuto(code).value
}
const getActiveAssistant = () => {
  if (activeAssistantIndex === undefined) return
  const assistant = messages.value[activeAssistantIndex]
  return assistant?.role === 'assistant' ? assistant : undefined
}
const clearTypewriterTimer = () => {
  if (typewriterTimer === undefined) return
  window.clearTimeout(typewriterTimer)
  typewriterTimer = undefined
}
const resetTypewriter = () => {
  clearTypewriterTimer()
  activeAssistantIndex = undefined
  typewriterQueue = ''
  streamEnded = false
}
const getTypewriterBatchSize = () => {
  if (typewriterQueue.length > 2400) return 32
  if (typewriterQueue.length > 1200) return 16
  if (typewriterQueue.length > 480) return 8
  if (typewriterQueue.length > 160) return 4
  if (typewriterQueue.length > 60) return 2
  return 1
}
const flushTypewriterQueue = () => {
  typewriterTimer = undefined
  const assistant = getActiveAssistant()
  if (!assistant) return
  const batchSize = getTypewriterBatchSize()
  assistant.content += typewriterQueue.slice(0, batchSize)
  typewriterQueue = typewriterQueue.slice(batchSize)
  scrollToBottom()
  if (typewriterQueue) typewriterTimer = window.setTimeout(flushTypewriterQueue, TYPEWRITER_INTERVAL)
  else if (streamEnded) completeGeneration()
}
const finishGeneration = (source: EventSource) => {
  if (eventSource !== source) return
  source.close()
  eventSource = undefined
  streamEnded = true
  if (!typewriterQueue) completeGeneration()
}
const completeGeneration = () => {
  if (!generating.value) return
  eventSource?.close()
  eventSource = undefined
  clearTypewriterTimer()
  generating.value = false
  const assistant = getActiveAssistant()
  if (assistant) {
    assistant.pending = false
  }
  activeAssistantIndex = undefined
  typewriterQueue = ''
  streamEnded = false
  app.value.codeGenType ||= 'html'
  previewReady.value = true
  refreshPreview()
  void syncGeneratedAppInfo()
  scrollToBottom()
}
const sendMessage = () => send(input.value)
const handleEnter = (event: KeyboardEvent) => { if (!event.shiftKey) { event.preventDefault(); sendMessage() } }
const refreshPreview = () => previewKey.value++
const openPreview = () => window.open(previewUrl.value, '_blank')
const downloadCode = () => { window.open(previewUrl.value, '_blank'); message.info('已打开生成资源，可在新页面中查看或保存代码') }
const deploy = async () => {
  deploying.value = true
  try {
    const res = await deployApp({ appId: id })
    if (res.data.code === 0 && res.data.data) Modal.success({
      title: '部署成功',
      content: `访问地址：${res.data.data}`,
      okText: '打开网站',
      onOk: (close) => {
        window.open(res.data.data, '_blank')
        close()
      },
    })
    else message.error('部署失败：' + res.data.message)
  } finally { deploying.value = false }
}
onMounted(loadApp)
onBeforeUnmount(() => {
  coverSyncId++
  eventSource?.close()
  eventSource = undefined
  resetTypewriter()
  stopResize()
})
</script>

<style scoped>
.chat-page { min-width: 1050px; min-height: 100vh; color: #172326; background: #f3f6f6; }
.studio-header { display: flex; height: 62px; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid #e8ecec; background: #fff; }
.app-title { display: flex; min-width: 260px; align-items: center; gap: 10px; color: #1e292c; }.app-title img { width: 38px; height: 38px; }.app-title strong, .app-title span { display: block; }.app-title span { color: #a1abad; font-size: 11px; letter-spacing: 1px; }
.header-center { color: #93a0a1; font-size: 12px; }.status-dot { display: inline-block; width: 8px; height: 8px; margin-right: 7px; border-radius: 50%; background: #51c5aa; }.status-dot.active { background: #f9b648; box-shadow: 0 0 0 4px rgba(249,182,72,.18); }
.workspace { display: grid; height: calc(100vh - 62px); background: #e6ebeb; }
.resize-handle { position: relative; width: 7px; padding: 0; border: 0; background: #e6ebeb; cursor: col-resize; touch-action: none; }.resize-handle::after { position: absolute; top: 50%; left: 2px; width: 3px; height: 42px; border-radius: 4px; background: #bcc9c9; content: ""; opacity: 0; transform: translateY(-50%); transition: opacity .2s, background .2s; }.resize-handle:hover::after, .resize-handle:focus-visible::after, .resizing .resize-handle::after { background: #24aaa1; opacity: 1; }.resize-handle:focus-visible { outline: 2px solid #24aaa1; outline-offset: -2px; }.resizing { cursor: col-resize; user-select: none; }.resizing iframe { pointer-events: none; }
.conversation, .preview-pane, .version-bar { background: #fff; }.conversation { position: relative; display: flex; min-height: 0; flex-direction: column; }.message-list { flex: 1; overflow-y: auto; padding: 18px 15px; }
.welcome { display: flex; gap: 12px; margin-bottom: 20px; padding: 14px; border-radius: 16px; background: #f1fbf9; }.welcome h2 { margin: 0 0 5px; font-size: 16px; }.welcome p { margin: 0; color: #718385; font-size: 12px; line-height: 1.7; }.ai-mark img, .avatar img { width: 30px; height: 30px; }
.message-row { display: flex; align-items: flex-start; gap: 8px; margin: 14px 0; }.message-row.user { justify-content: flex-end; }.avatar { flex: 0 0 auto; }.user-avatar { color: #fff; background: #1d9bf0; font-weight: 700; }.bubble { max-width: 85%; padding: 11px 13px; border-radius: 14px; background: #f4f6f6; }.assistant .bubble { min-width: 0; }.user .bubble { color: #fff; background: #1aa79e; }.message-role { margin-bottom: 5px; color: inherit; font-size: 11px; font-weight: 700; opacity: .72; }.message-content { font-size: 13px; line-height: 1.75; word-break: break-word; }.message-text { white-space: pre-wrap; }
.code-block { margin: 9px 0; overflow: hidden; border: 1px solid #dde5e5; border-radius: 8px; background: #fff; }.code-header { display: flex; align-items: center; justify-content: space-between; padding: 5px 9px; border-bottom: 1px solid #e6ebeb; color: #7b898b; background: #f8fafa; font-size: 11px; text-transform: lowercase; }.streaming-label { color: #199e96; }.code-block pre { max-height: 340px; margin: 0; overflow: auto; padding: 10px; background: #fff; }.code-block code { font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 11px; line-height: 1.65; white-space: pre; }
.typing i { display: inline-block; width: 5px; height: 5px; margin: 9px 3px 0 0; border-radius: 50%; background: #74c7bd; animation: pulse 1s infinite alternate; }.typing i:nth-child(2) { animation-delay: .2s; }.typing i:nth-child(3) { animation-delay: .4s; } @keyframes pulse { to { opacity: .25; transform: translateY(-3px); } }
.scroll-to-latest { position: absolute; bottom: 128px; left: 50%; z-index: 1; padding: 7px 13px; border: 1px solid #bfe2de; border-radius: 16px; color: #168f88; background: rgba(255,255,255,.96); box-shadow: 0 4px 14px rgba(29,90,94,.12); cursor: pointer; font-size: 12px; transform: translateX(-50%); }.scroll-to-latest:hover { border-color: #24aaa1; background: #f4fbfa; }
.composer-wrap { padding: 12px; }.composer { padding: 9px 10px; border: 1px solid #e1e8e8; border-radius: 17px; background: #fff; box-shadow: 0 7px 24px rgba(29,90,94,.09); }.composer textarea { resize: none; }.composer-input.disabled { cursor: not-allowed; }.composer-footer, .preview-toolbar { display: flex; align-items: center; justify-content: space-between; }.composer-footer span { color: #a0abad; font-size: 12px; }
.preview-pane { display: flex; min-width: 0; flex-direction: column; }.preview-toolbar { height: 46px; padding: 0 12px; border-bottom: 1px solid #ebeeee; color: #657375; font-size: 13px; }.url { overflow: hidden; max-width: 55%; color: #a1adae; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.preview-stage { flex: 1; padding: 12px; background: #f5f7f7; }.preview-stage iframe { width: 100%; height: 100%; border: 1px solid #e7eaea; border-radius: 9px; background: #fff; }.empty-preview { display: flex; height: 100%; flex-direction: column; align-items: center; justify-content: center; text-align: center; }.preview-illustration { display: grid; width: 76px; height: 76px; place-items: center; border-radius: 24px; color: #18a69c; background: #e7f7f5; font-size: 28px; }.empty-preview h2 { margin: 18px 0 5px; }.empty-preview p { max-width: 360px; color: #98a5a6; line-height: 1.7; }
.version-bar { padding: 15px 10px; }.version-bar h3 { margin: 0 0 12px; font-size: 15px; }.version-card { width: 100%; padding: 7px; border: 1px solid #18aaa0; border-radius: 9px; color: #138f88; background: #f4fbfa; text-align: left; cursor: pointer; }.version-thumb { display: grid; height: 55px; margin-top: 6px; place-items: center; overflow: hidden; border-radius: 4px; background: #daf1ef; }.version-thumb img { width: 100%; height: 100%; object-fit: cover; }.version-thumb .fallback-logo { width: 35px; height: 35px; }
</style>
