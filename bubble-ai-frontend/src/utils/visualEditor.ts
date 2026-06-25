export type VisualEditorElementInfo = {
  tagName: string
  selector: string
  text: string
  attributes: Record<string, string>
  rect: {
    x: number
    y: number
    width: number
    height: number
  }
}

type VisualEditorCommand = 'enable' | 'disable' | 'clearSelection'

type VisualEditorFrameWindow = Window & {
  __BUBBLE_AI_VISUAL_EDITOR__?: Record<VisualEditorCommand, () => void>
}

type VisualEditorMessage = {
  source?: string
  type?: string
  payload?: VisualEditorElementInfo
}

type VisualEditorBridgeOptions = {
  getIframe: () => HTMLIFrameElement | undefined
  onSelect: (element: VisualEditorElementInfo) => void
  onError?: (error: unknown) => void
}

const VISUAL_EDITOR_SOURCE = 'bubble-ai-visual-editor'
const VISUAL_EDITOR_COMMAND = 'bubble-ai-visual-editor:command'
const VISUAL_EDITOR_SELECTED = 'bubble-ai-visual-editor:selected'

const INJECTED_VISUAL_EDITOR_SCRIPT = `
;(function () {
  var SOURCE = '${VISUAL_EDITOR_SOURCE}'
  var COMMAND_TYPE = '${VISUAL_EDITOR_COMMAND}'
  var SELECTED_TYPE = '${VISUAL_EDITOR_SELECTED}'

  if (window.__BUBBLE_AI_VISUAL_EDITOR__) return

  var state = {
    active: false,
    hoverEl: null,
    selectedEl: null,
    hoverBox: null,
    selectedBox: null,
    styleEl: null,
    rafId: 0
  }

  function createBox(kind) {
    var box = document.createElement('div')
    box.setAttribute('data-bubble-visual-editor-overlay', kind)
    box.style.position = 'fixed'
    box.style.left = '0'
    box.style.top = '0'
    box.style.zIndex = '2147483647'
    box.style.pointerEvents = 'none'
    box.style.boxSizing = 'border-box'
    box.style.borderRadius = '6px'
    box.style.opacity = '0'
    box.style.transition = 'opacity .12s ease, transform .12s ease, width .12s ease, height .12s ease'
    if (kind === 'selected') {
      box.style.border = '2px solid #1677ff'
      box.style.background = 'rgba(22, 119, 255, .07)'
      box.style.boxShadow = '0 0 0 2px rgba(22, 119, 255, .18)'
    } else {
      box.style.border = '1.5px dashed #24aaa1'
      box.style.background = 'rgba(36, 170, 161, .05)'
      box.style.boxShadow = '0 0 0 2px rgba(36, 170, 161, .12)'
    }
    ;(document.body || document.documentElement).appendChild(box)
    return box
  }

  function getBox(kind) {
    if (kind === 'selected') {
      if (!state.selectedBox) state.selectedBox = createBox('selected')
      return state.selectedBox
    }
    if (!state.hoverBox) state.hoverBox = createBox('hover')
    return state.hoverBox
  }

  function hideBox(box) {
    if (box) box.style.opacity = '0'
  }

  function removeBox(box) {
    if (box && box.parentNode) box.parentNode.removeChild(box)
  }

  function isOverlayElement(element) {
    return !!(element && element.getAttribute && element.getAttribute('data-bubble-visual-editor-overlay'))
  }

  function isInspectableElement(element) {
    return !!(
      element &&
      element.nodeType === 1 &&
      !isOverlayElement(element) &&
      element !== document.documentElement &&
      element !== document.body &&
      element.tagName !== 'SCRIPT' &&
      element.tagName !== 'STYLE' &&
      element.tagName !== 'META' &&
      element.tagName !== 'LINK'
    )
  }

  function getEventElement(event) {
    var path = typeof event.composedPath === 'function' ? event.composedPath() : []
    for (var index = 0; index < path.length; index++) {
      if (isInspectableElement(path[index])) return path[index]
    }
    return isInspectableElement(event.target) ? event.target : null
  }

  function updateBox(box, element) {
    if (!box || !element || !document.documentElement.contains(element)) {
      hideBox(box)
      return
    }
    var rect = element.getBoundingClientRect()
    if (!rect.width || !rect.height) {
      hideBox(box)
      return
    }
    box.style.width = rect.width + 'px'
    box.style.height = rect.height + 'px'
    box.style.transform = 'translate(' + rect.left + 'px, ' + rect.top + 'px)'
    box.style.opacity = '1'
  }

  function updateBoxes() {
    state.rafId = 0
    updateBox(getBox('selected'), state.selectedEl)
    if (state.hoverEl && state.hoverEl !== state.selectedEl) updateBox(getBox('hover'), state.hoverEl)
    else hideBox(state.hoverBox)
  }

  function requestBoxUpdate() {
    if (state.rafId) return
    state.rafId = window.requestAnimationFrame(updateBoxes)
  }

  function trimText(value, maxLength) {
    var text = String(value || '').replace(/\\s+/g, ' ').trim()
    return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
  }

  function escapeCss(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value)
    return String(value).replace(/[^a-zA-Z0-9_-]/g, function (char) {
      return '\\\\' + char
    })
  }

  function getClassNames(element) {
    return trimText(element.getAttribute('class') || '', 120)
  }

  function getSelectorSegment(element) {
    var tagName = element.tagName.toLowerCase()
    var id = element.getAttribute('id')
    if (id) return tagName + '#' + escapeCss(id)
    var className = getClassNames(element)
    var classSelector = className
      ? '.' + className.split(/\\s+/).filter(Boolean).slice(0, 3).map(escapeCss).join('.')
      : ''
    var sameTagIndex = 1
    var sibling = element
    while ((sibling = sibling.previousElementSibling)) {
      if (sibling.tagName === element.tagName) sameTagIndex++
    }
    return tagName + classSelector + ':nth-of-type(' + sameTagIndex + ')'
  }

  function buildSelector(element) {
    var parts = []
    var current = element
    while (current && current.nodeType === 1 && current !== document.body && current !== document.documentElement) {
      parts.unshift(getSelectorSegment(current))
      if (current.getAttribute('id')) break
      current = current.parentElement
    }
    return parts.join(' > ')
  }

  function getAttributes(element) {
    var allowed = ['id', 'class', 'href', 'src', 'alt', 'title', 'role', 'aria-label', 'name', 'type', 'placeholder']
    var result = {}
    for (var index = 0; index < allowed.length; index++) {
      var name = allowed[index]
      var value = element.getAttribute(name)
      if (value) result[name] = trimText(value, 160)
    }
    for (var attrIndex = 0; attrIndex < element.attributes.length; attrIndex++) {
      var attr = element.attributes[attrIndex]
      if (attr.name.indexOf('data-') === 0 && Object.keys(result).length < 14) {
        result[attr.name] = trimText(attr.value, 160)
      }
    }
    return result
  }

  function getElementInfo(element) {
    var rect = element.getBoundingClientRect()
    return {
      tagName: element.tagName.toLowerCase(),
      selector: buildSelector(element),
      text: trimText(element.innerText || element.textContent || '', 160),
      attributes: getAttributes(element),
      rect: {
        x: Math.round(rect.left),
        y: Math.round(rect.top),
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      }
    }
  }

  function emitSelected(element) {
    window.parent.postMessage({
      source: SOURCE,
      type: SELECTED_TYPE,
      payload: getElementInfo(element)
    }, '*')
  }

  function handlePointerMove(event) {
    if (!state.active) return
    var element = getEventElement(event)
    if (!element || state.hoverEl === element) return
    state.hoverEl = element
    requestBoxUpdate()
  }

  function handlePointerLeave() {
    if (!state.active) return
    state.hoverEl = null
    hideBox(state.hoverBox)
  }

  function handleClick(event) {
    if (!state.active) return
    var element = getEventElement(event)
    if (!element) return
    event.preventDefault()
    event.stopPropagation()
    if (typeof event.stopImmediatePropagation === 'function') event.stopImmediatePropagation()
    state.selectedEl = element
    emitSelected(element)
    requestBoxUpdate()
  }

  function handleScrollOrResize() {
    if (state.active) requestBoxUpdate()
  }

  function addListeners() {
    document.addEventListener('pointermove', handlePointerMove, true)
    document.addEventListener('pointerleave', handlePointerLeave, true)
    document.addEventListener('click', handleClick, true)
    window.addEventListener('scroll', handleScrollOrResize, true)
    window.addEventListener('resize', handleScrollOrResize)
  }

  function removeListeners() {
    document.removeEventListener('pointermove', handlePointerMove, true)
    document.removeEventListener('pointerleave', handlePointerLeave, true)
    document.removeEventListener('click', handleClick, true)
    window.removeEventListener('scroll', handleScrollOrResize, true)
    window.removeEventListener('resize', handleScrollOrResize)
  }

  function ensureStyle() {
    if (state.styleEl) return
    state.styleEl = document.createElement('style')
    state.styleEl.setAttribute('data-bubble-visual-editor-style', 'true')
    state.styleEl.textContent = 'html[data-bubble-visual-editor-active] * { cursor: crosshair !important; }'
    ;(document.head || document.documentElement).appendChild(state.styleEl)
  }

  function enable() {
    if (state.active) {
      requestBoxUpdate()
      return
    }
    state.active = true
    ensureStyle()
    document.documentElement.setAttribute('data-bubble-visual-editor-active', 'true')
    addListeners()
    requestBoxUpdate()
  }

  function clearSelection() {
    state.selectedEl = null
    hideBox(state.selectedBox)
  }

  function disable() {
    if (!state.active && !state.selectedEl && !state.hoverEl) return
    state.active = false
    state.hoverEl = null
    state.selectedEl = null
    document.documentElement.removeAttribute('data-bubble-visual-editor-active')
    removeListeners()
    if (state.rafId) {
      window.cancelAnimationFrame(state.rafId)
      state.rafId = 0
    }
    removeBox(state.hoverBox)
    removeBox(state.selectedBox)
    state.hoverBox = null
    state.selectedBox = null
  }

  window.__BUBBLE_AI_VISUAL_EDITOR__ = {
    enable: enable,
    disable: disable,
    clearSelection: clearSelection
  }

  window.addEventListener('message', function (event) {
    var data = event.data || {}
    if (data.source !== SOURCE || data.type !== COMMAND_TYPE) return
    if (data.command === 'enable') enable()
    if (data.command === 'disable') disable()
    if (data.command === 'clearSelection') clearSelection()
  })
})()
`

