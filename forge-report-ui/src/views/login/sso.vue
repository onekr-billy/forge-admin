<template>
  <div class="sso-login-page">
    <div class="sso-panel">
      <div v-if="loading" class="sso-loading">
        <n-spin size="large">
          <template #description>
            正在登录报表系统
          </template>
        </n-spin>
      </div>

      <n-result
        v-else-if="errorMessage"
        status="error"
        title="单点登录失败"
        :description="errorMessage"
      >
        <template #footer>
          <n-space justify="center">
            <n-button type="primary" @click="handleExchange">
              重试
            </n-button>
            <n-button @click="goLogin">
              返回登录页
            </n-button>
          </n-space>
        </template>
      </n-result>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ssoExchangeApi } from '@/api/auth'
import { PageEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { clearLocalStorage, setLocalStorage } from '@/utils/storage'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorMessage = ref('')

const redirectPath = computed(() => normalizeRedirectPath(route.query.redirect))

function normalizeRedirectPath(value: unknown) {
  const raw = typeof value === 'string' ? value.trim() : ''
  if (!raw) {
    return PageEnum.BASE_HOME_ITEMS
  }
  if (raw.includes('://') || raw.startsWith('//')) {
    return PageEnum.BASE_HOME_ITEMS
  }
  return raw.startsWith('/') ? raw : `/${raw}`
}

async function handleExchange() {
  const ticket = typeof route.query.ticket === 'string' ? route.query.ticket.trim() : ''
  if (!ticket) {
    loading.value = false
    errorMessage.value = '缺少 SSO 票据'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    clearLocalStorage(StorageEnum.GO_ACCESS_TOKEN_STORE)

    const res = await ssoExchangeApi({ ticket })
    const token = res?.data?.accessToken
    if (!token) {
      throw new Error(res?.msg || '未获取到访问令牌')
    }

    setLocalStorage(StorageEnum.GO_ACCESS_TOKEN_STORE, token)
    await router.replace(redirectPath.value)
  }
  catch (error: any) {
    errorMessage.value = error?.response?.data?.msg || error?.message || '单点登录失败'
  }
  finally {
    loading.value = false
  }
}

function goLogin() {
  router.replace({
    name: PageEnum.BASE_LOGIN_NAME,
  })
}

onMounted(() => {
  handleExchange()
})
</script>

<style lang="scss" scoped>
.sso-login-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.12), transparent 32%),
    radial-gradient(circle at bottom right, rgba(99, 102, 241, 0.14), transparent 34%),
    #070b13;
}

.sso-panel {
  display: flex;
  width: min(520px, calc(100vw - 32px));
  min-height: 260px;
  align-items: center;
  justify-content: center;
  padding: 32px;
  box-sizing: border-box;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.92);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.28);
}

.sso-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
