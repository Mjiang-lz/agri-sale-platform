import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// 必须紧跟 Element Plus 样式之后：覆盖的是它在 :root 上定义的变量，靠后加载取胜。
import './styles/theme.css'
// 页级原语（.page-head / .content-card / 卡片层级）。同样依赖「在 EP 之后」的顺序，
// 它有一条选择器与 .el-card.is-never-shadow 特异性打平，靠顺序决出胜负。
import './styles/layout.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
