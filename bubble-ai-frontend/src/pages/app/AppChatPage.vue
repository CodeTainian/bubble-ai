<template>
  <div class="chat-page" :class="{ 'preview-fullscreen': previewFullscreen }">
    <header class="studio-header">
      <RouterLink to="/" class="app-title">
        <img src="@/assets/logo.svg" alt="Bubble AI" />
        <div><strong>{{ app.appName || 'AI 应用工作台' }}</strong><span>Bubble AI Studio</span></div>
      </RouterLink>
      <div class="header-center" :style="{ left: previewToolbarLeft }">
        <div class="preview-actions">
          <a-tooltip :title="previewFullscreen ? '退出全屏预览' : '全屏预览'">
            <a-button
              class="header-icon-button"
              :disabled="!previewReady"
              @click="togglePreviewFullscreen"
            >
              <FullscreenExitOutlined v-if="previewFullscreen" />
              <FullscreenOutlined v-else />
            </a-button>
          </a-tooltip>
          <a-tooltip title="刷新预览">
            <a-button class="header-icon-button" :disabled="!previewReady" @click="refreshPreview">
              <ReloadOutlined />
            </a-button>
          </a-tooltip>
        </div>
      </div>
      <a-space>
        <a-tooltip title="打开生成页面"><a-button class="header-icon-button" :disabled="!previewReady" @click="openPreview"><CodeOutlined /></a-button></a-tooltip>
        <a-tooltip title="下载 / 打开代码资源"><a-button class="header-icon-button" :disabled="!previewReady" @click="downloadCode"><DownloadOutlined /></a-button></a-tooltip>
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
          <div v-if="historyLoading && !historyInitialized" class="history-loading">正在加载历史对话...</div>
          <div v-else-if="historyInitialized && hasMoreHistory" class="history-load-more">
            <a-button size="small" type="link" :loading="historyLoadingMore" @click="loadMoreHistory">
              <UpOutlined /> 加载更多
            </a-button>
          </div>
          <article v-for="(item, index) in messages" :key="item.id || index" class="message-row" :class="item.role">
            <div v-if="item.role === 'assistant'" class="avatar"><img src="@/assets/logo.svg" alt="" /></div>
            <div class="bubble">
              <div class="message-role">{{ item.role === 'user' ? '你' : 'Bubble AI' }}</div>
              <div v-if="item.role === 'assistant'" class="message-content">
                <template v-if="hasAssistantOutput(item)">
                  <template v-for="(block, blockIndex) in renderAssistantBlocks(item)" :key="blockIndex">
                    <div v-if="block.type === 'text'" class="message-text">{{ block.content }}</div>
                    <div v-else-if="block.type === 'summary'" class="process-summary">{{ block.content }}</div>
                    <section v-else-if="block.type === 'code'" class="code-block">
                      <div class="code-header">
                        <span>{{ block.language || 'code' }}</span>
                        <span v-if="!block.closed" class="streaming-label">实时生成中</span>
                      </div>
                      <pre><code v-html="highlightCode(block.content, block.language)"></code></pre>
                    </section>
                    <div v-else-if="block.type === 'step'" class="process-step">{{ block.title }}</div>
                    <div v-else-if="block.type === 'dependency'" class="process-row dependency-row">
                      <CodeSandboxOutlined />
                      <span class="process-name">{{ block.name }}</span>
                      <span class="process-status" :class="block.status">{{ block.statusText }}</span>
                    </div>
                    <div v-else-if="block.type === 'file'" class="process-row file-row">
                      <EditOutlined />
                      <span class="process-name">{{ block.fileName }}</span>
                      <span class="process-path">{{ block.path }}</span>
                    </div>
                    <div v-else-if="block.type === 'tool'" class="process-row tool-row">
                      <CodeOutlined />
                      <span class="process-name">{{ block.label }}</span>
                      <span v-if="block.detail" class="process-path">{{ block.detail }}</span>
                    </div>
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
        <div class="version-card active">
          <span>v1</span>
          <div class="version-thumb">
            <AppCover :cover="app.cover" :refresh-key="versionCoverRefreshKey" variant="thumb" />
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { ArrowUpOutlined, CodeOutlined, CodeSandboxOutlined, DownloadOutlined, EditOutlined, FullscreenExitOutlined, FullscreenOutlined, MessageOutlined, ReloadOutlined, RocketOutlined, UpOutlined } from '@ant-design/icons-vue'
import hljs from 'highlight.js/lib/core'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import xml from 'highlight.js/lib/languages/xml'
import 'highlight.js/styles/github.css'
import { deployApp, getAppVoById } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import AppCover from '@/components/AppCover.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { APP_PREVIEW_BASE_URL } from '@/config/env'

type ChatMessage = { id?: string; role: 'user' | 'assistant'; content: string; pending?: boolean; createTime?: string; blocks?: AssistantBlock[] }
type AssistantBlock =
  | { type: 'text'; content: string }
  | { type: 'code'; content: string; language: string; closed: boolean }
  | { type: 'step'; title: string }
  | { type: 'summary'; content: string }
  | { type: 'dependency'; name: string; status: 'running' | 'success' | 'error'; statusText: string }
  | { type: 'file'; fileName: string; path: string; description?: string }
  | { type: 'tool'; label: string; detail?: string }
