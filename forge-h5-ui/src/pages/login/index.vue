<template>
  <view class="login-page">
    <view class="noise-layer" />
    <view class="grid-layer" />
    <view class="mesh-layer mesh-layer-a" />
    <view class="mesh-layer mesh-layer-b" />
    <view class="mesh-layer mesh-layer-c" />

    <!-- 企微免登 pending 时全屏 loading，不展示登录表单 -->
    <view v-if="wecomPending" class="page-shell wecom-loading-shell">
      <view class="wecom-loading-content">
        <image class="wecom-loading-logo" :src="assetUrl('/static/logo.png')" mode="aspectFit" />
        <text class="wecom-loading-text">正在登录...</text>
        <view class="wecom-loading-dots">
          <view class="dot dot-1" />
          <view class="dot dot-2" />
          <view class="dot dot-3" />
        </view>
      </view>
    </view>

    <view v-else class="page-shell">
      <view class="brand-bar">
        <view class="brand-main">
          <view class="brand-mark">
            <image class="brand-logo" :src="assetUrl('/static/logo.png')" mode="aspectFit" />
          </view>
          <view class="brand-copy">
            <text class="brand-title">FORGE 移动端</text>
            <text class="brand-subtitle">移动端模板</text>
          </view>
        </view>
        <text class="client-badge">{{ userClient.toUpperCase() }}</text>
      </view>

      <view class="login-panel">
        <view class="panel-head">
          <view class="panel-seal">
            <image class="panel-logo" :src="assetUrl('/static/logo.png')" mode="aspectFit" />
          </view>
          <text class="panel-title">{{ title }}</text>
          <text class="panel-subtitle">使用您的企业账号登录</text>
        </view>

        <view class="form-stack">
          <view class="field">
            <image class="field-icon" :src="assetUrl('/static/icons/ai-icon/user.svg')" mode="aspectFit" />
            <input
              v-model="form.username"
              class="field-input"
              placeholder="请输入用户名"
              placeholder-class="field-placeholder"
              confirm-type="next"
            />
          </view>

          <view class="field">
            <image class="field-icon" :src="assetUrl('/static/icons/ai-icon/lock.svg')" mode="aspectFit" />
            <input
              v-model="form.password"
              class="field-input"
              :password="!showPassword"
              placeholder="请输入密码"
              placeholder-class="field-placeholder"
              confirm-type="done"
              @confirm="handleLogin"
            />
            <view class="password-toggle" @click.stop="togglePassword">
              <image
                class="toggle-icon"
                :src="assetUrl(showPassword ? '/static/icons/ai-icon/eye-off.svg' : '/static/icons/ai-icon/eye.svg')"
                mode="aspectFit"
              />
            </view>
          </view>

          <view class="captcha-row">
            <view class="field captcha-input-wrap">
              <image class="field-icon" :src="assetUrl('/static/icons/ai-icon/shield.svg')" mode="aspectFit" />
              <input
                v-model="form.code"
                class="field-input"
                placeholder="请输入验证码"
                placeholder-class="field-placeholder"
                confirm-type="done"
                @confirm="handleLogin"
              />
            </view>
            <view class="captcha-image" :class="{ 'captcha-refreshing': captcha.loading }" @click="loadCaptcha">
              <image v-if="captcha.image" class="captcha-img" :src="captcha.image" mode="aspectFit" />
              <text v-else class="captcha-empty">{{ captcha.loading ? '加载中' : '刷新' }}</text>
              <view class="captcha-lines" />
              <view class="captcha-hover">
                <image class="refresh-icon" :src="assetUrl('/static/icons/ai-icon/refresh-cw.svg')" mode="aspectFit" />
              </view>
            </view>
          </view>
        </view>

        <button class="login-button" :disabled="loading" @click="handleLogin">
          <text>{{ loading ? '登录中...' : '登录' }}</text>
          <text class="button-arrow">→</text>
        </button>

      </view>

      <view class="login-foot">
        <text>© 2026 FORGE 移动端</text>
        <text class="foot-dot" />
        <text>{{ requestPrefix }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useAuthStore } from '@/store'
import api from '@/api'
import { resolveStaticUrl } from '@/utils/assets'
import { notify, toast } from '@/utils/notify'
import { getWeComAutoLoginPromise, isWeComAutoLoginPending } from '@/utils/wecom'

