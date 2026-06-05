<template>
  <div class="app-cover" :class="[`app-cover-${variant}`, { 'has-overlay': Boolean($slots.overlay) }]">
    <a-image v-if="coverUrl && preview" :src="coverUrl" :alt="alt" :preview="preview" />
    <img v-else-if="coverUrl" :src="coverUrl" :alt="alt" class="cover-image" />
    <div v-else class="cover-placeholder">
      <img src="@/assets/logo.svg" alt="" />
      <span v-if="placeholder">{{ placeholder }}</span>
    </div>
    <div v-if="$slots.overlay" class="cover-overlay">
      <slot name="overlay"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  alt?: string
  cover?: string
  placeholder?: string
  preview?: boolean
  refreshKey?: number | string
  variant?: 'card' | 'thumb' | 'table'
}>(), {
  alt: '应用封面',
  placeholder: '',
  preview: false,
  variant: 'card',
})

const coverUrl = computed(() => {
  if (!props.cover) return ''
  if (!props.refreshKey) return props.cover
  const separator = props.cover.includes('?') ? '&' : '?'
  return `${props.cover}${separator}t=${props.refreshKey}`
})
</script>

<style scoped>
.app-cover {
  position: relative;
  display: grid;
  overflow: hidden;
  place-items: center;
  background: #eff9f8;
}

.cover-image,

.cover-image,

.cover-placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #86a4a3;
  background: linear-gradient(135deg, #f3fbfa, #e5f5f6);
}
.cover-placeholder img {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  opacity: .75;
}
.app-cover-thumb .cover-placeholder img {
  width: 35px;
  height: 35px;
}
.app-cover-table .cover-placeholder img {
  width: 30px;
  height: 30px;
}
.cover-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(12, 37, 42, .24);
  opacity: 0;
  transition: opacity .2s ease, visibility .2s ease;
  visibility: hidden;
}
.has-overlay:hover .cover-overlay,
.has-overlay:focus-within .cover-overlay {
  opacity: 1;
  visibility: visible;
}
</style>
