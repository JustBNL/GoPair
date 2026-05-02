import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'

// 生产环境禁用 console.log / console.warn / console.debug，保留 console.error
if (import.meta.env.PROD) {
  const noop = () => {}
  console.log = noop
  console.warn = noop
  console.debug = noop
  console.group = noop
  console.groupEnd = noop
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
// ant-design-vue 组件通过 unplugin-vue-components 自动按需引入，无需 app.use(Antd)

app.mount('#app')