function normalizeLoginError(error) {
  const rawMessage = error?.message || error?.error?.message || error?.error?.msg || '登录失败，请稍后重试'
  return String(rawMessage)
    .replace(/^com\.[\w.$]+Exception:\s*/i, '')
    .replace(/^.*BusinessException:\s*/i, '')
    .trim() || '登录失败，请稍后重试'
}

export default {
  data() {
    return {
      title: import.meta.env.VITE_TITLE || 'Forge 移动端',
      userClient: import.meta.env.VITE_USER_CLIENT || 'app',
      requestPrefix: import.meta.env.VITE_REQUEST_PREFIX || '/',
      redirect: '/pages/index/index',
      loading: false,
      wecomPending: false,
      showPassword: false,
      captcha: {
        loading: false,
        image: '',
        codeKey: '',
      },
      form: {
        username: '',
        password: '',
        code: '',
      },
    }
  },
  onLoad(options = {}) {
    this.redirect = options.redirect ? decodeURIComponent(options.redirect) : '/pages/index/index'
    const authStore = useAuthStore()
    if (authStore.isLogin) {
      this.goTarget()
      return
    }
    // 企微客户端内正处于免登流程时，等待其结果，避免闪现账号密码登录表单
    if (isWeComAutoLoginPending()) {
      this.wecomPending = true
      this.loading = true
      const pending = getWeComAutoLoginPromise()
      if (pending) {
        pending.then((result) => {
          if (result?.status === 'logged-in' || authStore.isLogin) {
            this.goTarget()
            return
          }
          if (result?.status === 'redirecting') {
            return
          }
          // 免登跳过或失败：回退到常规账号密码登录
          this.wecomPending = false
          this.loading = false
          this.loadCaptcha()
        })
        return
      }
    }
    this.loadCaptcha()
  },
  methods: {
    assetUrl(path) {
      return resolveStaticUrl(path)
    },
    togglePassword() {
      this.showPassword = !this.showPassword
    },
    normalizeCaptchaImage(image) {
      if (!image) {
        return ''
      }
      return image.startsWith('data:image') ? image : `data:image/png;base64,${image}`
    },
    async loadCaptcha() {
      if (this.captcha.loading) {
        return
      }
      this.captcha.loading = true
      try {
        const res = await api.getCaptcha()
        const data = res.data || {}
        this.captcha.image = this.normalizeCaptchaImage(data.image)
        this.captcha.codeKey = data.codeKey || ''
        this.form.code = ''
      }
      catch (error) {
        console.error('获取验证码失败:', error)
        toast('验证码加载失败', { type: 'error' })
      }
      finally {
        this.captcha.loading = false
      }
    },
    goTarget() {
      const url = this.redirect || '/pages/index/index'
      const path = url.split('?')[0]
      if (path === '/pages/index/index' || path === '/pages/mine/index') {
        uni.switchTab({ url: path })
        return
      }
      uni.reLaunch({ url })
    },
    async handleLogin() {
      const username = this.form.username.trim()
      const password = this.form.password
      const code = this.form.code.trim()
      if (!username || !password || !code) {
        toast('请输入用户名、密码和验证码', { type: 'warning' })
        return
      }
      if (!this.captcha.codeKey) {
        toast('请先刷新验证码', { type: 'warning' })
        return
      }

      this.loading = true
      try {
        const authStore = useAuthStore()
        await authStore.login({
          username,
          password,
          code,
          codeKey: this.captcha.codeKey,
        })
        toast('登录成功', { type: 'success' })
        this.goTarget()
      }
      catch (error) {
        console.error('登录失败:', error)
        notify({
          title: '登录失败',
          description: normalizeLoginError(error),
          type: 'error',
          duration: 3600,
        })
        this.loadCaptcha()
      }
      finally {
        this.loading = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 32rpx;
  box-sizing: border-box;
  color: #172033;
  background: #f8fafc;
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", sans-serif;
}

.noise-layer,
.grid-layer,
.mesh-layer {
  position: absolute;
  pointer-events: none;
}

.noise-layer {
  inset: 0;
  z-index: 0;
  opacity: 0.22;
  background-image:
    linear-gradient(135deg, rgba(255, 255, 255, 0.58) 25%, transparent 25%),
    linear-gradient(225deg, rgba(255, 255, 255, 0.58) 25%, transparent 25%),
    linear-gradient(45deg, rgba(148, 163, 184, 0.1) 25%, transparent 25%),
    linear-gradient(315deg, rgba(148, 163, 184, 0.1) 25%, #f8fafc 25%);
  background-position: 18rpx 0, 18rpx 0, 0 0, 0 0;
  background-size: 36rpx 36rpx;
}

.grid-layer {
  inset: 0;
  z-index: 1;
  opacity: 0.45;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.24) 1rpx, transparent 1rpx),
    linear-gradient(90deg, rgba(148, 163, 184, 0.24) 1rpx, transparent 1rpx);
  background-size: 74rpx 74rpx;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.84), rgba(0, 0, 0, 0.24) 72%, transparent);
}