const isVisualEditorMessage = (data: unknown): data is VisualEditorMessage =>
  Boolean(data && typeof data === 'object' && (data as VisualEditorMessage).source === VISUAL_EDITOR_SOURCE)

const formatAttributes = (attributes: Record<string, string>) =>
  Object.entries(attributes)
    .map(([key, value]) => `${key}="${value}"`)
    .join(' ')

export const getVisualEditorElementTitle = (element: VisualEditorElementInfo) => {
  const id = element.attributes.id ? `#${element.attributes.id}` : ''
  const className = element.attributes.class
    ? '.' + element.attributes.class.split(/\s+/).filter(Boolean).slice(0, 2).join('.')
    : ''
  return `${element.tagName}${id || className ? ` ${id}${className}` : ''}`
}

export const getVisualEditorElementDescription = (element: VisualEditorElementInfo) => {
  const text = element.text ? `文本：${element.text}` : ''
  const selector = `选择器：${element.selector}`
  return [selector, text].filter(Boolean).join('；')
}

export const buildVisualEditorPrompt = (message: string, element?: VisualEditorElementInfo) => {
  const userMessage = message.trim()
  if (!element) return userMessage
  const attributes = formatAttributes(element.attributes)
  return [
    userMessage,
    '',
    '请优先基于用户在预览页面中选中的元素进行修改。选中元素信息如下：',
    `- 标签：${element.tagName}`,
    `- 选择器：${element.selector}`,
    element.text ? `- 文本：${element.text}` : '',
    attributes ? `- 属性：${attributes}` : '',
    `- 位置尺寸：x=${element.rect.x}, y=${element.rect.y}, width=${element.rect.width}, height=${element.rect.height}`,
  ].filter(Boolean).join('\n')
}

