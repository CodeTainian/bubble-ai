<template>
  <section class="showcase" :class="{ featured }">
    <div class="section-heading">
      <div><span>{{ eyebrow }}</span><h2>{{ title }}</h2></div>
      <a-input-search v-model:value="searchValue" allow-clear :placeholder="searchPlaceholder" @search="$emit('search')" />
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
      :current="pageNum"
      :page-size="pageSize"
      :total="total"
      hide-on-single-page
      @change="handlePageChange"
    />
  </section>
</template>

<script setup lang="ts">
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
  max-width: 1220px;
  margin: 52px auto 0;
  padding: 42px 42px 34px;
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

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

@media (max-width: 800px) {
  .showcase {
    padding: 30px 18px 16px;
  }
  .section-heading {
    align-items: flex-end;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