.mesh-layer {
  z-index: 2;
  filter: blur(80rpx);
  transform: translate3d(0, 0, 0);
}

.mesh-layer-a {
  top: -160rpx;
  left: -180rpx;
  width: 650rpx;
  height: 650rpx;
  border-radius: 48%;
  background: rgba(96, 165, 250, 0.32);
  animation: meshFloatA 15s ease-in-out infinite;
}

.mesh-layer-b {
  right: -180rpx;
  bottom: -170rpx;
  width: 560rpx;
  height: 560rpx;
  border-radius: 46%;
  background: rgba(129, 140, 248, 0.26);
  animation: meshFloatB 18s ease-in-out infinite;
}

.mesh-layer-c {
  top: 33vh;
  left: 50%;
  width: 720rpx;
  height: 720rpx;
  margin-left: -360rpx;
  border-radius: 42%;
  background: rgba(244, 114, 182, 0.16);
  animation: meshPulse 10s ease-in-out infinite;
}

.page-shell {
  position: relative;
  z-index: 5;
  min-height: calc(100vh - 64rpx);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 130rpx 0 94rpx;
  box-sizing: border-box;
}

.brand-bar {
  position: absolute;
  top: 10rpx;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  animation: fadeSlideDown 0.8s ease both;
}

.brand-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.brand-mark {
  position: relative;
  width: 78rpx;
  height: 78rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.88);
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10rpx 30rpx rgba(15, 23, 42, 0.07);
  backdrop-filter: blur(20rpx);
}

.brand-mark::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(219, 234, 254, 0.8), rgba(255, 255, 255, 0));
}

.brand-logo {
  position: relative;
  z-index: 1;
  width: 52rpx;
  height: 52rpx;
}

.brand-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.brand-title {
  color: #172033;
  font-size: 29rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  line-height: 1.1;
}

.brand-subtitle {
  color: #64748b;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 0;
}

.client-badge {
  flex: 0 0 auto;
  min-width: 72rpx;
  height: 48rpx;
  padding: 0 20rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  border-radius: 18rpx;
  color: #2563eb;
  font-size: 21rpx;
  font-weight: 900;
  line-height: 48rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.64);
  box-shadow: 0 8rpx 22rpx rgba(37, 99, 235, 0.08);
  backdrop-filter: blur(18rpx);
}

.login-panel {
  position: relative;
  overflow: hidden;
  width: 100%;
  max-width: 760rpx;
  margin: 0 auto;
  padding: 62rpx 46rpx 34rpx;
  box-sizing: border-box;
  border: 1rpx solid rgba(255, 255, 255, 0.9);
  border-radius: 52rpx;
  background: rgba(255, 255, 255, 0.64);
  box-shadow:
    0 34rpx 96rpx rgba(15, 23, 42, 0.08),
    inset 0 0 0 1rpx rgba(255, 255, 255, 0.56);
  backdrop-filter: blur(34rpx);
  animation: panelEnter 0.78s ease both;
}

.sweep-light {
  position: absolute;
  top: -10%;
  bottom: -10%;
  left: -54%;
  z-index: 4;
  width: 42%;
  transform: skewX(-24deg);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.72), transparent);
  animation: sweep 6s linear infinite 1.2s;
  pointer-events: none;
}

.inner-glow {
  position: absolute;
  width: 230rpx;
  height: 230rpx;
  border-radius: 40%;
  filter: blur(48rpx);
  pointer-events: none;
}

