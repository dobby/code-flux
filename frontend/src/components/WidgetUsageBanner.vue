<script setup lang="ts">
import { ChevronRight, Copy, Flame } from 'lucide-vue-next'

defineProps<{
  title: string
  description: string
}>()

const emit = defineEmits<{
  (event: 'duplicate'): void
}>()
</script>

<template>
  <section class="usage-banner">
    <div class="usage-banner__icon">
      <Flame :size="15" />
    </div>
    <div class="usage-banner__content">
      <div class="usage-banner__eyebrow">Shared widget</div>
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
    <button class="usage-banner__action" type="button" @click="emit('duplicate')">
      <Copy :size="14" />
      <span>Duplicate & customize</span>
      <ChevronRight :size="14" />
    </button>
  </section>
</template>

<style scoped>
.usage-banner {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 40px;
  padding: 0 20px;
  border: 0;
  border-bottom: 1px solid rgba(234, 179, 8, 0.22);
  border-radius: 0;
  background: rgba(234, 179, 8, 0.08);
  color: #92400e;
}

.usage-banner__icon {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  background: transparent;
}

.usage-banner__content {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.usage-banner__eyebrow {
  display: none;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.8;
}

.usage-banner__content strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #162033;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.usage-banner__content p {
  margin: 0;
  color: #92400e;
  font-size: 11px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.usage-banner__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid rgba(234, 179, 8, 0.22);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  color: #78350f;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .usage-banner {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: start;
    padding: 8px 16px;
  }

  .usage-banner__content {
    display: grid;
    gap: 2px;
  }

  .usage-banner__action {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
