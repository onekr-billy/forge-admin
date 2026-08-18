<template>
  <view class="mine-page">
    <view class="page-glow page-glow-blue" />
    <view class="page-glow page-glow-pink" />
    <view class="grid-layer" />

    <view class="mine-content">
      <view class="profile-card animate-in">
        <view class="profile-shape profile-shape-blue" />
        <view class="profile-shape profile-shape-pink" />

        <AiImageUpload
          :model-value="rawAvatarUrl"
          :fallback="DEFAULT_AVATAR_URL"
          business-type="avatar"
          @success="handleAvatarUploadSuccess"
          @upload-start="avatarUploading = true"
          @upload-end="avatarUploading = false"
        >
          <view class="avatar-frame">
            <view class="avatar-inner">
              <AiAuthImage class="avatar-image" :src="rawAvatarUrl" :fallback="DEFAULT_AVATAR_URL" mode="aspectFill" />
            </view>
            <view class="avatar-edit">
              <AiIcon icon="/static/icons/ai-icon/camera.svg" color="#ffffff" size="sm" />
            </view>
            <view v-if="avatarUploading" class="avatar-loading-mask">
              <view class="avatar-loading-spinner" />
            </view>
          </view>
        </AiImageUpload>

        <view class="profile-copy">
          <text class="profile-name">{{ authStore.displayName }}</text>
          <view class="member-badge">
            <AiIcon icon="/static/icons/ai-icon/shield.svg" color="#2563eb" size="sm" />
            <text>{{ authStore.roleText }}</text>
          </view>
        </view>

      </view>

      <view class="menu-groups">
        <view
          v-for="(group, groupIndex) in menuGroups"
          :key="groupIndex"
          class="menu-group animate-in"
          :class="`delay-${groupIndex + 2}`"
        >
          <view
            v-for="item in group.items"
            :key="item.key"
            class="menu-row"
            :class="{ 'menu-row--danger': item.danger }"
            @click="handleMenu(item)"
          >
            <view class="menu-icon" :class="item.bgClass">
              <AiIcon :icon="item.icon" :color="item.color" size="md" />
            </view>
            <view class="menu-main">
              <text class="menu-label">{{ item.label }}</text>
              <text class="menu-desc">{{ item.desc }}</text>
            </view>
            <AiIcon
              v-if="!item.danger"
              icon="/static/icons/ai-icon/chevron-right.svg"
              color="#94a3b8"
              size="sm"
            />
          </view>
        </view>
      </view>
    </view>

    <AiPopupSheet
      v-model="profileSheetVisible"
      title="个人资料"
      description="同步到当前登录账号资料"
      max-height="88vh"
      body-max-height="calc(88vh - 188rpx - env(safe-area-inset-bottom))"
    >
      <view class="sheet-form">
        <AiImageUpload
          class="avatar-large-upload"
          :model-value="rawAvatarUrl"
          :fallback="DEFAULT_AVATAR_URL"
          business-type="avatar"
          @success="handleAvatarUploadSuccess"
          @upload-start="avatarUploading = true"
          @upload-end="avatarUploading = false"
        >
          <view class="avatar-large">
            <AiAuthImage class="avatar-large-image" :src="rawAvatarUrl" :fallback="DEFAULT_AVATAR_URL" mode="aspectFill" />
            <view class="avatar-large-action">
              <AiIcon icon="/static/icons/ai-icon/camera.svg" color="#ffffff" size="sm" />
              <text>{{ avatarUploading ? '上传中' : '更换头像' }}</text>
            </view>
            <view v-if="avatarUploading" class="avatar-loading-mask avatar-loading-mask--large">
              <view class="avatar-loading-spinner" />
            </view>
          </view>
        </AiImageUpload>
        <AiField v-model="profileForm.username" label="账号" placeholder="请输入账号" clearable />
        <AiField v-model="profileForm.realName" label="姓名" placeholder="请输入姓名" clearable />
        <AiField v-model="profileForm.phone" label="手机号" type="number" placeholder="请输入手机号" clearable />
        <AiField v-model="profileForm.email" label="邮箱" type="text" placeholder="请输入邮箱" clearable />
      </view>
      <template #footer>
        <AiButton block :loading="profileSaving" @click="submitProfile">
          保存资料
        </AiButton>
      </template>
    </AiPopupSheet>

    <AiPopupSheet
      v-model="passwordSheetVisible"
      title="修改密码"
      description="修改后会退出当前登录状态"
      max-height="78vh"
      body-max-height="calc(78vh - 188rpx - env(safe-area-inset-bottom))"
    >
      <view class="sheet-form">
        <AiField v-model="passwordForm.oldPassword" label="当前密码" type="password" placeholder="请输入当前密码" clearable />
        <AiField v-model="passwordForm.newPassword" label="新密码" type="password" placeholder="至少 6 位字符" clearable />
        <AiField v-model="passwordForm.confirmPassword" label="确认密码" type="password" placeholder="再次输入新密码" clearable />
      </view>
      <template #footer>
        <AiButton block :loading="passwordSaving" @click="submitPassword">
          确认修改
        </AiButton>
      </template>
    </AiPopupSheet>

    <AiPopupSheet
      v-model="securitySheetVisible"
      title="安全中心"
      description="查看当前账号和绑定信息"
      max-height="74vh"
      body-max-height="calc(74vh - 172rpx - env(safe-area-inset-bottom))"
    >
      <view class="info-list">
        <view v-for="item in securityItems" :key="item.label" class="info-row">
          <view class="info-icon">
            <AiIcon :icon="item.icon" :color="item.color" size="sm" />
          </view>
          <view class="info-main">
            <text class="info-label">{{ item.label }}</text>
            <text class="info-value">{{ item.value }}</text>
          </view>
        </view>
      </view>
      <template #footer>
        <AiButton block variant="secondary" @click="openPasswordSheet">
          修改登录密码
        </AiButton>
      </template>
    </AiPopupSheet>

    <AiPopupSheet
      v-model="tenantSheetVisible"
      title="切换租户"
      :description="`当前租户：${currentTenantName}`"
      max-height="76vh"
      body-max-height="calc(76vh - 172rpx - env(safe-area-inset-bottom))"
    >
      <view class="tenant-panel">
        <view class="tenant-current-card">
          <view class="tenant-current-icon">
            <AiIcon icon="/static/icons/ai-icon/briefcase.svg" color="#ffffff" size="md" />
          </view>
          <view class="tenant-current-copy">
            <text class="tenant-current-label">当前工作空间</text>
            <text class="tenant-current-name">{{ currentTenantName }}</text>
          </view>
        </view>

        <AiListSkeleton v-if="tenantLoading" :rows="3" compact />

        <view v-else class="tenant-list">
          <view
            v-for="tenant in displayTenantOptions"
            :key="tenant.tenantId"
            class="tenant-row"
            :class="{
              active: isCurrentTenant(tenant),
              switching: switchingTenantId === tenant.tenantId,
            }"
            @click="handleTenantSwitch(tenant)"
          >
            <view class="tenant-row-icon">
              <AiIcon icon="/static/icons/ai-icon/layers.svg" :color="isCurrentTenant(tenant) ? '#2563eb' : '#64748b'" size="sm" />
            </view>
            <view class="tenant-row-main">
              <text class="tenant-row-name">{{ tenant.tenantName }}</text>
              <text class="tenant-row-desc">{{ isCurrentTenant(tenant) ? '正在使用' : '切换到此租户' }}</text>
            </view>
            <view v-if="switchingTenantId === tenant.tenantId" class="tenant-row-loading" />
            <AiIcon
              v-else-if="isCurrentTenant(tenant)"
              icon="/static/icons/ai-icon/check-circle.svg"
              color="#2563eb"
              size="sm"
            />
          </view>
        </view>
      </view>
      <template #footer>
        <AiButton block variant="secondary" :loading="tenantLoading" @click="loadTenantOptions">
          刷新租户列表
        </AiButton>
      </template>
    </AiPopupSheet>

    <AiPopupSheet
      v-model="settingsSheetVisible"
      title="通用设置"
      description="本机偏好，不影响其他设备"
      max-height="72vh"
      body-max-height="calc(72vh - 172rpx - env(safe-area-inset-bottom))"
    >
      <view class="setting-list">
        <view class="setting-row">
          <view class="setting-copy">
            <text class="setting-title">消息免打扰</text>
            <text class="setting-desc">开启后保留消息红点，不做本机提醒</text>
          </view>
          <switch :checked="messageQuietMode" color="#2563eb" @change="toggleQuietMode" />
        </view>
        <view class="setting-row setting-row-button" @click="clearLocalCache">
          <view class="setting-copy">
            <text class="setting-title">清理安全会话缓存</text>
            <text class="setting-desc">清理接口加密会话，不退出登录</text>
          </view>
          <AiIcon icon="/static/icons/ai-icon/trash.svg" color="#ef4444" size="sm" />
        </view>
      </view>
    </AiPopupSheet>

    <AiPopupSheet
      v-model="aboutSheetVisible"
      title="帮助与支持"
      description="移动端常用入口"
      max-height="70vh"
      body-max-height="calc(70vh - 172rpx - env(safe-area-inset-bottom))"
    >
      <view class="support-card">
        <view class="support-icon">
          <AiIcon icon="/static/icons/ai-icon/info.svg" color="#2563eb" size="lg" />
        </view>
        <text class="support-title">Forge 移动端</text>
        <text class="support-desc">支持移动端菜单、消息中心、流程待办和账号自助维护。遇到权限或页面打不开时，请先在首页刷新信息。</text>
        <view class="support-actions">
          <AiButton variant="secondary" size="sm" @click="refreshUser">
            刷新信息
          </AiButton>
          <AiButton size="sm" @click="goMessages">
            消息中心
          </AiButton>
        </view>
      </view>
    </AiPopupSheet>

    <AiTabBar active="mine" />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AiAuthImage from '@/components/AiAuthImage.vue'
