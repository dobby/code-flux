import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { useAppearanceStore } from './stores/appearance'

const pinia = createPinia()
const app = createApp(App)

app.use(pinia)
app.use(router)

const appearance = useAppearanceStore(pinia)
void appearance.initialize()

app.mount('#app')
