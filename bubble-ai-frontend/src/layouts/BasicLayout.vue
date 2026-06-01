<template>
  <a-layout class="basic-layout" :class="{ 'fullscreen-layout': route.meta.fullscreen }">
    <!-- 顶部导航栏 -->
    <GlobalHeader v-if="!route.meta.fullscreen" />

    <!-- 主要内容区域 -->
    <a-layout-content class="main-content" :class="{ 'fullscreen-content': route.meta.fullscreen }">
      <router-view />
    </a-layout-content>

    <!-- 底部版权信息 -->
    <GlobalFooter v-if="!route.meta.fullscreen" />
  </a-layout>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
</script>

<style scoped>
.basic-layout {
  background: none;
}

.main-content {
  max-width: 1200px;
  padding: 24px;
  background: white;
  margin: 16px auto 56px;
  width: calc(100% - 32px);
}

.fullscreen-layout,
.fullscreen-content {
  min-height: 100vh;
}

.fullscreen-content {
  max-width: none;
  width: 100%;
  padding: 0;
  margin: 0;
}
</style>