import AiButton from '@/components/AiButton.vue'
import AiField from '@/components/AiField.vue'
import AiIcon from '@/components/AiIcon.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiImageUpload from '@/components/AiImageUpload.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiTabBar from '@/components/AiTabBar.vue'
import api from '@/api'
import { useAuthStore } from '@/store'
import { ensureLogin } from '@/utils/auth-guard'
import { resetKeyExchange } from '@/utils/crypto/key-exchange'
import { showConfirmDialog } from '@/utils/dialog'
import { DEFAULT_AVATAR_URL } from '@/utils/file'
import { toast } from '@/utils/notify'

const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo || {})

const rawAvatarUrl = computed(() => userInfo.value.avatar || '')

const profileSheetVisible = ref(false)
const passwordSheetVisible = ref(false)
const securitySheetVisible = ref(false)
const tenantSheetVisible = ref(false)
const settingsSheetVisible = ref(false)
const aboutSheetVisible = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const avatarUploading = ref(false)
const tenantLoading = ref(false)
const switchingTenantId = ref(null)
const messageQuietMode = ref(false)
const tenantOptions = ref([])

const profileForm = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const maskedPhone = computed(() => maskPhone(userInfo.value.phone || userInfo.value.mobile))
const maskedEmail = computed(() => maskEmail(userInfo.value.email))
const currentTenantId = computed(() => userInfo.value.tenantId)
const currentTenantName = computed(() => {
  const current = tenantOptions.value.find(item => Number(item.tenantId) === Number(currentTenantId.value))
  return current?.tenantName || userInfo.value.tenantName || '当前租户'
})
const displayTenantOptions = computed(() => {
  if (tenantOptions.value.length) {
    return tenantOptions.value
  }
  if (!currentTenantId.value) {
    return []
  }
  return [{
    tenantId: currentTenantId.value,
    tenantName: userInfo.value.tenantName || '当前租户',
  }]
})
const switchableTenantCount = computed(() => {
  const ids = Array.isArray(userInfo.value.tenantIds) ? userInfo.value.tenantIds : []
  return Math.max(displayTenantOptions.value.length, ids.length)
})
const showTenantSwitch = computed(() => switchableTenantCount.value > 1)
const securityItems = computed(() => {
  const items = []
  if (userInfo.value.username) {
    items.push({
      label: '登录账号',
      value: userInfo.value.username,
      icon: '/static/icons/ai-icon/user.svg',
      color: '#2563eb',
    })
  }
  if (maskedPhone.value) {
    items.push({
      label: '绑定手机',
      value: maskedPhone.value,
      icon: '/static/icons/ai-icon/phone.svg',
      color: '#0891b2',
    })
  }
  if (maskedEmail.value) {
    items.push({
      label: '绑定邮箱',
      value: maskedEmail.value,
      icon: '/static/icons/ai-icon/mail.svg',
      color: '#7c3aed',
    })
  }
  if (authStore.roleText) {
    items.push({
      label: '角色',
      value: authStore.roleText,
      icon: '/static/icons/ai-icon/shield.svg',
      color: '#16a34a',
    })
  }
  return items
})

