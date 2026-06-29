import { Modal } from 'ant-design-vue'

export const getAppName = (app: API.AppVO) => app.appName || '未命名应用'

const CODE_GEN_TYPE_META: Record<string, { label: string; description: string }> = {
  html: { label: 'html', description: '原生 HTML 模式' },
  multi_file: { label: 'multi_file', description: '原生多文件模式' },
  react_project: { label: 'react_project', description: 'React 工程模式' },
}

export const normalizeCodeGenType = (codeGenType?: string) => (codeGenType || '').trim()

export const getCodeGenTypeDisplay = (codeGenType?: string, fallback = '未选择') => {
  const normalized = normalizeCodeGenType(codeGenType)
  if (!normalized) return fallback
  return CODE_GEN_TYPE_META[normalized]?.label ?? normalized
}

export const getCodeGenTypeDescription = (codeGenType?: string, fallback = '暂无生成类型') => {
  const normalized = normalizeCodeGenType(codeGenType)
  if (!normalized) return fallback
  return CODE_GEN_TYPE_META[normalized]?.description ?? '自定义生成模式'
}

export const confirmDeleteApp = (app: API.AppVO, onOk: () => Promise<void> | void) => {
  Modal.confirm({
    title: `确认删除“${getAppName(app)}”？`,
    content: '删除后无法恢复。',
    okType: 'danger',
    onOk,
  })
}
