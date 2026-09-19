import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { NTag, NIcon } from 'naive-ui'
import App from './WorkspaceVerificationApp.vue'
import Designer from '@/views/print/designer.vue'
import Preview from '@/views/print/preview.vue'
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/workspace', component: { render: () => null } }, { path: '/print/designer', component: Designer }, { path: '/print/preview', component: Preview }] })
await router.push('/workspace')
createApp(App).use(createPinia()).use(router).component('NTag', NTag).component('NIcon', NIcon).mount('#app')