.inner-glow-a {
  top: -96rpx;
  right: -80rpx;
  background: rgba(191, 219, 254, 0.62);
}

.inner-glow-b {
  left: -98rpx;
  bottom: -90rpx;
  background: rgba(199, 210, 254, 0.58);
}

.panel-head {
  position: relative;
  z-index: 6;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 54rpx;
}

.panel-title {
  display: block;
  margin-top: 26rpx;
  color: #172033;
  font-size: 54rpx;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: 0;
  text-align: center;
}

.panel-subtitle {
  display: block;
  margin-top: 16rpx;
  color: #94a3b8;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1.3;
  text-align: center;
}

.panel-seal {
  position: relative;
  width: 118rpx;
  height: 118rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1rpx solid #eef2f7;
  border-radius: 30rpx;
  background: #ffffff;
  box-shadow: 0 14rpx 42rpx rgba(59, 130, 246, 0.15);
  animation: sealIn 0.72s cubic-bezier(0.2, 0.9, 0.32, 1.2) 0.16s both;
}

.panel-seal::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.2), rgba(37, 99, 235, 0.1));
}

.panel-logo {
  position: relative;
  z-index: 1;
  width: 72rpx;
  height: 72rpx;
}

.form-stack {
  position: relative;
  z-index: 6;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.field {
  position: relative;
  height: 98rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 4rpx 18rpx rgba(15, 23, 42, 0.03);
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
  animation: fieldEnter 0.58s ease both;
}

.field:nth-child(2) {
  animation-delay: 0.08s;
}

.field:focus-within {
  background: #ffffff;
  border-color: rgba(96, 165, 250, 0.58);
  box-shadow: 0 0 0 8rpx rgba(37, 99, 235, 0.08);
}

.captcha-input-wrap {
  flex: 1;
  min-width: 0;
}

.captcha-row {
  display: flex;
  gap: 20rpx;
  height: 98rpx;
  animation: fieldEnter 0.58s ease 0.16s both;
}

.field-icon {
  flex: 0 0 auto;
  width: 34rpx;
  height: 34rpx;
  margin-left: 28rpx;
  margin-right: 18rpx;
  opacity: 0.46;
}

.field-input {
  flex: 1;
  min-width: 0;
  height: 96rpx;
  color: #172033;
  font-size: 30rpx;
  font-weight: 500;
  line-height: 96rpx;
}

.field-placeholder {
  color: #94a3b8;
  font-weight: 500;
}

.password-toggle {
  flex: 0 0 auto;
  width: 82rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toggle-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.52;
}

.captcha-image {
  position: relative;
  flex: 0 0 204rpx;
  height: 98rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.72);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 4rpx 18rpx rgba(15, 23, 42, 0.03);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.captcha-image:active {
  transform: scale(0.96);
}

.captcha-img {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
}

.captcha-empty {
  position: relative;
  z-index: 1;
  color: #2563eb;
  font-size: 24rpx;
  font-weight: 800;
}

.captcha-lines {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(12deg, transparent 0 22%, rgba(37, 99, 235, 0.3) 23%, transparent 25% 100%),
    linear-gradient(150deg, transparent 0 34%, rgba(124, 58, 237, 0.22) 35%, transparent 38% 100%),
    radial-gradient(circle at 26% 38%, rgba(37, 99, 235, 0.38) 0 3rpx, transparent 4rpx),
    radial-gradient(circle at 72% 64%, rgba(124, 58, 237, 0.28) 0 4rpx, transparent 5rpx);
  opacity: 0.7;
  mix-blend-mode: multiply;
}

.captcha-hover {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(4rpx);
  transition: opacity 0.2s ease;
}

.captcha-image:active .captcha-hover,
.captcha-refreshing .captcha-hover {
  opacity: 1;
}

.refresh-icon {
  width: 38rpx;
  height: 38rpx;
}

.captcha-refreshing .refresh-icon {
  animation: spin 0.8s linear infinite;
}

.login-button {
  position: relative;
  z-index: 6;
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin: 44rpx 0 0;
  overflow: hidden;
  border-radius: 28rpx;
  border: 0;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 100rpx;
  background: linear-gradient(90deg, #2563eb 0%, #4f46e5 100%);
  box-shadow: 0 16rpx 44rpx rgba(79, 70, 229, 0.27);
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
  animation: fieldEnter 0.58s ease 0.22s both;
}

.login-button::before {
  content: "";
  position: absolute;
  inset: 0;
  transform: translateX(-150%) skewX(-20deg);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.24), transparent);
  animation: buttonSweep 2.8s ease-in-out infinite 1.4s;
}