const menuGroups = computed(() => [
  {
    items: [
      {
        key: 'profile',
        icon: '/static/icons/ai-icon/user.svg',
        label: '个人信息',
        desc: '姓名、手机、邮箱和头像',
        color: '#3b82f6',
        bgClass: 'bg-blue',
      },
      {
        key: 'password',
        icon: '/static/icons/ai-icon/key.svg',
        label: '修改密码',
        desc: '更新当前登录密码',
        color: '#7c3aed',
        bgClass: 'bg-purple',
      },
      {
        key: 'security',
        icon: '/static/icons/ai-icon/shield.svg',
        label: '安全中心',
        desc: '账号绑定和角色信息',
        color: '#6366f1',
        bgClass: 'bg-indigo',
      },
      ...(showTenantSwitch.value
        ? [{
            key: 'tenant',
            icon: '/static/icons/ai-icon/briefcase.svg',
            label: '切换租户',
            desc: currentTenantName.value,
            color: '#2563eb',
            bgClass: 'bg-blue',
          }]
        : []),
      {
        key: 'messages',
        icon: '/static/icons/ai-icon/bell.svg',
        label: '消息中心',
        desc: '站内消息和流程提醒',
        color: '#0891b2',
        bgClass: 'bg-cyan',
      },
    ],
  },
  {
    items: [
      {
        key: 'help',
        icon: '/static/icons/ai-icon/help-circle.svg',
        label: '帮助与支持',
        desc: '移动端能力说明',
        color: '#10b981',
        bgClass: 'bg-emerald',
      },
      {
        key: 'settings',
        icon: '/static/icons/ai-icon/settings.svg',
        label: '通用设置',
        desc: '消息提醒和本机缓存',
        color: '#64748b',
        bgClass: 'bg-slate',
      },
      {
        key: 'logout',
        icon: '/static/icons/ai-icon/log-out.svg',
        label: '退出登录',
        desc: '清除当前登录态',
        color: '#ef4444',
        bgClass: 'bg-rose',
        danger: true,
      },
    ],
  },
])

