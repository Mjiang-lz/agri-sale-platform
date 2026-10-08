<template>
  <!--
    融销通品牌标（v2 圆环麦穗）。
    几何：200×200 栅格，圆心 (100,100)；外环 R62 / 线宽 14 / 圆头收笔；
    缺口 60° 朝右上 45°（不可改方向）；三粒麦粒；缺口正中暖橙节点。
    viewBox 用 20 20 160 160 —— 让图形占满 87.5% 边长，与旧版 35×35 的留白一致，
    换标时不用改调用处的尺寸类。
  -->
  <svg
    class="rxt-logo"
    :width="size"
    :height="size"
    viewBox="20 20 160 160"
    fill="none"
    role="img"
    aria-label="融销通"
  >
    <path
      d="M159.9 83.95 A62 62 0 1 1 116.05 40.1"
      fill="none"
      :stroke="ring"
      stroke-width="14"
      stroke-linecap="round"
    />
    <ellipse cx="100" cy="126" rx="10.5" ry="12.5" :fill="grain" />
    <ellipse cx="100" cy="100" rx="12.5" ry="13.5" :fill="grain" />
    <ellipse cx="100" cy="74" rx="10.5" ry="12.5" :fill="grain" />
    <!-- 暖橙节点：缺口正中，指向「数据出山」那个方向。橙色是稀缺资源，全站只此一处 -->
    <circle cx="143.8" cy="56.2" r="7.5" fill="#F5A524" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: { type: [Number, String], default: 38 },
  /** color = 浅色底用；reverse = 深色底用（环与麦粒反白，暖橙节点保留） */
  variant: { type: String, default: 'color' },
})

const isReverse = computed(() => props.variant === 'reverse')
const ring = computed(() => (isReverse.value ? '#FFFFFF' : '#0E7A5F'))
const grain = computed(() => (isReverse.value ? '#FFFFFF' : '#1D9E75'))
</script>

<style scoped>
.rxt-logo {
  display: block;
  flex: none;
}
</style>
