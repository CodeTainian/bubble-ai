<template>
  <div class="chat-page" :class="{ 'preview-fullscreen': previewFullscreen, 'visual-editing': visualEditMode }">
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
              <FullscreenExitOutlined v-if="previewFullscreen" class="header-icon" />
              <FullscreenOutlined v-else class="header-icon" />
            </a-button>
          </a-tooltip>
          <a-tooltip title="刷新预览">
            <a-button class="header-icon-button" :disabled="!previewReady" @click="refreshPreview">
              <ReloadOutlined class="header-icon" />
            </a-button>
          </a-tooltip>
        </div>
      </div>
      <a-space class="header-actions">
        <a-tooltip title="打开生成页面"><a-button class="header-icon-button" :disabled="!previewReady" @click="openPreview"><CodeOutlined class="header-icon" /></a-button></a-tooltip>
        <a-tooltip title="下载代码"><a-button class="header-icon-button" :loading="downloading" :disabled="!previewReady || downloading" @click="downloadCode"><DownloadOutlined class="header-icon" /></a-button></a-tooltip>
        <a-button class="deploy-button" type="primary" :loading="deploying" :disabled="!previewReady" @click="deploy"><RocketOutlined /> 部署</a-button>
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
            <a-button class="history-load-more-button" size="small" type="link" :loading="historyLoadingMore" @click="loadMoreHistory">
              <UpOutlined /> 加载更多
            </a-button>
          </div>
          <article
            v-for="(item, index) in messages"
            :key="item.id || index"
            class="message-row"
            :class="{ 'message-row-user': item.role === 'user', 'message-row-assistant': item.role === 'assistant' }"
          >
            <div v-if="item.role === 'assistant'" class="avatar"><img src="@/assets/logo.svg" alt="" /></div>
            <div class="bubble" :class="{ 'bubble-user': item.role === 'user', 'bubble-assistant': item.role === 'assistant' }">
              <div class="message-role">{{ item.role === 'user' ? '你' : 'Bubble AI' }}</div>
              <div v-if="item.role === 'assistant'" class="message-content message-content-assistant">
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
              <div v-else class="message-content message-content-user">{{ item.content }}</div>
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
            <a-alert
              v-if="selectedVisualElement"
              class="selected-element-alert"
              type="info"
              show-icon
              closable
              @close="clearSelectedVisualElement"
            >
              <template #message>{{ selectedVisualElementTitle }}</template>
              <template #description>{{ selectedVisualElementDescription }}</template>
            </a-alert>
            <div class="composer-input" :class="{ disabled: !canChat }" :title="chatPermissionTip || undefined">
              <a-textarea v-model:value="input" :bordered="false" :disabled="!canChat" :auto-size="{ minRows: 3, maxRows: 5 }" placeholder="描述得越详细，页面越具体。可以继续提出修改意见..." @pressEnter="handleEnter" />
            </div>
            <div class="composer-footer">
              <span><MessageOutlined /> 继续对话完善页面</span>
              <div class="composer-actions">
                <a-tooltip :title="visualEditorTip">
                  <a-button
                    class="composer-edit-button"
                    :class="{ active: visualEditMode }"
                    shape="circle"
                    :disabled="!visualEditMode && !canUseVisualEditor"
                    @click="toggleVisualEditMode"
                  >
                    <EditOutlined />
                  </a-button>
                </a-tooltip>
                <a-button class="composer-send-button" type="primary" shape="circle" :loading="generating" :disabled="!canChat || !input.trim()" @click="sendMessage"><ArrowUpOutlined /></a-button>
              </div>
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
          <iframe v-if="previewReady" ref="previewFrame" :key="previewKey" :src="previewUrl" title="生成应用预览" @load="handlePreviewFrameLoad"></iframe>
          <div v-else class="empty-preview">
            <div v-if="previewLoading" class="preview-loader" aria-hidden="true">
              <div class="loader-toolbar"><span></span><span></span><span></span></div>
              <div class="loader-canvas">
                <i></i><i></i><i></i><i></i>
                <b></b>
              </div>
            </div>
            <div v-else class="preview-illustration"><CodeOutlined /></div>
            <h2>{{ previewPlaceholderTitle }}</h2>
            <p>{{ previewPlaceholderText }}</p>
            <a-button
              v-if="previewCheckFailed"
              class="preview-retry-button"
              type="primary"
              ghost
              :loading="previewRebuilding"
              :disabled="previewRebuilding"
              @click="retryPreviewCheck"
            >
              <ReloadOutlined /> 重新检查
            </a-button>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { ArrowUpOutlined, CodeOutlined, DownloadOutlined, EditOutlined, FullscreenExitOutlined, FullscreenOutlined, MessageOutlined, ReloadOutlined, RocketOutlined, UpOutlined } from '@ant-design/icons-vue'
