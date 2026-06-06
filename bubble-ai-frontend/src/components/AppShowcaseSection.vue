<template>
  <section class="showcase" :class="{ featured }">
    <div class="section-heading">
      <div><span>{{ eyebrow }}</span><h2>{{ title }}</h2></div>
      <div class="section-search">
        <a-input
          v-model:value="searchValue"
          allow-clear
          :placeholder="searchPlaceholder"
          @press-enter="$emit('search')"
        />
        <a-button class="search-button" @click="$emit('search')">
          <SearchOutlined />
          搜索
        </a-button>
      </div>
    </div>
    <a-spin :spinning="loading">
      <div v-if="apps.length" class="card-grid">
        <AppCard
          v-for="item in apps"
          :key="item.id"
          :app="item"
          :editable="editable"
          :featured="featured"
          :owner-only="ownerOnly"
          @edit="$emit('edit', $event)"
          @delete="$emit('delete', $event)"
        />
      </div>
      <a-empty v-else :description="emptyDescription" />
    </a-spin>
    <a-pagination
      v-if="total > pageSize"
      class="section-pagination"
      :current="pageNum"
      :page-size="pageSize"
      :total="total"
      hide-on-single-page
      @change="handlePageChange"
    />
  </section>
</template>

<script setup lang="ts">
import { SearchOutlined } from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'

defineProps<{
  apps: API.AppVO[]
  emptyDescription: string
  eyebrow: string
  featured?: boolean
  editable?: boolean
  loading: boolean
  ownerOnly?: boolean
  pageNum: number
  pageSize: number
  searchPlaceholder: string
  title: string
  total: number
}>()

const emit = defineEmits<{
  delete: [app: API.AppVO]
  edit: [app: API.AppVO]
  pageChange: [page: number, pageSize: number]
  search: []
}>()

const searchValue = defineModel<string | undefined>('searchValue')
const handlePageChange = (page: number, pageSize: number) => emit('pageChange', page, pageSize)
</script>

<style scoped>
.showcase {
  position: relative;
  z-index: 1;
  width: min(1500px, calc(100vw - 96px));
  margin: 38px auto 0;
  padding: 46px 52px 40px;
  border: 1px solid rgba(255,255,255,.54);
  border-radius: 28px;
  background: rgba(255,255,255,.9);
  box-shadow: 0 26px 70px rgba(31, 111, 148, .12);
  backdrop-filter: blur(14px);
}
.featured {
  padding-bottom: 70px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 24px;
}
.section-heading span {
  color: #0eaaa0;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 7px;
}
.section-heading h2 {
  margin: 5px 0 0;
  color: #15272b;
  font-size: 30px;
}
.section-search {
  display: flex;
  flex: 0 1 360px;
  width: 360px;
  max-width: 42%;
  align-items: center;
  gap: 8px;
}
.section-search :deep(.ant-input-affix-wrapper) {
  height: 34px;
  align-items: center;
  border-color: rgba(17, 24, 39, .14);
  border-radius: 999px;
  background: rgba(255, 255, 255, .78);
  box-shadow: none;
  padding: 0 13px 0 16px;
}
.section-search :deep(.ant-input) {
  height: 32px;
  background: transparent;
  font-size: 13px;
  line-height: 32px;
  padding: 0;
}
.section-search :deep(.ant-input::placeholder) {
  color: #a7b0b7;
  line-height: 32px;
}
.section-search :deep(.ant-input-clear-icon) {
  display: inline-flex;
  align-items: center;
}
.search-button {
  display: inline-flex;
  height: 34px;
  align-items: center;
  border-color: rgba(17, 24, 39, .18);
  border-radius: 999px;
  color: #111827;
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, .06);
  font-size: 13px;
  font-weight: 700;
  gap: 4px;
  padding-inline: 15px;
}
.search-button:hover,
.search-button:focus {
  border-color: rgba(17, 24, 39, .32);
  color: #111827;
  background: #fff;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 34px 28px;
}
.section-pagination {
  display: flex;
  width: fit-content;
  justify-content: center;
  margin: 44px auto 0;
  padding: 6px 10px;
  border: 1px solid rgba(17, 24, 39, .08);
  border-radius: 999px;
  background: rgba(255, 255, 255, .72);
  box-shadow: 0 12px 26px rgba(15, 23, 42, .06);
}
.section-pagination :deep(.ant-pagination-item),
.section-pagination :deep(.ant-pagination-prev),
.section-pagination :deep(.ant-pagination-next) {
  min-width: 32px;
  height: 32px;
  line-height: 32px;
}
.section-pagination :deep(.ant-pagination-item) {
  border-radius: 10px;
}
.section-pagination :deep(.ant-pagination-item-active) {
  border-color: #1f7aff;
  box-shadow: 0 6px 14px rgba(31, 122, 255, .14);
}
.section-pagination :deep(.ant-pagination-prev .ant-pagination-item-link),
.section-pagination :deep(.ant-pagination-next .ant-pagination-item-link) {
  border-radius: 10px;
}

@media (max-width: 800px) {
  .showcase {
    width: calc(100vw - 28px);
    padding: 30px 18px 20px;
  }
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }
  .section-search {
    width: 100%;
    max-width: none;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
  .section-pagination {
    margin-top: 32px;
  }
}
</style>
