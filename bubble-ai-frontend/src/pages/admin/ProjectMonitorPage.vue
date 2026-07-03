<template>
  <div class="admin-page project-monitor-page">
    <div class="page-title">
      <div>
        <h2>项目监控</h2>
        <p>统一进入业务监控、时序数据库和性能监控公网控制台</p>
      </div>
    </div>

    <div class="monitor-grid">
      <a
        v-for="item in monitorItems"
        :key="item.title"
        class="monitor-card"
        :href="item.url"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div class="monitor-icon">
          <component :is="item.icon" />
        </div>
        <div class="monitor-content">
          <div class="monitor-title">{{ item.title }}</div>
          <div class="monitor-desc">{{ item.desc }}</div>
          <div class="monitor-url">{{ item.url }}</div>
        </div>
        <ExportOutlined class="monitor-open-icon" />
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  DashboardOutlined,
  DatabaseOutlined,
  ExportOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import {
  APP_MONITOR_ARMS_URL,
  APP_MONITOR_GRAFANA_URL,
  APP_MONITOR_PROMETHEUS_URL,
} from '@/config/env'

const monitorItems = [
  {
    title: '业务监控',
    desc: 'Grafana',
    url: APP_MONITOR_GRAFANA_URL,
    icon: DashboardOutlined,
  },
  {
    title: 'Prometheus 时序数据库',
    desc: 'Prometheus',
    url: APP_MONITOR_PROMETHEUS_URL,
    icon: DatabaseOutlined,
  },
  {
    title: '性能监控',
    desc: '阿里云 ARMS',
    url: APP_MONITOR_ARMS_URL,
    icon: ThunderboltOutlined,
  },
]
</script>

<style scoped>
.project-monitor-page {
  min-height: 520px;
}

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.monitor-card {
  position: relative;
  display: flex;
  gap: 16px;
  min-height: 150px;
  padding: 22px;
  color: #172b3a;
  border: 1px solid rgba(213, 221, 232, .92);
  border-radius: 8px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 18px 44px rgba(15, 23, 42, .06);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}

.monitor-card:hover {
  color: #172b3a;
  border-color: rgba(22, 119, 255, .38);
  box-shadow: 0 22px 54px rgba(15, 23, 42, .1);
  transform: translateY(-2px);
}

.monitor-icon {
  display: flex;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  color: #1677ff;
  font-size: 22px;
  border-radius: 8px;
  background: #edf5ff;
}

.monitor-content {
  min-width: 0;
  padding-right: 22px;
}

.monitor-title {
  margin-bottom: 8px;
  color: #152633;
  font-size: 18px;
  font-weight: 800;
}

.monitor-desc {
  margin-bottom: 16px;
  color: #6a7a89;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
}

.monitor-url {
  overflow-wrap: anywhere;
  color: #486071;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.45;
}

.monitor-open-icon {
  position: absolute;
  top: 20px;
  right: 20px;
  color: #7b8b99;
  font-size: 16px;
}

@media (max-width: 900px) {
  .monitor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