import hljs from 'highlight.js/lib/core'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import xml from 'highlight.js/lib/languages/xml'
import 'highlight.js/styles/github.css'
import { deployApp, downloadAppCode, getAppVoById, rebuildApp } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import AppCover from '@/components/AppCover.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { APP_API_BASE_URL, APP_PREVIEW_BASE_URL } from '@/config/env'
import {
  buildVisualEditorPrompt,
  createVisualEditorBridge,
  getVisualEditorElementDescription,
  getVisualEditorElementTitle,
  type VisualEditorElementInfo,
} from '@/utils/visualEditor'

type ChatMessage = { id?: string; role: 'user' | 'assistant'; content: string; pending?: boolean; createTime?: string }
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
const generating = ref(false), deploying = ref(false), downloading = ref(false), previewReady = ref(false)
const historyLoading = ref(false), historyLoadingMore = ref(false), historyInitialized = ref(false), hasMoreHistory = ref(false)
const previewFullscreen = ref(false)
const followingOutput = ref(true)
const previewKey = ref(0)
const versionCoverRefreshKey = ref(0)
const messageList = ref<HTMLElement>()
const workspace = ref<HTMLElement>()
const previewFrame = ref<HTMLIFrameElement>()
const conversationWidth = ref(520), versionWidth = ref(132)
const resizingPane = ref<ResizePane>()
const visualEditMode = ref(false)
const selectedVisualElement = ref<VisualEditorElementInfo>()
let eventSource: EventSource | undefined
let activeAssistantIndex: number | undefined
let typewriterTimer: number | undefined
let typewriterQueue = ''
let streamEnded = false
let coverSyncId = 0
let previewCheckId = 0
const generatedCodeGenType = ref<string>()
const previewEntryUrl = ref('')
const previewChecking = ref(false)
const previewCheckFailed = ref(false)
const previewRebuilding = ref(false)
const historyIndicatesReactProject = ref(false)
const previewUrl = computed(() => previewEntryUrl.value || buildPreviewUrl(getPreferredPreviewCodeGenType()))
const previewLoading = computed(() => generating.value || previewChecking.value || previewRebuilding.value)
const previewPlaceholderTitle = computed(() => {
  if (generating.value) return '正在搭建你的应用'
  if (previewRebuilding.value) return '正在重新打包预览'
  if (previewChecking.value) return '正在构建项目预览'
  if (previewCheckFailed.value) return '预览还没准备好'
  return '等待生成网页'
})
const previewPlaceholderText = computed(() => {
  if (generating.value) return 'AI 正在编写项目文件，完成后会自动进入构建检查。'
  if (previewRebuilding.value) return '后端已开始重新打包，完成后会继续检查预览。'
  if (previewChecking.value) return '后端正在安装依赖并构建 React 项目，页面可访问后会自动展示。'
  if (previewCheckFailed.value) return '构建可能仍在继续，可以稍后重新检查预览。'
  return '在左侧输入你的想法，生成结果会出现在这里。'
})
const gridTemplateColumns = computed(() => `${conversationWidth.value}px 7px minmax(${MIN_PREVIEW_WIDTH}px, 1fr) 7px ${versionWidth.value}px`)
const canChat = computed(() => Boolean(appLoaded.value && app.value.userId && loginUserStore.loginUser.id && String(app.value.userId) === String(loginUserStore.loginUser.id)))
const chatPermissionTip = computed(() => appLoaded.value && !canChat.value ? '无法在别人的作品下对话哦~' : '')
const loginUserInitial = computed(() => (loginUserStore.loginUser.userName || loginUserStore.loginUser.userAccount || '我').slice(0, 1))
const canUseVisualEditor = computed(() => Boolean(canChat.value && previewReady.value && !generating.value))
const visualEditorTip = computed(() => {
  if (visualEditMode.value) return '退出可视化编辑'
  if (!canChat.value) return chatPermissionTip.value || '当前应用不可编辑'
  if (!previewReady.value) return '预览生成后可以点选页面元素'
  if (generating.value) return '生成过程中暂不可点选元素'
  return '点选预览中的元素'
})
const selectedVisualElementTitle = computed(() =>
  selectedVisualElement.value ? `已选中元素：${getVisualEditorElementTitle(selectedVisualElement.value)}` : ''
)
const selectedVisualElementDescription = computed(() =>
  selectedVisualElement.value ? getVisualEditorElementDescription(selectedVisualElement.value) : ''
)

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
const COVER_SYNC_POLL_DELAY = 1500
const REACT_PROJECT_CODE_GEN_TYPE = 'react_project'
const DEFAULT_CODE_GEN_TYPE = 'html'
const PREVIEW_READY_RETRY_COUNT = 45
const REBUILD_PREVIEW_READY_RETRY_COUNT = 300
const PREVIEW_READY_RETRY_DELAY = 1200
let resizeStartX = 0
let resizeStartWidth = 0
let historyCursor: string | undefined
let loadedHistoryTotal = 0
const loadedHistoryKeys = new Set<string>()
let visualEditorAccessWarned = false