onShow(async () => {
  hideNativeTabBar()
  messageQuietMode.value = uni.getStorageSync('forge_h5_quiet_mode') === '1'
  const ok = await ensureLogin({ redirect: '/pages/mine/index' })
  if (!ok) {
    return
  }
  if (!authStore.menus.length && !authStore.permissions.length) {
    await authStore.fetchAccessSnapshot()
  }
  loadTenantOptions({ silent: true })
})

function hideNativeTabBar() {
  if (typeof uni === 'undefined' || typeof uni.hideTabBar !== 'function') {
    return
  }
  uni.hideTabBar({
    animation: false,
    fail: () => {},
  })
}

function handleMenu(item) {
  const actionMap = {
    profile: openProfileSheet,
    password: openPasswordSheet,
    security: () => { securitySheetVisible.value = true },
    tenant: openTenantSheet,
    messages: goMessages,
    help: () => { aboutSheetVisible.value = true },
    settings: () => { settingsSheetVisible.value = true },
    logout: handleLogout,
  }
  actionMap[item.key]?.()
}

function goMessages() {
  uni.navigateTo({ url: '/pages/message/index' })
}

function openProfileSheet() {
  syncProfileForm()
  profileSheetVisible.value = true
}

function openPasswordSheet() {
  securitySheetVisible.value = false
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordSheetVisible.value = true
}

async function openTenantSheet() {
  tenantSheetVisible.value = true
  await loadTenantOptions()
}

function syncProfileForm() {
  profileForm.username = userInfo.value.username || ''
  profileForm.realName = userInfo.value.realName || userInfo.value.nickName || ''
  profileForm.phone = userInfo.value.phone || userInfo.value.mobile || ''
  profileForm.email = userInfo.value.email || ''
}