type CodeBlock = Extract<AssistantBlock, { type: 'code' }>
type TypewriterJob =
  | { type: 'text'; content: string }
  | { type: 'code'; content: string; block: CodeBlock }
  | { type: 'blocks'; blocks: AssistantBlock[] }

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
const historyLoading = ref(false), historyLoadingMore = ref(false), historyInitialized = ref(false), hasMoreHistory = ref(false)
const previewFullscreen = ref(false)
const followingOutput = ref(true)
const previewKey = ref(0)
const versionCoverRefreshKey = ref(0)
const messageList = ref<HTMLElement>()
const workspace = ref<HTMLElement>()
const conversationWidth = ref(520), versionWidth = ref(132)
const resizingPane = ref<ResizePane>()
let eventSource: EventSource | undefined
let activeAssistantIndex: number | undefined
let typewriterTimer: number | undefined
let typewriterQueue: TypewriterJob[] = []
let streamEnded = false
let coverSyncId = 0
const REACT_PROJECT_CODE_DIR_PREFIX = 'react_project_file'
const buildCodeOutputDirName = (codeGenType: string | undefined, appId: string) => {
  const type = codeGenType || 'react_project'
  const prefix = type === 'react_project' ? REACT_PROJECT_CODE_DIR_PREFIX : type
  return `${prefix}_${appId}`
}
const previewUrl = computed(() => `${APP_PREVIEW_BASE_URL}/${buildCodeOutputDirName(app.value.codeGenType, id)}/`)
const gridTemplateColumns = computed(() => `${conversationWidth.value}px 7px minmax(${MIN_PREVIEW_WIDTH}px, 1fr) 7px ${versionWidth.value}px`)
const canChat = computed(() => Boolean(appLoaded.value && app.value.userId && loginUserStore.loginUser.id && String(app.value.userId) === String(loginUserStore.loginUser.id)))
const chatPermissionTip = computed(() => appLoaded.value && !canChat.value ? '无法在别人的作品下对话哦~' : '')
const loginUserInitial = computed(() => (loginUserStore.loginUser.userName || loginUserStore.loginUser.userAccount || '我').slice(0, 1))

type ResizePane = 'conversation' | 'versions'
const MIN_CONVERSATION_WIDTH = 360
const MAX_CONVERSATION_WIDTH = 660
const MIN_PREVIEW_WIDTH = 540
const MIN_VERSION_WIDTH = 116
const MAX_VERSION_WIDTH = 280
const RESIZE_STEP = 16
const HANDLE_WIDTH = 14
const PREVIEW_TOOLBAR_SAFE_LEFT = 404
const PREVIEW_TOOLBAR_OFFSET = 17
const TYPEWRITER_INTERVAL = 18
const AUTO_SCROLL_THRESHOLD = 24
const HISTORY_PAGE_SIZE = 10
const COVER_SYNC_RETRY_COUNT = 5
const COVER_SYNC_RETRY_DELAY = 800
const CHAT_STREAM_EVENT_TYPES: API.ChatStreamMessageType[] = [
  'ai_response',
  'thinking',
  'step',
  'tool_call_start',
  'tool_call_result',
  'file_write',
  'error',
  'done',
]
const FILE_TOKEN_PATTERN = /(?:src\/[\w@./-]+\.(?:jsx|tsx|js|ts|css|json|html)|[\w@.-]+\.(?:json|html|jsx|tsx|js|ts|css))/g
const DEPENDENCY_STATUS_PATTERN = /(@?[\w.-]+(?:\/[\w.-]+)?)\s*(安装成功|安装失败|安装中)/g
const INSTALL_TOOL_PATTERN = /(install|package|dependency|npm|pnpm|yarn|依赖|安装)/i
let resizeStartX = 0
let resizeStartWidth = 0
let historyCursor: string | undefined
let loadedHistoryTotal = 0
const loadedHistoryKeys = new Set<string>()

const previewToolbarLeft = computed(() => {
  const previewLeft = previewFullscreen.value
    ? PREVIEW_TOOLBAR_SAFE_LEFT
    : conversationWidth.value + PREVIEW_TOOLBAR_OFFSET
  return `${Math.max(PREVIEW_TOOLBAR_SAFE_LEFT, previewLeft)}px`
})
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
const getHistoryKey = (history: API.ChatHistory) =>
  history.id ? `id:${history.id}` : `${history.createTime || ''}:${history.messageType || ''}:${history.message || ''}`