.login-button::after {
  border: 0;
}

.login-button:active {
  transform: scale(0.98);
}

.button-arrow {
  display: inline-block;
  opacity: 0.78;
  animation: arrowNudge 1.45s ease-in-out infinite;
}

.login-button[disabled] {
  opacity: 0.72;
}

.panel-links {
  position: relative;
  z-index: 6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  margin-top: 34rpx;
  padding: 0 4rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 700;
  animation: fadeIn 0.62s ease 0.32s both;
}

.link-primary {
  color: #2563eb;
  font-weight: 900;
}

.login-foot {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  min-height: 36rpx;
  color: #94a3b8;
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  animation: fadeIn 0.72s ease 0.6s both;
}

.foot-dot {
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: #cbd5e1;
}

@keyframes fadeSlideDown {
  from {
    opacity: 0;
    transform: translateX(-20rpx);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes panelEnter {
  from {
    opacity: 0;
    transform: translateY(44rpx);
    filter: blur(10rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@keyframes sealIn {
  from {
    opacity: 0;
    transform: scale(0.58) rotate(-35deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotate(0);
  }
}

@keyframes fieldEnter {
  from {
    opacity: 0;
    transform: translateX(-22rpx);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(12rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes sweep {
  0% {
    left: -56%;
  }
  42%,
  100% {
    left: 118%;
  }
}

@keyframes buttonSweep {
  0%,
  48% {
    transform: translateX(-150%) skewX(-20deg);
  }
  78%,
  100% {
    transform: translateX(150%) skewX(-20deg);
  }
}

@keyframes arrowNudge {
  0%,
  100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(7rpx);
  }
}

/* 登录页保持静态、清晰和轻量，专业感来自品牌与表单层级而非装饰动画。 */
.noise-layer,
.grid-layer,
.mesh-layer,
.sweep-light,
.inner-glow,
.captcha-lines,
.captcha-hover {
  display: none;
}

.login-page {
  background: #f5f6f8;
}

.page-shell {
  justify-content: center;
}

.brand-bar,
.login-panel,
.panel-seal,
.field,
.login-button,
.login-foot {
  animation: none;
}

.login-panel {
  max-width: 680rpx;
  padding: 52rpx 40rpx 44rpx;
  border: 1rpx solid var(--border-color);
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 41, 0.05);
}

.panel-seal,
.brand-mark {
  border-radius: 12rpx;
  box-shadow: none;
  background: #fff;
}

.field,
.captcha-image {
  border-color: var(--border-color);
  border-radius: 10rpx;
  background: #fff;
  box-shadow: none;
}

.login-button {
  border-radius: 10rpx;
  background: var(--primary-color);
  box-shadow: none;
}

.login-button::before,
.button-arrow {
  display: none;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes meshFloatA {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.78;
  }
  50% {
    transform: translate(64rpx, 34rpx) scale(1.08);
    opacity: 0.92;
  }
}

@keyframes meshFloatB {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
    opacity: 0.68;
  }
  50% {
    transform: translate(-44rpx, -62rpx) scale(1.14);
    opacity: 0.86;
  }
}

@keyframes meshPulse {
  0%,
  100% {
    opacity: 0.56;
  }
  50% {
    opacity: 0.78;
  }
}

/* ===== 企微免登 loading 遮罩 ===== */
.wecom-loading-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
}

.wecom-loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32rpx;
}

.wecom-loading-logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 24rpx;
}

.wecom-loading-text {
  font-size: 32rpx;
  color: #475569;
  font-weight: 500;
}

.wecom-loading-dots {
  display: flex;
  gap: 16rpx;
}

.wecom-loading-dots .dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #6366f1;
  animation: dotBounce 1.2s infinite ease-in-out;
}

.wecom-loading-dots .dot-1 {
  animation-delay: 0s;
}

.wecom-loading-dots .dot-2 {
  animation-delay: 0.2s;
}

.wecom-loading-dots .dot-3 {
  animation-delay: 0.4s;
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
