<template>
  <!--
    页面级「返回顶部」按钮。用于内容较长的列表页。

    为什么用 Element Plus 的 el-backtop 而不是自己实现：
    它监听 window.scrollY，而本项目的滚动容器就是整个文档
    （MainLayout 里 .main 已改为 overflow: visible，滚动交给文档），
    两者正好对上，不需要自己监听 scroll 事件再防抖。

    样式：去掉 el-backtop 默认的灰底与阴影，换成与业务端统一的绿调。
  -->
  <el-backtop
    :right="right"
    :bottom="bottom"
    :visibility-height="visibilityHeight"
    class="page-backtop"
  >
    <el-button circle class="page-backtop-btn" aria-label="返回顶部" title="返回顶部">
      <!--
        内联 SVG 而不是 RxtIcon：图标精灵表（components/RxtIconSprite.vue）
        里只有 home/trade/cart/order/user/dashboard/audit 等 16 个图标，没有 ico-up。
        为这一个箭头去改公共精灵表，影响面远大于直接画一个 17px 的路径。
        stroke 用 currentColor，hover 时跟随按钮文字色一起变白。
      -->
      <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
        <path
          d="M12 19V5M5 12l7-7 7 7"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </el-button>
  </el-backtop>
</template>

<script setup>
/**
 * 页面级返回顶部按钮。
 *
 * 默认值按列表页的实际情况定：
 * - bottom=88 而不是 el-backtop 自带的 40，是为了让开分页器所在的那一行，
 *   否则按钮会压住「共 24条 / 12条每页」，看着像挡住了内容。
 * - visibilityHeight=260：滚过约一屏才出现。给一屏缓冲而不是 0，
 *   否则页面稍微一动按钮就闪，视觉噪声大。
 *
 * ⚠️ 这里用**具名 props 声明**，不要写 `withDefaults(defineProps(), {...})`。
 * 那个匿名写法在本项目编译后拿不到默认值——实测根元素 inline style 变成了
 * el-backtop 自带的 `right:40px; bottom:40px`，参数被静默忽略，
 * build 也发现不了（模板里 `right="t.right"` 照常编译，只是取到 undefined）。
 * 具名声明让编译器在构建期就能校验，也便于在IDE 里看到类型。
 *
 * 页面较短（可滚动距离不足）时按钮不渲染，这是 el-backtop 的预期行为，不是 bug。
 */
defineProps({
  /** 距视口右侧距离（px） */
  right: { type: Number, default: 24 },
  /** 距视口底部距离（px） */
  bottom: { type: Number, default: 88 },
  /** 滚动超过多少 px 后才显示 */
  visibilityHeight: { type: Number, default: 260 }
})
</script>

<style scoped>
/*
  注意：这里的 class 直接写在 el-backtop 上，它是组件的根元素，
  本身就是 scoped 的目标，**不要用 :deep()**（组合后选择器匹配不到）。
*/
.page-backtop {
  background: transparent;
  box-shadow: none;
}
.page-backtop-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--rxt-brand, var(--el-color-primary));
  border: 1px solid var(--rxt-border);
  background: var(--el-bg-color);
  box-shadow: var(--rxt-shadow-sm, 0 2px 8px rgb(0 0 0 / 8%));
}
.page-backtop-btn:hover {
  color: #fff;
  border-color: var(--rxt-brand, var(--el-color-primary));
  background: var(--rxt-brand, var(--el-color-primary));
}
/* 键盘聚焦时也要有可见轮廓，否则 tab 导航到它看不出焦点在哪 */
.page-backtop-btn:focus-visible {
  outline: 2px solid var(--rxt-brand, var(--el-color-primary));
  outline-offset: 2px;
}
</style>