const getHistoryTime = (history?: API.ChatHistory) => {
  const value = history?.createTime ? new Date(history.createTime).getTime() : 0
  return Number.isNaN(value) ? 0 : value
}
const sortHistoryAsc = (records: API.ChatHistory[]) => [...records].sort((a, b) => {
  const timeDiff = getHistoryTime(a) - getHistoryTime(b)
  if (timeDiff !== 0) return timeDiff
  return String(a.id || '').localeCompare(String(b.id || ''))
})
const resolveHistoryRole = (messageType?: string): ChatMessage['role'] => {
  const type = (messageType || '').trim().toLowerCase()
  if (type.includes('user') || type.includes('human') || type.includes('request') || type.includes('用户')) return 'user'
  if (type === 'ai' || type.includes('assistant') || type.includes('model') || type.includes('answer') || type.includes('response') || type.includes('助手')) return 'assistant'
  return 'assistant'
}
const toChatMessage = (history: API.ChatHistory): ChatMessage => ({
  id: history.id,
  role: resolveHistoryRole(history.messageType),
  content: history.message || '',
  createTime: history.createTime,
})
const getHistoryTotal = (page?: API.PageChatHistory) => Number(page?.totalRow ?? 0)
const mergeHistoryMessages = (records: API.ChatHistory[], mode: 'replace' | 'prepend') => {
  const newMessages = sortHistoryAsc(records).reduce<ChatMessage[]>((result, history) => {
    const key = getHistoryKey(history)
    if (loadedHistoryKeys.has(key)) return result
    loadedHistoryKeys.add(key)
    result.push(toChatMessage(history))
    return result
  }, [])
  messages.value = mode === 'replace' ? newMessages : [...newMessages, ...messages.value]
  return newMessages.length
}
const updateHistoryCursor = (records: API.ChatHistory[]) => {
  const oldest = sortHistoryAsc(records)[0]
  if (oldest?.createTime) historyCursor = oldest.createTime
}
const updateHistoryMoreState = (page: API.PageChatHistory | undefined, records: API.ChatHistory[], addedCount: number) => {
  const total = getHistoryTotal(page)
  loadedHistoryTotal = total || loadedHistoryTotal
  if (total > 0) {
    hasMoreHistory.value = loadedHistoryKeys.size < total
    return
  }
  hasMoreHistory.value = records.length >= HISTORY_PAGE_SIZE && addedCount > 0
}
const syncPreviewReadyWithHistory = () => {
  previewReady.value = Boolean(app.value.codeGenType || loadedHistoryTotal >= 2 || loadedHistoryKeys.size >= 2)
}
const loadInitialHistory = async () => {
  historyLoading.value = true
  try {
    const res = await listAppChatHistory({ appId: id, pageSize: HISTORY_PAGE_SIZE })
    if (res.data.code !== 0) {
      message.error('加载历史对话失败：' + res.data.message)
      historyInitialized.value = true
      return false
    }
    const page = res.data.data
    const records = page?.records ?? []
    loadedHistoryKeys.clear()
    historyCursor = undefined
    loadedHistoryTotal = getHistoryTotal(page)
    const addedCount = mergeHistoryMessages(records, 'replace')
    updateHistoryCursor(records)
    updateHistoryMoreState(page, records, addedCount)
    syncPreviewReadyWithHistory()
    historyInitialized.value = true
    await nextTick()
    resumeFollowingOutput()
    return true
  } catch {
    message.error('加载历史对话失败')
    historyInitialized.value = true
    return false
  } finally {
    historyLoading.value = false
  }
}
const loadMoreHistory = async () => {
  if (!hasMoreHistory.value || historyLoadingMore.value) return
  historyLoadingMore.value = true
  const list = messageList.value
  const previousHeight = list?.scrollHeight ?? 0
  const previousTop = list?.scrollTop ?? 0
  const previousCursor = historyCursor
  try {
    const params: API.listAppChatHistoryParams = { appId: id, pageSize: HISTORY_PAGE_SIZE }
    if (historyCursor) params.lastCreateTime = historyCursor
    const res = await listAppChatHistory(params)
    if (res.data.code !== 0) {
      message.error('加载更多失败：' + res.data.message)
      return
    }
    const page = res.data.data
    const records = page?.records ?? []
    const addedCount = mergeHistoryMessages(records, 'prepend')
    updateHistoryCursor(records)
    updateHistoryMoreState(page, records, addedCount)
    if (!records.length || (!addedCount && historyCursor === previousCursor)) hasMoreHistory.value = false
    await nextTick()
    const currentList = messageList.value
    if (currentList) currentList.scrollTop = currentList.scrollHeight - previousHeight + previousTop
  } catch {
    message.error('加载更多失败')
  } finally {
    historyLoadingMore.value = false
  }
}
const loadApp = async () => {
  const res = await getAppVoById({ id })
  if (!res.data.data) return message.error('获取应用失败：' + res.data.message)
  app.value = res.data.data
  appLoaded.value = true
  syncPreviewReadyWithHistory()
  const historyLoaded = await loadInitialHistory()
  if (route.query.auto === '1') {
    await router.replace({ path: route.path })
  }
  if (historyLoaded && canChat.value && loadedHistoryTotal === 0 && loadedHistoryKeys.size === 0 && app.value.initPrompt) {
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
  const aiMessage = `${userMessage}`
  eventSource?.close()
  resetTypewriter()
  messages.value.push({ role: 'user', content: userMessage })
  activeAssistantIndex = messages.value.push({ role: 'assistant', content: '', pending: true, blocks: [] }) - 1
  input.value = ''
  generating.value = true
  previewReady.value = false
  const source = new EventSource(`http://localhost:8123/api/app/chat/gen/code?appId=${id}&message=${encodeURIComponent(aiMessage)}`, { withCredentials: true })
  eventSource = source
  registerChatStreamListeners(source)
  resumeFollowingOutput()
}

const registerChatStreamListeners = (source: EventSource) => {
  source.onmessage = (event) => handleStreamEvent(event, source)
  CHAT_STREAM_EVENT_TYPES.forEach((type) => {
    source.addEventListener(type, (event) => handleStreamEvent(event as MessageEvent<string>, source))
  })
  source.onerror = () => handleStreamConnectionError(source)
}
const handleStreamEvent = (event: MessageEvent<string>, source: EventSource) => {
  if (eventSource !== source) return
  const streamMessage = parseChatStreamMessage(event, source)
  if (!streamMessage) return
  handleChatStreamMessage(streamMessage, source)
}
const parseChatStreamMessage = (event: MessageEvent<string>, source: EventSource) => {
  if (!event.data) {
    failGeneration(source, '生成消息为空，请稍后重试')
    return
  }
  try {
    const parsed = JSON.parse(event.data) as Partial<API.ChatStreamMessage>
    if (!parsed || typeof parsed !== 'object' || !isChatStreamMessageType(parsed.type)) {
      failGeneration(source, '生成消息格式异常，请稍后重试')
      return
    }
    return parsed as API.ChatStreamMessage
  } catch (error) {
    console.error('[chat-stream] parse error', error, event.data)
    failGeneration(source, '解析生成消息失败，请稍后重试')
  }
}
const isChatStreamMessageType = (type: unknown): type is API.ChatStreamMessageType =>
  typeof type === 'string' && CHAT_STREAM_EVENT_TYPES.includes(type as API.ChatStreamMessageType)
const handleChatStreamMessage = (streamMessage: API.ChatStreamMessage, source: EventSource) => {
  switch (streamMessage.type) {
    case 'ai_response':
      appendAssistantContent(streamMessage.content)
      break
    case 'file_write':
    case 'tool_call_result':
      appendStreamProcessBlock(streamMessage)
      break
    case 'thinking':
    case 'step':
    case 'tool_call_start':
      console.log(`[chat-stream] ${streamMessage.type}`, streamMessage)
      break
    case 'error':
      console.error('[chat-stream] error', streamMessage)
      failGeneration(source, streamMessage.content || 'AI 生成失败，请稍后重试')
      break
    case 'done':
      finishGeneration(source)
      break
  }
}
const appendAssistantContent = (content?: string) => {
  const assistant = getActiveAssistant()
  if (!assistant || !content) return
  assistant.pending = false
  queueTypewriterText(content)
}
const appendStreamProcessBlock = (streamMessage: API.ChatStreamMessage) => {
  const assistant = getActiveAssistant()
  if (!assistant) return
  if (streamMessage.type === 'file_write') {
    if (appendFileWriteBlock(streamMessage)) {
      assistant.pending = false
    }
    return
  }
  const newBlocks = createStreamProcessBlocks(streamMessage)
  if (!newBlocks.length) {
    console.log(`[chat-stream] ${streamMessage.type}`, streamMessage)
    return
  }
  assistant.pending = false
  queueTypewriterBlocks(newBlocks)
}
const appendFileWriteBlock = (streamMessage: API.ChatStreamMessage) => {
  const filePath = normalizeRelativePath(getMetadataString(streamMessage, 'path'))
  const language = getMetadataString(streamMessage, 'language') || guessLanguageByPath(filePath)
  const code = streamMessage.content || ''
  const codeBlock: CodeBlock | undefined = code ? { type: 'code', content: '', language, closed: false } : undefined
  const newBlocks = createFileWriteBlocks(streamMessage, codeBlock)
  if (!newBlocks.length) return false
  queueTypewriterBlocks(newBlocks)
  if (codeBlock) queueTypewriterCode(codeBlock, code)
  return true
}
const handleStreamConnectionError = (source: EventSource) => {
  if (eventSource !== source) return
  failGeneration(source, '生成连接异常，请稍后重试')
}
const hasAssistantOutput = (item: ChatMessage) => Boolean(item.content || item.blocks?.length)
const renderAssistantBlocks = (item: ChatMessage): AssistantBlock[] => {
  if (item.blocks?.length) {
    return item.blocks.flatMap((block) => block.type === 'text' ? parseAssistantContent(block.content) : [block])
  }
  return parseAssistantContent(item.content)
}
const parseAssistantContent = (content: string): AssistantBlock[] => {
  const normalizedNewlines = content.replace(/\\n/g, '\n')
  const blocks: AssistantBlock[] = []
  let syntheticStep = 0
  const pushParsedBlock = (block: AssistantBlock) => {
    if (block.type === 'step') {
      const step = getStepNumberFromTitle(block.title)
      if (step) syntheticStep = Math.max(syntheticStep, step)
      blocks.push(block)
      return
    }
    if (block.type === 'file') {
      const previousBlock = blocks[blocks.length - 1]
      if (previousBlock?.type !== 'step') {
        syntheticStep += 1
        blocks.push({ type: 'step', title: `STEP ${syntheticStep}: 创建${block.fileName}` })
      }
      blocks.push(block)
      return
    }
    blocks.push(block)
  }
  parseMessageBlocks(normalizedNewlines).forEach((block) => {
    if (block.type !== 'text') {
      pushParsedBlock(block)
      return
    }
    parseProcessTextBlocks(normalizeProcessText(block.content)).forEach(pushParsedBlock)
  })
  return blocks
}
const normalizeProcessText = (content: string) =>
  content
    .replace(/#{1,6}\s*/g, '')
    .replace(/(STEP\s*\d+\s*[:：])/gi, '\n$1')
    .replace(/[-—]\s*(?=创建[^：:\n]{0,36}[：:])/g, '\n')
    .replace(/(开始创建[：:])/g, '\n$1')
    .trim()
const parseMessageBlocks = (content: string): AssistantBlock[] => {
  const blocks: AssistantBlock[] = []
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
const parseProcessTextBlocks = (content: string): AssistantBlock[] => {
  const blocks: AssistantBlock[] = []
  const lines = content.split('\n').map((line) => line.trim()).filter(Boolean)
  lines.forEach((line) => {
    const stepBlock = parseStepLine(line)
    if (stepBlock) {
      blocks.push(stepBlock)
      return
    }
    const dependencyBlocks = parseDependencyLine(line)
    if (dependencyBlocks.length) {
      blocks.push(...dependencyBlocks)
      return
    }
    const fileBlock = parseFileLine(line)
    if (fileBlock) {
      blocks.push(fileBlock)
      return
    }
    const planBlocks = parsePlanLine(line)
    if (planBlocks.length) {
      blocks.push(...planBlocks)
      return
    }
    pushTextBlock(blocks, cleanupMessageLine(line))
  })
  return blocks
}
const parseStepLine = (line: string): AssistantBlock | undefined => {
  const match = line.match(/^STEP\s*(\d+)\s*[:：]\s*(.+)$/i)
  if (!match) return
  const title = match[2].trim()
  return { type: 'step', title: `STEP ${match[1]}: ${title}` }
}
const parseFileLine = (line: string): AssistantBlock | undefined => {
  const match = line.match(/^(?:文件|file)\s*[:：]\s*(.+)$/i)
  if (!match) return
  const fileInfo = cleanupMessageLine(match[1])
  const [filePath] = extractFileTokens(fileInfo)
  return createFileBlock(filePath || fileInfo)
}
const parseDependencyLine = (line: string): AssistantBlock[] => {
  const blocks: AssistantBlock[] = []
  DEPENDENCY_STATUS_PATTERN.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = DEPENDENCY_STATUS_PATTERN.exec(line)) !== null) {
    blocks.push({
      type: 'dependency',
      name: match[1],
      status: match[2] === '安装失败' ? 'error' : match[2] === '安装中' ? 'running' : 'success',
      statusText: match[2],
    })
  }
  return blocks
}
const parsePlanLine = (line: string): AssistantBlock[] => {
  const normalizedLine = cleanupMessageLine(line)
  const match = normalizedLine.match(/^(?:生成计划\s*)?(?:创建|更新|修改|生成)([^：:]{0,40})[：:]\s*(.+)$/)
  if (!match) return []
  const files = extractFileTokens(match[2])
  if (!files.length) return []
  const title = match[1]?.trim()
  const blocks: AssistantBlock[] = []
  if (title) blocks.push({ type: 'step', title: `创建${title}` })
  blocks.push(...files.map((path) => createFileBlock(path)))
  return blocks
}
const cleanupMessageLine = (line: string) => line.replace(/^[-\s]+/, '').replace(/\s+/g, ' ').trim()
const pushTextBlock = (blocks: AssistantBlock[], content: string) => {
  if (!content) return
  const lastBlock = blocks[blocks.length - 1]
  if (lastBlock?.type === 'text') {
    lastBlock.content = `${lastBlock.content}\n${content}`
    return
  }
  blocks.push({ type: 'text', content })
}
const appendTextBlock = (assistant: ChatMessage, content: string) => {
  const blocks = assistant.blocks ?? (assistant.blocks = [])
  const lastBlock = blocks[blocks.length - 1]
  if (lastBlock?.type === 'text') {
    lastBlock.content += content
    return
  }
  blocks.push({ type: 'text', content })
}
const createStreamProcessBlocks = (streamMessage: API.ChatStreamMessage): AssistantBlock[] => {
  if (streamMessage.type === 'step') {
    if (!isVisibleStreamMessage(streamMessage)) return []
    return [{ type: 'step', title: formatStepTitle(streamMessage) }]
  }
  if (streamMessage.type === 'file_write') {
    return createFileWriteBlocks(streamMessage)
  }
  if (streamMessage.type === 'tool_call_result') {
    return createToolResultBlocks(streamMessage)
  }
  return []
}
const createFileWriteBlocks = (streamMessage: API.ChatStreamMessage, codeBlock?: CodeBlock): AssistantBlock[] => {
  if (!isVisibleStreamMessage(streamMessage)) return []
  const filePath = normalizeRelativePath(getMetadataString(streamMessage, 'path'))
  const fileName = getMetadataString(streamMessage, 'fileName') || getFileNameFromPath(filePath)
  const description = getMetadataString(streamMessage, 'description')
  const blocks: AssistantBlock[] = []
  const step = getMetadataNumber(streamMessage, 'step')
  if (step) blocks.push({ type: 'step', title: `STEP ${step}: ${description || `创建${fileName}`}` })
  blocks.push(createFileBlock(filePath || fileName, fileName, description))
  if (codeBlock) blocks.push(codeBlock)
  return blocks
}
const formatStepTitle = (streamMessage: API.ChatStreamMessage) => {
  const step = getMetadataNumber(streamMessage, 'step')
  const title = getMetadataString(streamMessage, 'title') || streamMessage.content || '生成步骤'
  const compactStep = title.match(/^STEP\s*(\d+)\s*[:：]\s*(.+)$/i)
  if (compactStep) return `STEP ${compactStep[1]}: ${compactStep[2].trim()}`
  return step ? `STEP ${step}: ${title.replace(/^STEP\s*\d+\s*[:：]?\s*/i, '').trim()}` : title
}
const createToolResultBlocks = (streamMessage: API.ChatStreamMessage): AssistantBlock[] => {
  const toolName = streamMessage.tool?.name || ''
  if (!INSTALL_TOOL_PATTERN.test(`${toolName} ${streamMessage.content || ''}`)) return []
  const packages = extractPackageTokens(`${streamMessage.tool?.arguments || ''} ${streamMessage.tool?.result || ''} ${streamMessage.content || ''}`)
  const status = streamMessage.tool?.success === false || streamMessage.status === 'error' ? 'error' : 'success'
  const statusText = status === 'error' ? '安装失败' : '安装成功'
  return packages.map((name) => ({ type: 'dependency', name, status, statusText }))
}
const createFileBlock = (path: string, fileName = getFileNameFromPath(path), description?: string): AssistantBlock => ({
  type: 'file',
  fileName: fileName || path,
  path: normalizeRelativePath(path),
  description,
})
const getMetadataString = (streamMessage: API.ChatStreamMessage, key: string) => {
  const value = streamMessage.metadata?.[key]
  return typeof value === 'string' ? value : value == null ? '' : String(value)
}
const getMetadataBoolean = (streamMessage: API.ChatStreamMessage, key: string) => {
  const value = streamMessage.metadata?.[key]
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value.toLowerCase() === 'true'
  return undefined
}
const getMetadataNumber = (streamMessage: API.ChatStreamMessage, key: string) => {
  const value = streamMessage.metadata?.[key]
  const numberValue = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numberValue) ? numberValue : undefined
}
const getStepNumberFromTitle = (title: string) => {
  const match = title.match(/^STEP\s*(\d+)\s*[:：]/i)
  const step = match ? Number(match[1]) : 0
  return Number.isFinite(step) ? step : 0
}
const extractFileTokens = (text: string) => Array.from(new Set(text.match(FILE_TOKEN_PATTERN) || []))
const extractPackageTokens = (text: string) => Array.from(new Set(text.match(/@?[\w.-]+(?:\/[\w.-]+)?/g) || []))
  .filter((token) => token.includes('-') || token.includes('/') || ['react', 'vue', 'vite'].includes(token))
