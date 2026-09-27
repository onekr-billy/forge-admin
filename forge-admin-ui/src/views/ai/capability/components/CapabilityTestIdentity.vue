<template>
  <div class="identity-panel">
    <h3>先准备调用身份</h3><p class="intro">
      {{ requiresSubjectToken ? '此能力以真实用户身份执行，需要接入系统密钥和用户身份凭据。' : '使用接入系统的密钥认证，业务权限仍由服务端校验。' }}
    </p>
    <div class="credential-help">
      <span>{{ state.credentialAutoFilled ? '已带入当前浏览器会话中的凭据。关闭后清除本次输入，不保存到磁盘。' : '请粘贴创建接入系统时保存的密钥。已有密钥无需重新生成；确实丢失时，再由管理员处理轮换。' }}</span><n-button text type="primary" @click="emit('manage')">
        管理接入系统
      </n-button>
    </div>
    <div class="identity-grid">
      <div><label for="test-auth-mode">认证方式</label><n-select v-model:value="state.authMode" :options="authOptions" :input-props="{ id: 'test-auth-mode' }" /></div>
      <div><label for="test-credential">{{ credentialLabel }} <em>必填</em></label><n-input v-model:value="state.credential" type="password" show-password-on="click" :placeholder="credentialPlaceholder" :input-props="{ id: 'test-credential', autocomplete: 'new-password' }" /></div>
      <template v-if="requiresSubjectToken">
        <div class="wide">
          <label>用户身份来源</label><n-radio-group v-model:value="state.subjectTokenMode" class="identity-source-options" aria-label="用户身份来源">
            <n-radio v-if="state.guide?.userAssertionEnabled" value="USER_ASSERTION">
              接入系统签名
            </n-radio><n-radio value="OIDC">
              已有身份令牌
            </n-radio>
          </n-radio-group><p class="hint">
            不是当前后台的登录 Token；该用户还需具备能力所需业务权限。
          </p>
        </div>
        <div v-if="state.subjectTokenMode === 'OIDC'" class="wide">
          <label for="test-subject-token">受信 OIDC 身份令牌 <em>必填</em></label><n-input v-model:value="state.subjectToken" type="password" show-password-on="click" placeholder="粘贴真实用户的受信 OIDC JWT" :input-props="{ id: 'test-subject-token', autocomplete: 'off' }" />
        </div>
        <template v-else>
          <div><label for="test-subject">外部用户标识 <em>必填</em></label><n-input v-model:value="state.userAssertionSubject" placeholder="已在接入系统中绑定的用户标识" :input-props="{ id: 'test-subject', autocomplete: 'off' }" /></div>
          <div v-if="state.guide?.userAssertionMappingMode === 'VERIFIED_PHONE'">
            <label for="test-phone">已验证手机号（首次匹配时填写）</label><n-input v-model:value="state.userAssertionPhone" placeholder="仅用于租户内唯一匹配" :input-props="{ id: 'test-phone', autocomplete: 'off' }" />
          </div>
          <div class="wide">
            <label for="test-private-key">用户签名私钥 <em>必填</em></label><n-input v-model:value="state.userAssertionPrivateKey" :type="showPrivateKey ? 'textarea' : 'password'" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="粘贴一次性保存的 PKCS#8 PEM 私钥" :input-props="{ id: 'test-private-key', autocomplete: 'off' }" /><n-button text size="tiny" @click="showPrivateKey = !showPrivateKey">
              {{ showPrivateKey ? '隐藏私钥' : '展开核对格式' }}
            </n-button>
          </div>
          <details class="wide">
            <summary>高级身份设置（可选）</summary><label for="test-org">执行组织 ID</label><n-input v-model:value="state.userAssertionOrgId" placeholder="留空使用该用户默认组织" :input-props="{ id: 'test-org' }" /><p class="hint">
              kid {{ state.guide?.userAssertionKeyId }} · audience {{ state.guide?.userAssertionAudience }}
            </p>
          </details>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useCapabilityOnlineTestStore } from '@/stores/capability/onlineTestStore'

defineProps({ authOptions: Array, credentialLabel: String, credentialPlaceholder: String, requiresSubjectToken: Boolean })
const emit = defineEmits(['manage'])
const state = useCapabilityOnlineTestStore()
const showPrivateKey = ref(false)
</script>

<style scoped>
h3 {
  margin: 0;
  font-size: 15px;
}
.intro,
.hint {
  margin: 6px 0 16px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.7;
}
.credential-help {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  background: var(--bg-secondary);
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 12px;
  color: var(--text-secondary);
}
.credential-help span {
  flex: 1;
  min-width: 200px;
  line-height: 1.7;
}
.identity-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.identity-grid > div {
  min-width: 0;
}
.wide {
  grid-column: 1 / -1;
}
label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
em {
  font-style: normal;
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: 6px;
}
.hint {
  margin-bottom: 0;
  overflow-wrap: anywhere;
}
summary {
  cursor: pointer;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 12px;
}
.identity-source-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  min-height: 34px;
  align-items: center;
}
@media (max-width: 640px) {
  .identity-grid {
    grid-template-columns: 1fr;
  }
  .wide {
    grid-column: auto;
  }
}
</style>