async function submitProfile() {
  if (!profileForm.username.trim()) {
    toast('请输入账号', { type: 'warning' })
    return
  }
  profileSaving.value = true
  try {
    await saveProfile({
      username: profileForm.username.trim(),
      realName: profileForm.realName.trim(),
      phone: profileForm.phone.trim(),
      email: profileForm.email.trim(),
      avatar: userInfo.value.avatar || '',
    })
    profileSheetVisible.value = false
    toast('资料已更新', { type: 'success' })
  }
  catch (error) {
    console.error('保存资料失败:', error)
  }
  finally {
    profileSaving.value = false
  }
}

async function saveProfile(payload) {
  const nextProfile = {
    username: payload.username ?? userInfo.value.username,
    realName: payload.realName ?? userInfo.value.realName,
    phone: payload.phone ?? userInfo.value.phone,
    email: payload.email ?? userInfo.value.email,
    avatar: payload.avatar ?? userInfo.value.avatar,
  }
  await api.updateUserProfile(nextProfile)
  authStore.patchUserInfo(nextProfile)
  await authStore.fetchUserInfo()
}

async function handleAvatarUploadSuccess(fileData) {
  const avatar = fileData?.fileId || fileData?.id || fileData?.filePath || fileData?.url || fileData
  if (!avatar) {
    toast('头像上传结果为空', { type: 'error' })
    return
  }
  try {
    await saveProfile({ avatar })
    toast('头像已更新', { type: 'success' })
  }
  catch (error) {
    console.error('保存头像失败:', error)
  }
}

async function submitPassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    toast('请输入当前密码和新密码', { type: 'warning' })
    return
  }
  if (passwordForm.newPassword.length < 6) {
    toast('新密码至少 6 位', { type: 'warning' })
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    toast('两次输入的新密码不一致', { type: 'warning' })
    return
  }
  passwordSaving.value = true
  try {
    await api.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    toast('密码已修改，请重新登录', { type: 'success' })
    await authStore.logout()
    uni.reLaunch({ url: '/pages/login/index' })
  }
  catch (error) {
    console.error('修改密码失败:', error)
  }
  finally {
    passwordSaving.value = false
  }
}

async function refreshUser() {
  try {
    await authStore.fetchUserInfo()
    await authStore.fetchAccessSnapshot()
    toast('已刷新', { type: 'success' })
  }
  catch (error) {
    console.error('刷新用户信息失败:', error)
  }
}

async function loadTenantOptions(options = {}) {
  if (!authStore.accessToken) {
    return
  }
  tenantLoading.value = true
  try {
    const res = await api.getCurrentTenantOptions()
    tenantOptions.value = normalizeTenantOptions(res?.data)
  }
  catch (error) {
    if (Number(error?.code) === 401 && !authStore.isLogin) {
      return
    }
    if (!options.silent) {
      toast('租户列表加载失败', { type: 'error' })
    }
    console.error('加载租户列表失败:', error)
  }
  finally {
    tenantLoading.value = false
  }
}

async function handleTenantSwitch(tenant) {
  const tenantId = Number(tenant?.tenantId)
  if (!tenantId || tenantId === Number(currentTenantId.value) || switchingTenantId.value) {
    return
  }
  switchingTenantId.value = tenantId
  try {
    const res = await api.switchTenant(tenantId)
    if (res?.code === 200 || res?.data !== undefined) {
      authStore.setMenus([])
      authStore.setPermissions([])
      await authStore.fetchUserInfo()
      await authStore.fetchAccessSnapshot()
      tenantSheetVisible.value = false
      await loadTenantOptions({ silent: true })
      toast(`已切换到${tenant.tenantName || '新租户'}`, { type: 'success' })
      return
    }
    toast('租户切换失败', { type: 'error' })
  }
  catch (error) {
    toast('租户切换失败', { type: 'error' })
    console.error('切换租户失败:', error)
  }
  finally {
    switchingTenantId.value = null
  }
}

function normalizeTenantOptions(list = []) {
  if (!Array.isArray(list)) {
    return []
  }
  return list
    .map((item) => {
      const tenantId = item.tenantId || item.id
      return {
        tenantId,
        tenantName: item.tenantName || item.name || `租户 ${tenantId}`,
      }
    })
    .filter(item => item.tenantId)
}