const getFileNameFromPath = (path: string) => normalizeRelativePath(path).split('/').filter(Boolean).pop() || path
const normalizeRelativePath = (path: string) => {
  let normalizedPath = (path || '').replace(/\\/g, '/').trim()
  while (normalizedPath.startsWith('./')) normalizedPath = normalizedPath.slice(2)
  return normalizedPath
}
const isVisibleStreamMessage = (streamMessage: API.ChatStreamMessage) => {
  const visible = getMetadataBoolean(streamMessage, 'visible')
  if (visible !== undefined) return visible
  return Boolean(normalizeRelativePath(getMetadataString(streamMessage, 'path')))
}
const guessLanguageByPath = (path: string) => {
  if (path.endsWith('.jsx')) return 'jsx'
  if (path.endsWith('.tsx')) return 'tsx'
  if (path.endsWith('.js')) return 'javascript'
  if (path.endsWith('.ts')) return 'typescript'
  if (path.endsWith('.css')) return 'css'
  if (path.endsWith('.json')) return 'json'
  if (path.endsWith('.html')) return 'html'
  return 'text'
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
  typewriterQueue = []
  streamEnded = false
}
const getTypewriterQueueLength = () => typewriterQueue.reduce((total, job) => {
  if (job.type === 'blocks') return total + job.blocks.length
  return total + job.content.length
}, 0)
const getTypewriterBatchSize = () => {
  const queueLength = getTypewriterQueueLength()
  if (queueLength > 2400) return 32
  if (queueLength > 1200) return 16
  if (queueLength > 480) return 8
  if (queueLength > 160) return 4
  if (queueLength > 60) return 2
  return 1
}
const startTypewriter = () => {
  if (typewriterTimer === undefined) flushTypewriterQueue()
}
const queueTypewriterText = (content: string) => {
  typewriterQueue.push({ type: 'text', content })
  startTypewriter()
}
const queueTypewriterBlocks = (blocks: AssistantBlock[]) => {
  if (!blocks.length) return
  typewriterQueue.push({ type: 'blocks', blocks })
  startTypewriter()
}
const queueTypewriterCode = (block: CodeBlock, content: string) => {
  if (!content) {
    block.closed = true
    return
  }
  typewriterQueue.push({ type: 'code', content, block })
  startTypewriter()
}
const applyTypewriterChunk = (job: TypewriterJob, chunk: string, assistant: ChatMessage) => {
  if (job.type === 'blocks') {
    const blocks = assistant.blocks ?? (assistant.blocks = [])
    blocks.push(...job.blocks)
    return
  }
  if (job.type === 'code') {
    job.block.content += chunk
    return
  }
  assistant.content += chunk
  appendTextBlock(assistant, chunk)
}
const flushRemainingTypewriterQueue = (assistant: ChatMessage) => {
  typewriterQueue.forEach((job) => {
    applyTypewriterChunk(job, job.type === 'blocks' ? '' : job.content, assistant)
    if (job.type === 'code') job.block.closed = true
  })
  typewriterQueue = []
}
const flushTypewriterQueue = () => {
  typewriterTimer = undefined
  const assistant = getActiveAssistant()
  if (!assistant) return
  const job = typewriterQueue[0]
  if (!job) {
    if (streamEnded) completeGeneration()
    return
  }
  if (job.type === 'blocks') {
    applyTypewriterChunk(job, '', assistant)
    typewriterQueue.shift()
    scrollToBottom()
    if (typewriterQueue.length) typewriterTimer = window.setTimeout(flushTypewriterQueue, TYPEWRITER_INTERVAL)
    else if (streamEnded) completeGeneration()
    return
  }
  const batchSize = getTypewriterBatchSize()
  const chunk = job.content.slice(0, batchSize)
  applyTypewriterChunk(job, chunk, assistant)
  job.content = job.content.slice(batchSize)
  if (!job.content) {
    if (job.type === 'code') job.block.closed = true
    typewriterQueue.shift()
  }
  scrollToBottom()
  if (typewriterQueue.length) typewriterTimer = window.setTimeout(flushTypewriterQueue, TYPEWRITER_INTERVAL)
  else if (streamEnded) completeGeneration()
}
const finishGeneration = (source: EventSource) => {
  if (eventSource !== source) return
  source.close()
  eventSource = undefined
  streamEnded = true
  if (!typewriterQueue.length) completeGeneration()
}
const failGeneration = (source: EventSource | undefined, errorText: string) => {
  if (source && eventSource !== source) return
  source?.close()
  if (!source) eventSource?.close()
  eventSource = undefined
  clearTypewriterTimer()
  generating.value = false
  const assistant = getActiveAssistant()
  if (assistant) {
    assistant.pending = false
    if (typewriterQueue.length) flushRemainingTypewriterQueue(assistant)
    if (!hasAssistantOutput(assistant)) assistant.content = `生成失败：${errorText}`
  }
  activeAssistantIndex = undefined
  typewriterQueue = []
  streamEnded = false
  message.error(errorText)
  scrollToBottom()
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
  typewriterQueue = []
  streamEnded = false
  app.value.codeGenType = 'react_project'
  previewReady.value = true
  refreshPreview()
  void syncGeneratedAppInfo()
  scrollToBottom()
}
const sendMessage = () => send(input.value)
const handleEnter = (event: KeyboardEvent) => { if (!event.shiftKey) { event.preventDefault(); sendMessage() } }
const refreshPreview = () => previewKey.value++
const togglePreviewFullscreen = () => { if (previewReady.value) previewFullscreen.value = !previewFullscreen.value }
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
  previewFullscreen.value = false
  coverSyncId++
  eventSource?.close()
  eventSource = undefined
  resetTypewriter()
  stopResize()
})
</script>

