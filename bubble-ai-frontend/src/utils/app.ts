import { Modal } from 'ant-design-vue'

export const getAppName = (app: API.AppVO) => app.appName || '未命名应用'

export const confirmDeleteApp = (app: API.AppVO, onOk: () => Promise<void> | void) => {
  Modal.confirm({
    title: `确认删除“${getAppName(app)}”？`,
    content: '删除后无法恢复。',
    okType: 'danger',
    onOk,
  })
}
