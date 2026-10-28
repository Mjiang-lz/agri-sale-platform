<template>
  <!--
    融销通图标精灵表 —— 全站唯一一份 symbol 定义，挂在 App 根部。
    与 brand 规范同笔法：24 栅格 / 1.5px 圆头描边 / currentColor / 四周留 2px。
    「货源」保留填充麦粒，是唯一的品牌锚点（其余为线稿）。

    ⚠️ 不要改成 display:none —— 部分浏览器会因此不渲染 <use> 的引用。
    用 width/height 0 + overflow:hidden 隐藏。
  -->
  <svg class="rxt-sprite" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
    <!-- 首页 -->
    <symbol id="ico-home" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.2 10.6L12 3.4L20.8 10.6" />
      <path d="M5.6 9.4V19.4a1.2 1.2 0 001.2 1.2h10.4a1.2 1.2 0 001.2-1.2V9.4" />
      <path d="M9.9 20.6v-5.2h4.2v5.2" />
    </symbol>

    <!-- 农产品交易（父级）：双向箭头 = 产销对流 -->
    <symbol id="ico-trade" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M4.4 8.6h12.4" />
      <path d="M14.2 5.8l2.8 2.8-2.8 2.8" />
      <path d="M19.6 15.4H7.2" />
      <path d="M9.8 12.6l-2.8 2.8 2.8 2.8" />
    </symbol>

    <!-- 商品货源：品牌锚点，唯一填充图标 -->
    <symbol id="ico-source" viewBox="0 0 24 24">
      <path d="M12 20.8V6.8" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
      <ellipse cx="12" cy="6.9" rx="1.8" ry="2.4" fill="currentColor" />
      <ellipse cx="8.8" cy="11" rx="1.8" ry="2.2" fill="currentColor" fill-opacity="0.72" transform="rotate(-30 8.8 11)" />
      <ellipse cx="15.2" cy="11" rx="1.8" ry="2.2" fill="currentColor" fill-opacity="0.72" transform="rotate(30 15.2 11)" />
      <ellipse cx="8.8" cy="15.8" rx="1.8" ry="2.2" fill="currentColor" fill-opacity="0.72" transform="rotate(-30 8.8 15.8)" />
      <ellipse cx="15.2" cy="15.8" rx="1.8" ry="2.2" fill="currentColor" fill-opacity="0.72" transform="rotate(30 15.2 15.8)" />
    </symbol>

    <!-- 我的商品：货箱（等轴立方 + 中脊）。
         2026-10-13 新增：原来「我的商品」与「商品货源」共用 ico-source（一株苗），
         两个菜单项图标一模一样，用户无法靠图标区分「去看别人的货」和「管我自己的货」。
         货源那侧保留植株（品牌锚点），这一侧改成货箱 —— 「我上架的货」是物、不是苗。 -->
    <symbol id="ico-myproduct" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round">
      <path d="M3.6 8.1 12 3.9l8.4 4.2v7.8L12 20.1l-8.4-4.2V8.1Z" />
      <path d="M3.6 8.1 12 12.3l8.4-4.2" />
      <path d="M12 12.3v7.8" />
    </symbol>

    <!-- 求购需求：放大镜 + 清单行 = 在需求里检索 -->
    <symbol id="ico-demand" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="11" cy="11" r="6.2" />
      <path d="M15.4 15.4l4.6 4.6" />
      <path d="M8.4 9.4h5.2" />
      <path d="M8.4 12.6h3.2" />
    </symbol>

    <!-- 购物车 -->
    <symbol id="ico-cart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M2.6 4.4h2.2l2.3 9.8h9.9" />
      <path d="M5.2 7.6h14l-2 6.6H7.2" />
      <circle cx="9.2" cy="18.8" r="1.5" />
      <circle cx="16.4" cy="18.8" r="1.5" />
    </symbol>

    <!-- 我的订单：单据 + 对勾 -->
    <symbol id="ico-order" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M6 3.5H14.5L18 7v12a1.6 1.6 0 01-1.6 1.6H6A1.6 1.6 0 014.4 19V5.1A1.6 1.6 0 016 3.5z" />
      <path d="M14.5 3.5V7H18" />
      <path d="M8.4 14.2l2.2 2.2 4-4.6" />
    </symbol>

    <!-- 融资服务（父级）：银行柱 -->
    <symbol id="ico-finance" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3 9.6L12 4.4l9 5.2" />
      <path d="M6.4 10.8v6.8" />
      <path d="M12 10.8v6.8" />
      <path d="M17.6 10.8v6.8" />
      <path d="M3.4 19.6h17.2" />
    </symbol>

    <!-- 融资申请：单据 + 加号（与「我的订单」对勾、「我的融资」叠页构成一组） -->
    <symbol id="ico-apply" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M6.6 3.2h7.6l4 4v11.4a1.6 1.6 0 01-1.6 1.6H6.6a1.6 1.6 0 01-1.6-1.6V4.8a1.6 1.6 0 011.6-1.6z" />
      <path d="M14.2 3.2v4h4" />
      <path d="M9.9 12.4h5.2" />
      <path d="M12.5 9.8v5.2" />
    </symbol>

    <!-- 我的融资：叠放的台账页 -->
    <symbol id="ico-ledger" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.4 16.6V5.4a1.4 1.4 0 011.4-1.4h6.6" />
      <path d="M7.6 7h7.6l3.4 3.4v8.6a1.4 1.4 0 01-1.4 1.4H7.6a1.4 1.4 0 01-1.4-1.4V8.4A1.4 1.4 0 017.6 7z" />
      <path d="M15.2 7v3.4h3.4" />
      <path d="M9.6 13.6h6" />
      <path d="M9.6 16.8h3.8" />
    </symbol>

    <!-- 专家助力（父级）：专家名片 -->
    <symbol id="ico-expert" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3.2" y="4.2" width="17.6" height="15.6" rx="2.6" />
      <circle cx="12" cy="10.4" r="2.7" />
      <path d="M7.4 17.4c0-2.3 2.1-3.8 4.6-3.8s4.6 1.5 4.6 3.8" />
    </symbol>

    <!-- 农业知识：翻开的书 -->
    <symbol id="ico-knowledge" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 7.6v11.2" />
      <path d="M12 7.6C10.8 6.3 9 5.5 7 5.5H3.4v11.3H7c2 0 3.8.8 5 2.1" />
      <path d="M12 7.6c1.2-1.3 3-2.1 5-2.1h3.6v11.3H17c-2 0-3.8.8-5 2.1" />
    </symbol>

    <!-- 专家问答：对话框 + 问号 -->
    <symbol id="ico-qa" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M4.4 4.6h15.2a2 2 0 012 2v7a2 2 0 01-2 2h-8.8l-4 3.2v-3.2H4.4a2 2 0 01-2-2v-7a2 2 0 012-2z" />
      <path d="M10 8.8a2.2 2.2 0 013.9 1.4c0 1.5-2.2 1.6-2.2 2.9" />
      <circle cx="11.7" cy="15" r="0.85" fill="currentColor" stroke="none" />
    </symbol>

    <!-- 专家预约：日历 + 标记日 -->
    <symbol id="ico-calendar" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3.2" y="5.2" width="17.6" height="15.6" rx="2.6" />
      <path d="M3.2 10.2h17.6" />
      <path d="M8.2 3v4" />
      <path d="M15.8 3v4" />
      <circle cx="12" cy="15.4" r="1.6" fill="currentColor" stroke="none" />
    </symbol>

    <!-- 个人中心 / 用户管理 -->
    <symbol id="ico-user" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="12" cy="8.4" r="3.5" />
      <path d="M5.3 20.6c0-3.5 3-5.9 6.7-5.9s6.7 2.4 6.7 5.9" />
    </symbol>

    <!-- 消息提醒：铃铛。2026-10-13 新增（顶栏消息中心入口） -->
    <symbol id="ico-bell" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M18 8.8a6 6 0 1 0-12 0c0 5.8-2.2 7.2-2.2 7.2h16.4S18 14.6 18 8.8Z" />
      <path d="M13.7 19.8a2 2 0 0 1-3.4 0" />
    </symbol>

    <!-- 退出登录：门 + 外指箭头。2026-10-13 新增（顶栏常驻退出按钮） -->
    <symbol id="ico-logout" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M14.8 4.6H6.6v14.8h8.2" />
      <path d="M12.4 12h7" />
      <path d="M16.8 8.9 19.5 12l-2.7 3.1" />
    </symbol>

    <!-- 收货地址：地图钉。2026-10-13 新增 —— 原与「我的订单」共用 ico-order，
         同属「两个菜单项一个图标」的缺陷（与 ico-myproduct 同一次修）。 -->
    <symbol id="ico-address" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 20.9c0-.1-4.5-5.3-4.5-9.5a4.5 4.5 0 0 1 9 0c0 4.2-4.5 9.4-4.5 9.5Z" />
      <circle cx="12" cy="11.4" r="1.9" />
    </symbol>

    <!-- 仪表盘 -->
    <symbol id="ico-dashboard" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.6 17.2a8.4 8.4 0 1116.8 0" />
      <path d="M12 17.2l4.4-4.6" />
      <circle cx="12" cy="17.2" r="1.3" fill="currentColor" stroke="none" />
    </symbol>

    <!-- 内容审核：盾牌 + 对勾 -->
    <symbol id="ico-audit" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 3.2l7.6 2.7v6.1c0 4.3-3.2 7.5-7.6 8.8-4.4-1.3-7.6-4.5-7.6-8.8V5.9z" />
      <path d="M8.9 11.8l2.3 2.3 4.1-4.7" />
    </symbol>
  </svg>
</template>

<style scoped>
/* 用 w/h 0 + overflow hidden 隐藏，不用 display:none（后者会让 <use> 失效） */
.rxt-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
</style>
