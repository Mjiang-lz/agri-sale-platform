<template>
  <svg
    class="rxt-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
    focusable="false"
  >
    <use :href="`#${name}`" />
  </svg>
</template>

<script setup>
/**
 * 融销通图标。
 *
 * 用法：<RxtIcon name="ico-source" :size="18" />
 * 颜色由父级 color 决定（图标内部用 currentColor），所以默认态 / 选中态
 * 不需要两套文件 —— 这也是品牌规范里定下的做法。
 *
 * name 可选值见 components/RxtIconSprite.vue。
 */
defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
})
</script>

<style scoped>
.rxt-icon {
  display: block;
  flex: none;
  /* 图标本身不参与点击，避免顶掉菜单项的可点区域 */
  pointer-events: none;
}
</style>