<style scoped>
.chat-page {
  min-width: 1180px;
  min-height: 100vh;
  color: #172326;
  background: #f5f7f9;
}
.studio-header {
  position: relative;
  display: flex;
  height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 10px 0 14px;
  border-bottom: 0;
  background: #f5f7f9;
  backdrop-filter: none;
}
.app-title {
  display: flex;
  flex: 0 0 320px;
  align-items: center;
  gap: 12px;
  color: #1e292c;
  z-index: 1;
}
.app-title img {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  box-shadow: 0 10px 26px rgba(17, 44, 70, .13);
}
.app-title strong,
.app-title span {
  display: block;
}
.app-title strong {
  max-width: 260px;
  overflow: hidden;
  color: #172326;
  font-size: 15px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-title span {
  color: #94a0a2;
  font-size: 11px;
  letter-spacing: 1.8px;
}
.header-center {
  position: absolute;
  top: 0;
  z-index: 2;
  display: flex;
  height: 66px;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
  color: #8c9a9d;
  font-size: 13px;
}
.preview-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  background: transparent;
}
.header-icon-button {
  display: inline-flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 9px;
  color: #172326;
}
.header-icon-button :deep(.anticon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  line-height: 1;
}
.header-icon-button:hover,
.header-icon-button:focus {
  border-color: rgba(17, 24, 39, .08);
  color: #172326;
  background: #fff;
}
.studio-header :deep(.ant-space) {
  position: relative;
  z-index: 1;
}
.studio-header :deep(.ant-btn) {
  height: 38px;
  border-radius: 9px;
}
.studio-header :deep(.header-icon-button) {
  width: 38px;
  height: 38px;
  padding: 0;
}
.studio-header :deep(.ant-btn-default) {
  border-color: rgba(17, 24, 39, .1);
  background: rgba(255, 255, 255, .76);
}
.studio-header :deep(.ant-btn-primary) {
  border-color: #1f7aff;
  background: #1f7aff;
  box-shadow: 0 10px 22px rgba(31, 122, 255, .18);
}
.workspace {
  display: grid;
  height: calc(100vh - 66px);
  background: #f5f7f9;
}
.preview-fullscreen .workspace {
  grid-template-columns: 0 0 minmax(0, 1fr) 0 132px !important;
}
.resize-handle {
  position: relative;
  width: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: col-resize;
  touch-action: none;
}
.resize-handle::after {
  position: absolute;
  top: 50%;
  left: 2px;
  width: 3px;
  height: 42px;
  border-radius: 4px;
  background: #b6c7cc;
  content: "";
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity .2s, background .2s;
}
.resize-handle:hover::after,
.resize-handle:focus-visible::after,
.resizing .resize-handle::after {
  background: #24aaa1;
  opacity: 1;
}
.resize-handle:focus-visible {
  outline: 2px solid #24aaa1;
  outline-offset: -2px;
}
.resizing {
  cursor: col-resize;
  user-select: none;
}
.resizing iframe {
  pointer-events: none;
}
.conversation,
.version-bar {
  background: #f5f7f9;
}
.conversation {
  position: relative;
  display: flex;
  min-height: 0;
  flex-direction: column;
}
.preview-fullscreen .conversation,
.preview-fullscreen .resize-handle {
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 28px 34px 174px;
}
.welcome {
  display: flex;
  gap: 12px;
  margin-bottom: 28px;
  padding: 16px;
  border: 1px solid #e9eeee;
  border-radius: 18px;
  background: #f7fbfb;
}
.welcome h2 {
  margin: 0 0 6px;
  font-size: 16px;
}
.welcome p {
  margin: 0;
  color: #718385;
  font-size: 13px;
  line-height: 1.75;
}
.history-loading,
.history-load-more {
  display: flex;
  justify-content: center;
  margin: -6px 0 18px;
  color: #8a989b;
  font-size: 12px;
}
.history-load-more :deep(.ant-btn-link) {
  height: 30px;
  border-radius: 999px;
  color: #168f88;
  background: rgba(255, 255, 255, .82);
  font-weight: 700;
}
.history-load-more :deep(.ant-btn-link:hover) {
  background: #f1fbfa;
}
.ai-mark img,
.avatar img {
  width: 30px;
  height: 30px;
}
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 24px 0;
}
.message-row.user {
  justify-content: flex-end;
}
.avatar {
  flex: 0 0 auto;
}
.user-avatar {
  color: #fff;
  background: #1d9bf0;
  font-weight: 700;
}
.bubble {
  max-width: 100%;
  min-width: 0;
}
.assistant .bubble {
  padding: 0;
  border-radius: 0;
  background: transparent;
}
.user .bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 14px;
  color: #172326;
  background: #f0f2f4;
}
.message-role {
  display: none;
}
.message-content {
  color: #142126;
  font-size: 15px;
  line-height: 1.85;
  word-break: break-word;
}
.assistant .message-content {
  max-width: 100%;
}
.user .message-content {
  font-size: 14px;
  line-height: 1.65;
}
.message-text {
  margin: 0 0 16px;
  white-space: pre-wrap;
}
.process-summary {
  margin: 10px 0 18px;
  color: #172326;
  font-size: 15px;
  line-height: 1.8;
}
.process-step {
  margin: 20px 0 12px;
  color: #152129;
  font-size: 15px;
  line-height: 1.65;
  font-weight: 500;
}
.process-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  margin: 9px 0 9px 18px;
  color: #6e7780;
  font-size: 14px;
  line-height: 1.6;
}
.process-row :deep(.anticon) {
  flex: 0 0 auto;
  color: #8a939d;
  font-size: 15px;
}
.process-name {
  min-width: 0;
  color: #66717b;
  font-weight: 500;
}
.process-path,
.process-status {
  min-width: 0;
  overflow: hidden;
  color: #b3b8be;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.process-status {
  font-family: inherit;
}
.process-status.success {
  color: #2fbd8d;
}
.process-status.error {
  color: #e05b5b;
}
.process-status.running {
  color: #b38b29;
}
.file-row {
  margin-top: 12px;
}
.code-block {
  margin: 14px 0;
  overflow: hidden;
  border: 1px solid #dde5e5;
  border-radius: 12px;
  background: #fff;
}
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 11px;
  border-bottom: 1px solid #e6ebeb;
  color: #7b898b;
  background: #f8fafa;
  font-size: 11px;
  text-transform: lowercase;
}
.streaming-label {
  color: #199e96;
}
.code-block pre {
  max-height: 340px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  background: #fff;
}
.code-block code {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 11px;
  line-height: 1.65;
  white-space: pre;
}
.typing i {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin: 9px 3px 0 0;
  border-radius: 50%;
  background: #74c7bd;
  animation: pulse 1s infinite alternate;
}
.typing i:nth-child(2) {
  animation-delay: .2s;
}
.typing i:nth-child(3) {
  animation-delay: .4s;
}
@keyframes pulse {
  to {
    opacity: .25;
    transform: translateY(-3px);
  }
}
.scroll-to-latest {
  position: absolute;
  bottom: 150px;
  left: 50%;
  z-index: 1;
  padding: 7px 13px;
  border: 1px solid #bfe2de;
  border-radius: 16px;
  color: #168f88;
  background: rgba(255,255,255,.96);
  box-shadow: 0 4px 14px rgba(29,90,94,.12);
  cursor: pointer;
  font-size: 12px;
  transform: translateX(-50%);
}
.scroll-to-latest:hover {
  border-color: #24aaa1;
  background: #f4fbfa;
}
.composer-wrap {
  position: absolute;
  right: 14px;
  bottom: 18px;
  left: 14px;
}
.composer {
  padding: 14px 14px 12px;
  border: 1px solid rgba(17, 24, 39, .08);
  border-radius: 22px;
  background: #f6f7f8;
  box-shadow: 0 14px 34px rgba(15, 23, 42, .08);
}
.composer textarea {
  resize: none;
}
.composer-input.disabled {
  cursor: not-allowed;
}
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.composer-footer span {
  color: #8b9699;
  font-size: 12px;
}
.composer-footer :deep(.ant-btn-circle) {
  background: #aeb5bb;
}
.preview-pane {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 14px 14px 28px 10px;
  background: #f5f7f9;
}
.preview-fullscreen .preview-pane {
  padding-left: 18px;
}
.preview-stage {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1.5px solid #d7e0e8;
  border-radius: 20px;
  background: #eef2f6;
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.72), 0 16px 36px rgba(15, 23, 42, .05);
}
.preview-stage iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 18px;
  background: #fff;
}
.empty-preview {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.preview-illustration {
  display: grid;
  width: 76px;
  height: 76px;
  place-items: center;
  border-radius: 24px;
  color: #18a69c;
  background: #e7f7f5;
  font-size: 28px;
}
.empty-preview h2 {
  margin: 18px 0 5px;
}
.empty-preview p {
  max-width: 360px;
  color: #98a5a6;
  line-height: 1.7;
}
.version-bar {
  padding: 18px 10px;
  background: #f5f7f9;
}
.version-bar h3 {
  margin: 0 0 14px;
  font-size: 15px;
}
.version-card {
  width: 100%;
  padding: 8px;
  border: 1px solid #18aaa0;
  border-radius: 12px;
  color: #138f88;
  background: #f4fbfa;
  text-align: left;
}
.version-thumb {
  margin-top: 8px;
}
</style>