const visualEditorBridge = createVisualEditorBridge({
  getIframe: () => previewFrame.value,
  onSelect: (element) => {
    selectedVisualElement.value = element
  },
  onError: () => {
    if (!visualEditMode.value || visualEditorAccessWarned) return
    visualEditorAccessWarned = true
    message.warning('无法注入可视化编辑脚本，请确认预览页面与主站同源')
  },
})

const clearSelectedVisualElement = () => {
  selectedVisualElement.value = undefined
  visualEditorBridge.clearSelection()
}

const exitVisualEditMode = () => {
  visualEditMode.value = false
  visualEditorBridge.disable()
}

const resetVisualEditorState = () => {
  selectedVisualElement.value = undefined
  if (visualEditMode.value) exitVisualEditMode()
  else visualEditorBridge.clearSelection()
}

const enterVisualEditMode = () => {
  if (!canUseVisualEditor.value) {
    message.warning(visualEditorTip.value)
    return
  }
  visualEditorAccessWarned = false
  visualEditMode.value = true
  visualEditorBridge.enable()
}

const toggleVisualEditMode = () => {
  if (visualEditMode.value) exitVisualEditMode()
  else enterVisualEditMode()
}

const handlePreviewFrameLoad = () => {
  if (visualEditMode.value) visualEditorBridge.refresh()
}

const normalizeCodeGenType = (codeGenType?: string) => (codeGenType || '').trim()
const isReactProjectType = (codeGenType?: string) => normalizeCodeGenType(codeGenType) === REACT_PROJECT_CODE_GEN_TYPE
const getPreferredPreviewCodeGenType = () =>
  normalizeCodeGenType(generatedCodeGenType.value)
  || normalizeCodeGenType(app.value.codeGenType)
  || DEFAULT_CODE_GEN_TYPE