export const createVisualEditorBridge = (options: VisualEditorBridgeOptions) => {
  let active = false
  let listening = false
  let injectTimer: number | undefined

  const clearInjectTimer = () => {
    if (injectTimer === undefined) return
    window.clearTimeout(injectTimer)
    injectTimer = undefined
  }

  const getFrameWindow = () => {
    const iframe = options.getIframe()
    return iframe?.contentWindow as VisualEditorFrameWindow | undefined
  }

  const getFrameApi = () => {
    try {
      return getFrameWindow()?.__BUBBLE_AI_VISUAL_EDITOR__
    } catch (error) {
      options.onError?.(error)
      return undefined
    }
  }

  const postCommand = (command: VisualEditorCommand) => {
    const iframe = options.getIframe()
    const frameWindow = iframe?.contentWindow
    if (!frameWindow) return
    let targetOrigin = window.location.origin
    try {
      targetOrigin = new URL(iframe.src, window.location.href).origin
    } catch {
      targetOrigin = '*'
    }
    frameWindow.postMessage({ source: VISUAL_EDITOR_SOURCE, type: VISUAL_EDITOR_COMMAND, command }, targetOrigin)
  }

  const runCommand = (command: VisualEditorCommand) => {
    const frameApi = getFrameApi()
    if (frameApi?.[command]) {
      frameApi[command]()
      return
    }
    postCommand(command)
  }

  const inject = () => {
    const frameWindow = getFrameWindow()
    if (!frameWindow) return false
    try {
      if (!frameWindow.__BUBBLE_AI_VISUAL_EDITOR__) {
        const doc = frameWindow.document
        if (!doc?.documentElement) return false
        const script = doc.createElement('script')
        script.textContent = INJECTED_VISUAL_EDITOR_SCRIPT
        ;(doc.head || doc.documentElement).appendChild(script)
        script.remove()
      }
      return Boolean(frameWindow.__BUBBLE_AI_VISUAL_EDITOR__)
    } catch (error) {
      options.onError?.(error)
      return false
    }
  }

  const refresh = () => {
    clearInjectTimer()
    injectTimer = window.setTimeout(() => {
      injectTimer = undefined
      if (!active) return
      if (inject()) runCommand('enable')
    })
  }

  const handleMessage = (event: MessageEvent<unknown>) => {
    if (!active || event.source !== getFrameWindow()) return
    if (!isVisualEditorMessage(event.data)) return
    if (event.data.type === VISUAL_EDITOR_SELECTED && event.data.payload) {
      options.onSelect(event.data.payload)
    }
  }

  const startListening = () => {
    if (listening) return
    window.addEventListener('message', handleMessage)
    listening = true
  }

  const stopListening = () => {
    if (!listening) return
    window.removeEventListener('message', handleMessage)
    listening = false
  }

  const enable = () => {
    active = true
    startListening()
    refresh()
  }

  const disable = () => {
    active = false
    clearInjectTimer()
    runCommand('disable')
    stopListening()
  }

  const clearSelection = () => {
    runCommand('clearSelection')
  }

  const destroy = () => {
    disable()
    stopListening()
  }

  return {
    enable,
    disable,
    clearSelection,
    refresh,
    destroy,
  }
}
