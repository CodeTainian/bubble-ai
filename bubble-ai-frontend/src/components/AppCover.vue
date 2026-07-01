<template>
  <div class="app-cover" :class="[`app-cover-${variant}`, { 'has-overlay': Boolean($slots.overlay) }]">
    <a-image v-if="showCover && preview" :src="coverUrl" :alt="alt" :preview="preview" @error="markCoverFailed" />
    <img v-else-if="showCover" :src="coverUrl" :alt="alt" class="cover-image" @error="markCoverFailed" />
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
import { computed, ref, watch } from 'vue'

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

const coverLoadFailed = ref(false)

const rawCoverUrl = computed(() => {
  if (!props.cover) return ''
  if (!props.refreshKey) return props.cover
  const separator = props.cover.includes('?') ? '&' : '?'
  return `${props.cover}${separator}t=${props.refreshKey}`
})

const isLoopbackHost = (host: string) => ['localhost', '127.0.0.1', '0.0.0.0', '[::1]', '::1'].includes(host)

const normalizeCoverUrl = (value: string) => {
  if (!value) return ''
  try {
    const coverUrl = new URL(value, window.location.origin)
    const isAbsoluteUrl = /^[a-z][a-z\d+\-.]*:\/\//i.test(value)
    const isOutputCover = coverUrl.pathname.startsWith('/output_covers/')
    if (!isOutputCover) return value

    const canUseApiProxy =
      !isAbsoluteUrl ||
      coverUrl.hostname === window.location.hostname ||
      (isLoopbackHost(coverUrl.hostname) && isLoopbackHost(window.location.hostname))

    return canUseApiProxy ? `/api${coverUrl.pathname}${coverUrl.search}${coverUrl.hash}` : value
  } catch {
    return value
  }
}

const coverUrl = computed(() => normalizeCoverUrl(rawCoverUrl.value))
const showCover = computed(() => Boolean(coverUrl.value) && !coverLoadFailed.value)

const markCoverFailed = () => {
  coverLoadFailed.value = true
}

watch(rawCoverUrl, () => {
  coverLoadFailed.value = false
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
.app-cover-card {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 16px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, .08);
}
.app-cover-thumb {
  width: 100%;
  height: 55px;
  border-radius: 4px;
  background: #daf1ef;
}
.app-cover-table {
  width: 92px;
  height: 52px;
  border-radius: 12px;
}
.cover-image,
.app-cover :deep(.ant-image),
.app-cover :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
}
.cover-image,
.app-cover :deep(.ant-image-img) {
  object-fit: cover;
}

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
