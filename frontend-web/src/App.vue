<template>
  <!-- 图标精灵表只需挂一次，放最外层，供全站 <RxtIcon> 通过 #id 引用 -->
  <RxtIconSprite />
  <router-view />
</template>

<script setup>
import RxtIconSprite from './components/RxtIconSprite.vue'
</script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
</style>
