<template>
  <a-layout class="basic-layout" :class="{ 'fullscreen-layout': route.meta.fullscreen }">
    <GlobalHeader v-if="!route.meta.fullscreen" />

    <a-layout-content class="main-content" :class="{ 'fullscreen-content': route.meta.fullscreen, 'full-bleed-content': route.meta.fullBleed }">
      <router-view v-slot="{ Component, route: viewRoute }">
        <KeepAlive>
          <component
            :is="Component"
            v-if="viewRoute.meta.keepAlive"
            :key="viewRoute.path"
          />
        </KeepAlive>
        <component
          :is="Component"
          v-if="!viewRoute.meta.keepAlive"
          :key="viewRoute.fullPath"
        />
      </router-view>
    </a-layout-content>

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
  min-height: 100vh;
  background:
    radial-gradient(circle at 12% 8%, rgba(89, 211, 205, .12), transparent 28%),
    radial-gradient(circle at 86% 12%, rgba(102, 166, 255, .1), transparent 26%),
    #f6f8fb;
  background-attachment: fixed;
}

.main-content {
  max-width: 1400px;
  padding: 0 24px;
  background: transparent;
  margin: 30px auto 64px;
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
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.full-bleed-content {
  max-width: none;
  width: 100%;
  padding: 0;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}
</style>