const buildPreviewUrl = (codeGenType: string) => {
  const normalizedType = normalizeCodeGenType(codeGenType) || DEFAULT_CODE_GEN_TYPE
  const dirName = `${normalizedType}_${id}`
  return isReactProjectType(normalizedType)
    ? `${APP_PREVIEW_BASE_URL}/${dirName}/dist/index.html`
    : `${APP_PREVIEW_BASE_URL}/${dirName}/`
}
const pushUnique = <T,>(list: T[], item: T) => {
  if (!list.includes(item)) list.push(item)
}
const getPreviewCodeGenCandidates = () => {
  const candidates: string[] = []
  const appCodeGenType = normalizeCodeGenType(app.value.codeGenType)
  const generatedType = normalizeCodeGenType(generatedCodeGenType.value)
  if (generatedType) pushUnique(candidates, generatedType)
  if (historyIndicatesReactProject.value || isReactProjectType(appCodeGenType)) {
    pushUnique(candidates, REACT_PROJECT_CODE_GEN_TYPE)
  }
  if (appCodeGenType) pushUnique(candidates, appCodeGenType)
  pushUnique(candidates, REACT_PROJECT_CODE_GEN_TYPE)
  pushUnique(candidates, DEFAULT_CODE_GEN_TYPE)
  pushUnique(candidates, 'multi_file')
  return candidates
}
const addCacheBust = (url: string) => `${url}${url.includes('?') ? '&' : '?'}_t=${Date.now()}`
const isPreviewUrlAvailable = async (url: string) => {
  try {
    const res = await fetch(addCacheBust(url), {
      cache: 'no-store',
      credentials: 'include',
    })
    const contentType = res.headers.get('content-type') || ''
    return res.ok && contentType.includes('text/html')
  } catch {
    return false
  }
}
const shouldCheckPreview = () => Boolean(loadedHistoryTotal >= 2 || loadedHistoryKeys.size >= 2 || generatedCodeGenType.value)
const resetPreviewState = () => {
  previewReady.value = false
  previewEntryUrl.value = ''
  previewChecking.value = false
  previewCheckFailed.value = false
  previewRebuilding.value = false
}
const sleep = (duration: number) => new Promise((resolve) => window.setTimeout(resolve, duration))
const resolvePreviewReady = async (retryCount = 1) => {
  const currentCheckId = ++previewCheckId
  const attempts = Math.max(1, retryCount)
  previewReady.value = false
  previewEntryUrl.value = ''
  previewChecking.value = true
  previewCheckFailed.value = false

  for (let attempt = 0; attempt < attempts; attempt++) {
    const candidates = getPreviewCodeGenCandidates()
    for (const codeGenType of candidates) {
      if (currentCheckId !== previewCheckId) return false
      const candidateUrl = buildPreviewUrl(codeGenType)
      if (await isPreviewUrlAvailable(candidateUrl)) {
        if (currentCheckId !== previewCheckId) return false
        previewEntryUrl.value = candidateUrl
        previewReady.value = true
        previewChecking.value = false
        previewCheckFailed.value = false
        if (isReactProjectType(codeGenType)) {
          generatedCodeGenType.value = REACT_PROJECT_CODE_GEN_TYPE
        }
        previewKey.value++
        return true
      }
    }
    if (attempt < attempts - 1) await sleep(PREVIEW_READY_RETRY_DELAY)
  }

  if (currentCheckId === previewCheckId) {
    previewChecking.value = false
    previewCheckFailed.value = true
  }
  return false
}

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
const isReactProjectHistoryContent = (content?: string) =>
  /\[(?:工具调用|选择工具)]\s*写入文件|package\.json|vite\.config\.(?:js|mjs|ts)|src\/main\.jsx/.test(content || '')
