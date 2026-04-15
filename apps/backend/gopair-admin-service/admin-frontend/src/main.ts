import './assets/styles/global.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAppStore } from '@/stores/app'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 应用启动时立即应用主题，防止闪屏
const appStore = useAppStore()
appStore.applyTheme()

app.mount('#app')