function isCurrentTenant(tenant) {
  return Number(tenant?.tenantId) === Number(currentTenantId.value)
}

function toggleQuietMode(event) {
  messageQuietMode.value = Boolean(event.detail.value)
  uni.setStorageSync('forge_h5_quiet_mode', messageQuietMode.value ? '1' : '0')
  toast(messageQuietMode.value ? '已开启免打扰' : '已关闭免打扰', { type: 'success' })
}

function clearLocalCache() {
  resetKeyExchange()
  toast('安全会话缓存已清理', { type: 'success' })
}

async function handleLogout() {
  const confirmed = await showConfirmDialog({
    title: '退出登录',
    description: '确认退出当前账号？退出后需要重新登录。',
    icon: 'warning',
    confirmText: '退出登录',
    cancelText: '取消',
    isDestructive: true,
  })
  if (!confirmed) {
    return
  }
  await authStore.logout()
  uni.reLaunch({ url: '/pages/login/index' })
}

function maskPhone(value) {
  const phone = String(value || '').trim()
  if (!phone) {
    return ''
  }
  if (phone.length < 7) {
    return phone
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function maskEmail(value) {
  const email = String(value || '').trim()
  if (!email) {
    return ''
  }
  const [name, domain] = email.split('@')
  if (!domain) {
    return email
  }
  return `${name.slice(0, 2)}***@${domain}`
}
</script>

<style lang="scss" scoped>
.mine-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  box-sizing: border-box;
  background: #f8fafc;
}

.mine-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 8%, rgba(147, 197, 253, 0.34), transparent 32%),
    radial-gradient(circle at 86% 34%, rgba(199, 210, 254, 0.3), transparent 34%),
    radial-gradient(circle at 26% 84%, rgba(251, 207, 232, 0.24), transparent 32%);
}

.grid-layer {
  position: absolute;
  inset: 0;
  opacity: 0.16;
  pointer-events: none;
  background-image:
    linear-gradient(#e2e8f0 1rpx, transparent 1rpx),
    linear-gradient(90deg, #e2e8f0 1rpx, transparent 1rpx);
  background-size: 80rpx 80rpx;
}

.page-glow {
  position: absolute;
  width: 520rpx;
  height: 520rpx;
  border-radius: 999rpx;
  filter: blur(90rpx);
  pointer-events: none;
}

.page-glow-blue {
  top: -180rpx;
  left: -160rpx;
  background: rgba(147, 197, 253, 0.42);
  animation: floatGlow 13s ease-in-out infinite;
}

.page-glow-pink {
  right: -180rpx;
  bottom: 160rpx;
  background: rgba(251, 207, 232, 0.36);
  animation: floatGlow 15s ease-in-out infinite reverse;
}

.mine-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: 56rpx 28rpx 190rpx;
  box-sizing: border-box;
}

.profile-card {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  align-items: center;
  margin-top: 8rpx;
  padding: 42rpx 36rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 44rpx;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 20rpx 60rpx rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(28rpx);
}

.profile-shape {
  position: absolute;
  border-radius: 999rpx;
  pointer-events: none;
}

.profile-shape-blue {
  top: -84rpx;
  right: -80rpx;
  width: 260rpx;
  height: 260rpx;
  background: rgba(191, 219, 254, 0.5);
  filter: blur(36rpx);
}

.profile-shape-pink {
  bottom: -76rpx;
  left: -58rpx;
  width: 220rpx;
  height: 220rpx;
  background: rgba(251, 207, 232, 0.52);
  filter: blur(32rpx);
}

.avatar-frame {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  width: 156rpx;
  height: 156rpx;
  padding: 8rpx;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #3b82f6, #4f46e5);
  box-shadow: 0 16rpx 38rpx rgba(59, 130, 246, 0.28);
}

.avatar-inner {
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 4rpx solid #ffffff;
  border-radius: 38rpx;
  background: linear-gradient(135deg, #dbeafe, #e0e7ff);
}

.avatar-image,
.avatar-large-image {
  width: 100%;
  height: 100%;
}

.avatar-large-upload {
  align-self: center;
}

.avatar-edit {
  position: absolute;
  right: -4rpx;
  bottom: -4rpx;
  display: flex;
  width: 48rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  border: 4rpx solid #ffffff;
  border-radius: 999rpx;
  background: #2563eb;
  box-shadow: 0 10rpx 22rpx rgba(37, 99, 235, 0.24);
}

.avatar-loading-mask {
  position: absolute;
  inset: 8rpx;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 38rpx;
  background: rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(8rpx);
}

.avatar-loading-mask--large {
  inset: 0;
  border-radius: 46rpx;
}

.avatar-loading-spinner {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.34);
  border-top-color: #ffffff;
  border-radius: 999rpx;
  animation: avatarSpin 0.78s linear infinite;
}

.profile-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 24rpx;
}