const mergeHistoryMessages = (records: API.ChatHistory[], mode: 'replace' | 'prepend') => {
  if (mode === 'replace') historyIndicatesReactProject.value = false
  const newMessages = sortHistoryAsc(records).reduce<ChatMessage[]>((result, history) => {
    const key = getHistoryKey(history)
    if (loadedHistoryKeys.has(key)) return result
    loadedHistoryKeys.add(key)
    const chatMessage = toChatMessage(history)
    if (chatMessage.role === 'assistant' && isReactProjectHistoryContent(chatMessage.content)) {
      historyIndicatesReactProject.value = true
    }
    result.push(chatMessage)
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
  if (!shouldCheckPreview()) {
    resetPreviewState()
    return
  }
  void resolvePreviewReady()
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
  const cursorBeforeLoad = historyCursor
  try {
    const params: API.listAppChatHistoryParams = { appId: id, pageSize: HISTORY_PAGE_SIZE }
    if (cursorBeforeLoad) params.lastCreateTime = cursorBeforeLoad
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
    if (!records.length || (!addedCount && historyCursor === cursorBeforeLoad)) hasMoreHistory.value = false
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
  const historyLoaded = await loadInitialHistory()
  if (route.query.auto === '1') {
    await router.replace({ path: route.path })
  }
  if (historyLoaded && canChat.value && loadedHistoryTotal === 0 && loadedHistoryKeys.size === 0 && app.value.initPrompt) {
    send(app.value.initPrompt)
  }
}
const syncGeneratedAppInfo = async () => {
  const currentSyncId = ++coverSyncId
  const previousCover = app.value.cover
  const previousUpdateTime = app.value.updateTime
  while (currentSyncId === coverSyncId) {
    if (currentSyncId !== coverSyncId) return
    try {
      const res = await getAppVoById({ id })
      if (currentSyncId !== coverSyncId) return
      if (res.data.code === 0 && res.data.data) {
        app.value = { ...app.value, ...res.data.data }
        const nextCover = res.data.data.cover
        const nextUpdateTime = res.data.data.updateTime
        if (nextCover) {
          versionCoverRefreshKey.value = Date.now()
          const coverChanged = nextCover !== previousCover
          const coverCreated = !previousCover
          const coverUpdated = Boolean(previousUpdateTime && nextUpdateTime && nextUpdateTime !== previousUpdateTime)
          if (coverCreated || coverChanged || coverUpdated) return
        }
      }
    } catch {
      // 生成结果已经可预览，封面同步失败时保持当前封面，下一次进入页面仍会加载最新数据。
    }
    await sleep(COVER_SYNC_POLL_DELAY)
  }
}
const send = (content: string) => {
  if (!content.trim() || generating.value || !canChat.value) return
  const userMessage = content.trim()
  const aiMessage = buildVisualEditorPrompt(userMessage, selectedVisualElement.value)
  eventSource?.close()
  resetTypewriter()
  messages.value.push({ role: 'user', content: userMessage })
  activeAssistantIndex = messages.value.push({ role: 'assistant', content: '', pending: true }) - 1
  input.value = ''
  resetVisualEditorState()
  generating.value = true
  generatedCodeGenType.value = REACT_PROJECT_CODE_GEN_TYPE
  historyIndicatesReactProject.value = true
  previewCheckId++
  previewReady.value = false
  previewEntryUrl.value = ''
  previewChecking.value = false
  previewCheckFailed.value = false
  previewRebuilding.value = false
  const source = new EventSource(`${APP_API_BASE_URL}/app/chat/gen/code?appId=${id}&message=${encodeURIComponent(aiMessage)}`, { withCredentials: true })
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
const normalizeTextBlock = (content: string) =>
  content.replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim()
const appendTextBlock = (blocks: MessageBlock[], content: string) => {
  const normalizedContent = normalizeTextBlock(content)
  if (normalizedContent) blocks.push({ type: 'text', content: normalizedContent })
}
const parseMessageBlocks = (content: string): MessageBlock[] => {
  const blocks: MessageBlock[] = []
  const openingFence = /```([^\n`]*)\n?/g
  let cursor = 0
  let match: RegExpExecArray | null
  while ((match = openingFence.exec(content)) !== null) {
    if (match.index > cursor) appendTextBlock(blocks, content.slice(cursor, match.index))
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
  if (cursor < content.length) appendTextBlock(blocks, content.slice(cursor))
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
  generatedCodeGenType.value = REACT_PROJECT_CODE_GEN_TYPE
  previewReady.value = false
  previewEntryUrl.value = ''
  previewCheckFailed.value = false
  previewRebuilding.value = false
  void resolvePreviewReady(PREVIEW_READY_RETRY_COUNT)
  void syncGeneratedAppInfo()
  scrollToBottom()
}
const sendMessage = () => send(input.value)
const handleEnter = (event: KeyboardEvent) => { if (!event.shiftKey) { event.preventDefault(); sendMessage() } }
const refreshPreview = () => {
  clearSelectedVisualElement()
  previewKey.value++
}
const retryPreviewCheck = async () => {
  if (previewRebuilding.value || previewChecking.value) return
  previewRebuilding.value = true
  previewCheckFailed.value = false
  previewReady.value = false
  previewEntryUrl.value = ''
  try {
    const res = await rebuildApp({ appId: id })
    if (res.data.code !== 0 || !res.data.data) {
      message.error('重新打包失败：' + (res.data.message || '请稍后再试'))
      previewCheckFailed.value = true
      return
    }
    void syncGeneratedAppInfo()
  } catch {
    message.error('重新打包失败，请稍后再试')
    previewCheckFailed.value = true
    return
  } finally {
    previewRebuilding.value = false
  }
  void resolvePreviewReady(REBUILD_PREVIEW_READY_RETRY_COUNT)
}
const safeDecodeURIComponent = (value: string) => {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}
const getResponseHeader = (headers: unknown, headerName: string) => {
  if (!headers || typeof headers !== 'object') return ''
  const headerGetter = (headers as { get?: (name: string) => unknown }).get
  if (typeof headerGetter === 'function') {
    const value = headerGetter.call(headers, headerName)
    if (typeof value === 'string') return value
  }
  const headerMap = headers as Record<string, unknown>
  const value = headerMap[headerName] ?? headerMap[headerName.toLowerCase()]
  if (typeof value === 'string') return value
  if (Array.isArray(value) && typeof value[0] === 'string') return value[0]
  return ''
}
const getDownloadFileName = (contentDisposition: string) => {
  const encodedFileName = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encodedFileName) return safeDecodeURIComponent(encodedFileName)
  const quotedFileName = contentDisposition.match(/filename="?([^";]+)"?/i)?.[1]
  return quotedFileName ? safeDecodeURIComponent(quotedFileName) : ''
}
const getFallbackDownloadFileName = () => {
  const rawName = app.value.appName?.trim() || `bubble-ai-app-${id}`
  const safeName = rawName.replace(/[\\/:*?"<>|]+/g, '_').replace(/\s+/g, '_') || `bubble-ai-app-${id}`
  return safeName.toLowerCase().endsWith('.zip') ? safeName : `${safeName}.zip`
}
const readBlobMessage = async (blob: Blob) => {
  const text = await blob.text()
  if (!text) return ''
  try {
    const parsed = JSON.parse(text) as { message?: unknown }
    return typeof parsed.message === 'string' ? parsed.message : text
  } catch {
    return text
  }
}
const resolveDownloadErrorMessage = async (error: unknown) => {
  const responseData = (error as { response?: { data?: unknown } }).response?.data
  if (responseData instanceof Blob) {
    const blobMessage = await readBlobMessage(responseData)
    if (blobMessage) return blobMessage
  }
  if (typeof responseData === 'string') return responseData
  if (responseData && typeof responseData === 'object' && 'message' in responseData) {
    const messageValue = (responseData as { message?: unknown }).message
    if (typeof messageValue === 'string') return messageValue
  }
  return error instanceof Error && error.message ? error.message : '请稍后再试'
}
const triggerBlobDownload = (blob: Blob, fileName: string) => {
  const blobUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => window.URL.revokeObjectURL(blobUrl), 100)
}
const togglePreviewFullscreen = () => { if (previewReady.value) previewFullscreen.value = !previewFullscreen.value }
const openPreview = () => window.open(previewUrl.value, '_blank')
const downloadCode = async () => {
  if (downloading.value) return
  downloading.value = true
  try {
    const res = await downloadAppCode({ appId: id }, { timeout: 0 })
    const blob = res.data
    const contentType = getResponseHeader(res.headers, 'content-type') || blob.type
    if (contentType.includes('application/json')) {
      const errorMessage = await readBlobMessage(blob)
      message.error('下载失败：' + (errorMessage || '请稍后再试'))
      return
    }
    if (!blob.size) {
      message.error('下载失败：文件内容为空')
      return
    }
    const fileName = getDownloadFileName(getResponseHeader(res.headers, 'content-disposition')) || getFallbackDownloadFileName()
    triggerBlobDownload(blob, fileName)
    message.success('代码压缩包已开始下载')
  } catch (error) {
    message.error('下载失败：' + await resolveDownloadErrorMessage(error))
  } finally {
    downloading.value = false
  }
}
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
watch(canUseVisualEditor, (canUse) => {
  if (!canUse) resetVisualEditorState()
})

onMounted(loadApp)
onBeforeUnmount(() => {
  previewFullscreen.value = false
  visualEditorBridge.destroy()
  coverSyncId++
  previewCheckId++
  previewRebuilding.value = false
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
  border-color: rgba(17, 24, 39, .1);
  border-radius: 9px;
  color: #172326;
  background: rgba(255, 255, 255, .76);
}
.header-icon {
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
.header-actions {
  position: relative;
  z-index: 1;
}
.deploy-button {
  height: 38px;
  border-radius: 9px;
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
.history-load-more-button {
  height: 30px;
  border-radius: 999px;
  color: #168f88;
  background: rgba(255, 255, 255, .82);
  font-weight: 700;
}
.history-load-more-button:hover {
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
  margin: 18px 0;
}
.message-row-user {
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
.bubble-assistant {
  padding: 0;
  border-radius: 0;
  background: transparent;
}
.bubble-user {
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
.message-content-assistant {
  max-width: 100%;
}
.message-content-user {
  font-size: 14px;
  line-height: 1.65;
}
.message-text {
  white-space: pre-wrap;
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
.selected-element-alert {
  margin-bottom: 10px;
  border-color: rgba(36, 170, 161, .26);
  border-radius: 14px;
  background: #f1fbfa;
}
.selected-element-alert :deep(.ant-alert-message) {
  color: #142126;
  font-size: 12px;
  font-weight: 700;
}
.selected-element-alert :deep(.ant-alert-description) {
  color: #5f7174;
  font-size: 12px;
  line-height: 1.55;
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
.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.composer-edit-button {
  border-color: rgba(17, 24, 39, .1);
  color: #172326;
  background: #fff;
}
.composer-edit-button:hover,
.composer-edit-button:focus {
  border-color: #24aaa1;
  color: #168f88;
}
.composer-edit-button.active {
  border-color: #24aaa1;
  color: #fff;
  background: #24aaa1;
}
.composer-send-button {
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
.visual-editing .preview-stage {
  border-color: rgba(36, 170, 161, .78);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.72), 0 16px 36px rgba(36, 170, 161, .14);
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
  padding: 32px;
  text-align: center;
}
.preview-loader {
  position: relative;
  width: min(320px, 62%);
  overflow: hidden;
  border: 1px solid rgba(16, 66, 94, .14);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 20px 42px rgba(28, 47, 66, .11);
}
.preview-loader::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 18%, rgba(31, 122, 255, .11) 45%, transparent 72%);
  content: "";
  transform: translateX(-100%);
  animation: preview-sweep 1.65s ease-in-out infinite;
}
.loader-toolbar {
  display: flex;
  gap: 6px;
  padding: 12px;
  border-bottom: 1px solid #edf2f6;
  background: #fbfcfe;
}
.loader-toolbar span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d7e2eb;
}
.loader-toolbar span:nth-child(1) {
  background: #ffb86a;
}
.loader-toolbar span:nth-child(2) {
  background: #54c7ec;
}
.loader-toolbar span:nth-child(3) {
  background: #37c99b;
}
.loader-canvas {
  position: relative;
  display: grid;
  min-height: 150px;
  grid-template-columns: 1.2fr .8fr;
  gap: 12px;
  padding: 20px;
  background:
    linear-gradient(90deg, rgba(146, 163, 176, .08) 1px, transparent 1px),
    linear-gradient(rgba(146, 163, 176, .08) 1px, transparent 1px),
    #fff;
  background-size: 22px 22px;
}
.loader-canvas i {
  display: block;
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, #dfe8ee, #b9d8ef, #dfe8ee);
  background-size: 220% 100%;
  animation: preview-line 1.2s ease-in-out infinite;
}
.loader-canvas i:nth-child(1) {
  grid-column: 1 / 3;
  width: 72%;
}
.loader-canvas i:nth-child(2) {
  width: 88%;
  animation-delay: .12s;
}
.loader-canvas i:nth-child(3) {
  width: 64%;
  animation-delay: .24s;
}
.loader-canvas i:nth-child(4) {
  grid-column: 1 / 3;
  width: 54%;
  animation-delay: .36s;
}
.loader-canvas b {
  position: absolute;
  right: 24px;
  bottom: 22px;
  width: 46px;
  height: 46px;
  border: 3px solid rgba(24, 166, 156, .18);
  border-top-color: #18a69c;
  border-radius: 50%;
  animation: preview-spin .9s linear infinite;
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
.preview-retry-button {
  margin-top: 14px;
  border-radius: 10px;
}
@keyframes preview-sweep {
  to {
    transform: translateX(100%);
  }
}
@keyframes preview-line {
  0%, 100% {
    background-position: 0 0;
  }
  50% {
    background-position: 100% 0;
  }
}
@keyframes preview-spin {
  to {
    transform: rotate(360deg);
  }
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