.profile-name,
.member-badge text,
.stat-value,
.stat-label,
.menu-label,
.menu-desc,
.quick-item text,
.info-label,
.info-value,
.setting-title,
.setting-desc,
.tenant-current-label,
.tenant-current-name,
.tenant-row-name,
.tenant-row-desc,
.tenant-loading-card text,
.support-title,
.support-desc {
  display: block;
}

.profile-name {
  max-width: 560rpx;
  overflow: hidden;
  color: #1e293b;
  font-size: 42rpx;
  font-weight: 950;
  line-height: 1.18;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-badge {
  display: flex;
  align-items: center;
  gap: 8rpx;
  max-width: 560rpx;
  margin-top: 14rpx;
  padding: 8rpx 18rpx;
  border: 1rpx solid rgba(147, 197, 253, 0.5);
  border-radius: 16rpx;
  background: linear-gradient(90deg, #eff6ff, #eef2ff);
  box-shadow: 0 6rpx 16rpx rgba(37, 99, 235, 0.08);
}

.member-badge text {
  overflow: hidden;
  color: #1d4ed8;
  font-size: 22rpx;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-groups {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.menu-group {
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 36rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8rpx 28rpx rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(24rpx);
}

.menu-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx;
  border-bottom: 1rpx solid rgba(226, 232, 240, 0.62);
}

.menu-row:last-child {
  border-bottom: 0;
}

.menu-icon {
  position: relative;
  display: flex;
  width: 74rpx;
  height: 74rpx;
  flex: 0 0 74rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 22rpx;
}

.menu-icon::before {
  content: '';
  position: absolute;
  inset: 0;
}

.bg-blue::before {
  background: #eff6ff;
}

.bg-indigo::before {
  background: #eef2ff;
}

.bg-purple::before {
  background: #f5f3ff;
}

.bg-cyan::before {
  background: #ecfeff;
}

.bg-emerald::before {
  background: #ecfdf5;
}

.bg-slate::before {
  background: #f1f5f9;
}

.bg-rose::before {
  background: #fff1f2;
}

.menu-main {
  min-width: 0;
  flex: 1;
}

.menu-label {
  overflow: hidden;
  color: #334155;
  font-size: 29rpx;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-row--danger .menu-label {
  color: #ef4444;
}

.menu-desc {
  overflow: hidden;
  margin-top: 6rpx;
  color: #94a3b8;
  font-size: 23rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.avatar-large {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  overflow: hidden;
  align-self: center;
  border: 6rpx solid #ffffff;
  border-radius: 52rpx;
  background: #dbeafe;
  box-shadow: 0 18rpx 42rpx rgba(37, 99, 235, 0.18);
}

.avatar-large-action {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 10rpx;
  background: rgba(15, 23, 42, 0.62);
}

.avatar-large-action text {
  color: #ffffff;
  font-size: 20rpx;
  font-weight: 800;
}

.info-list,
.setting-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.info-row,
.setting-row,
.tenant-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 104rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.8);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.72);
  box-sizing: border-box;
}

.info-icon {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  flex: 0 0 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  background: #f8fafc;
}

.info-main,
.setting-copy {
  min-width: 0;
  flex: 1;
}

.info-label,
.setting-desc {
  color: #94a3b8;
  font-size: 22rpx;
  font-weight: 700;
}

.info-value,
.setting-title {
  overflow: hidden;
  margin-top: 6rpx;
  color: #1e293b;
  font-size: 28rpx;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setting-row {
  justify-content: space-between;
}

.setting-row-button {
  cursor: pointer;
}

.tenant-panel {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.tenant-current-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20rpx;
  overflow: hidden;
  padding: 26rpx;
  border: 1rpx solid rgba(147, 197, 253, 0.28);
  border-radius: 30rpx;
  background:
    radial-gradient(circle at 94% 12%, rgba(125, 211, 252, 0.26), transparent 34%),
    linear-gradient(135deg, #1d4ed8, #2563eb 58%, #0f766e);
  box-shadow: 0 14rpx 36rpx rgba(37, 99, 235, 0.18);
}

.tenant-current-icon {
  display: flex;
  width: 76rpx;
  height: 76rpx;
  flex: 0 0 76rpx;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.2);
}

.tenant-current-copy {
  min-width: 0;
  flex: 1;
}

.tenant-current-label {
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
  font-weight: 750;
}

.tenant-current-name {
  overflow: hidden;
  margin-top: 8rpx;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-loading-card {
  display: flex;
  min-height: 132rpx;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  border: 1rpx dashed rgba(147, 197, 253, 0.72);
  border-radius: 28rpx;
  background: rgba(239, 246, 255, 0.74);
}

.tenant-loading-card text {
  color: #2563eb;
  font-size: 25rpx;
  font-weight: 850;
}

.tenant-loading-spinner,
.tenant-row-loading {
  width: 34rpx;
  height: 34rpx;
  border: 4rpx solid rgba(37, 99, 235, 0.18);
  border-top-color: #2563eb;
  border-radius: 999rpx;
  animation: avatarSpin 0.78s linear infinite;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.tenant-row {
  min-height: 112rpx;
  border-color: rgba(226, 232, 240, 0.78);
  background: rgba(255, 255, 255, 0.78);
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.tenant-row.active {
  border-color: rgba(37, 99, 235, 0.32);
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.98), rgba(238, 242, 255, 0.86));
}

.tenant-row.switching {
  opacity: 0.78;
}

.tenant-row-icon {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  flex: 0 0 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  background: #f8fafc;
}

.tenant-row.active .tenant-row-icon {
  background: #dbeafe;
}

.tenant-row-main {
  min-width: 0;
  flex: 1;
}

.tenant-row-name {
  overflow: hidden;
  color: #1e293b;
  font-size: 28rpx;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-row-desc {
  overflow: hidden;
  margin-top: 6rpx;
  color: #94a3b8;
  font-size: 22rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-row.active .tenant-row-desc {
  color: #2563eb;
}

.setting-title {
  margin-top: 0;
}

.setting-desc {
  margin-top: 8rpx;
}

.support-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36rpx 28rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.8);
  border-radius: 32rpx;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  text-align: center;
}

.support-icon {
  display: flex;
  width: 96rpx;
  height: 96rpx;
  align-items: center;
  justify-content: center;
  border-radius: 28rpx;
  background: #eff6ff;
}

.support-title {
  margin-top: 22rpx;
  color: #1e293b;
  font-size: 34rpx;
  font-weight: 950;
}

.support-desc {
  margin-top: 14rpx;
  color: #64748b;
  font-size: 25rpx;
  font-weight: 650;
  line-height: 1.65;
}

.support-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.animate-in {
  animation: enterUp 0.58s ease both;
}

.delay-1 {
  animation-delay: 0.08s;
}

.delay-2 {
  animation-delay: 0.16s;
}

.delay-3 {
  animation-delay: 0.24s;
}

@keyframes enterUp {
  from {
    opacity: 0;
    transform: translateY(28rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-glow,
.grid-layer,
.profile-shape {
  display: none;
}

.mine-page,
.mine-page::before {
  background: var(--page-bg);
}

.profile-card,
.menu-group {
  border-color: var(--border-color);
  background: #fff;
  box-shadow: none;
}

.profile-card::before,
.profile-card::after {
  display: none;
}

.member-badge,
.menu-icon {
  background: #e8f3ff !important;
  box-shadow: none;
}

.animate-in {
  animation: none;
}

@keyframes floatGlow {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(36rpx, 28rpx, 0) scale(1.08);
  }
}

@keyframes avatarSpin {
  to {
    transform: rotate(360deg);
  }
}
</style>
