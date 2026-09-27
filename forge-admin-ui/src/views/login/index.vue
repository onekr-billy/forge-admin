<template>
  <div class="login-container">
    <div class="login-bg-animated" />

    <div class="login-card">
      <!-- Left side - Branding -->
      <div class="login-brand">
        <div class="brand-content">
          <div class="logo-lockup">
            <img :src="brandLogoUrl" class="logo-img" alt="Logo" @error="handleBrandLogoError">
            <div class="logo-text">
              {{ brandSystemName }}
            </div>
          </div>

          <div class="banner">
            <n-carousel
              autoplay
              :interval="4200"
              show-arrow
              class="stack-carousel"
            >
              <div class="carousel-item">
                <div class="carousel-copy">
                  <div class="carousel-title">
                    开箱即用的后台模板
                  </div>
                  <div class="carousel-subtitle">
                    常见列表、表单、详情和权限场景快速落地
                  </div>
                </div>
                <img :src="loginCarouselImage" class="carousel-image" alt="后台系统能力总览">
              </div>
              <div class="carousel-item">
                <div class="carousel-copy">
                  <div class="carousel-title">
                    业务对象驱动生成
                  </div>
                  <div class="carousel-subtitle">
                    字段、操作、列表和表单配置保持一致
                  </div>
                </div>
                <img :src="loginCarouselImage" class="carousel-image" alt="业务对象驱动生成">
              </div>
              <div class="carousel-item">
                <div class="carousel-copy">
                  <div class="carousel-title">
                    权限与流程统一治理
                  </div>
                  <div class="carousel-subtitle">
                    多租户、数据权限和审批流程统一接入
                  </div>
                </div>
                <img :src="loginCarouselImage" class="carousel-image" alt="权限与流程统一治理">
              </div>
              <template #arrow="{ prev, next }">
                <button type="button" class="arco-carousel-arrow-left" title="上一页" @click="prev">
                  <i class="ai-icon:chevron-left" />
                </button>
                <button type="button" class="arco-carousel-arrow-right" title="下一页" @click="next">
                  <i class="ai-icon:chevron-right" />
                </button>
              </template>
              <template #dots="{ total, currentIndex, to }">
                <div class="arco-carousel-dots" role="tablist">
                  <button
                    v-for="index in total"
                    :key="index"
                    type="button"
                    class="arco-carousel-dot"
                    :class="{ active: currentIndex === index - 1 }"
                    :aria-selected="currentIndex === index - 1"
                    @click="to(index - 1)"
                  />
                </div>
              </template>
            </n-carousel>
          </div>
        </div>
      </div>

      <!-- Right side - Login form -->
      <div class="login-form-wrapper">
        <div class="login-form">
          <div class="form-header">
            <h2 class="form-title">
              {{ showResetForm ? '找回密码' : '登录' }}
            </h2>
            <p class="form-subtitle">
              {{ showResetForm ? resetSubtitle : loginSubtitle }}
            </p>
          </div>

          <div v-if="!showResetForm" class="form-body">
            <!-- Username -->
            <div class="form-group">
              <label for="username" class="form-label">用户名</label>
              <div class="input-wrapper">
                <n-input
                  id="username"
                  v-model:value="loginInfo.username"
                  autofocus
                  class="modern-input"
                  placeholder="请输入用户名"
                  :maxlength="20"
                  size="large"
                >
                  <template #prefix>
                    <i class="input-icon ai-icon:user" />
                  </template>
                </n-input>
              </div>
            </div>

            <!-- Password -->
            <div class="form-group">
              <label for="password" class="form-label">密码</label>
              <div class="input-wrapper">
                <n-input
                  id="password"
                  v-model:value="loginInfo.password"
                  class="modern-input"
                  type="password"
                  show-password-on="click"
                  placeholder="请输入密码"
                  :maxlength="20"
                  size="large"
                  @keydown.enter="handleLogin()"
                >
                  <template #prefix>
                    <i class="input-icon ai-icon:lock" />
                  </template>
                </n-input>
              </div>
            </div>

            <!-- 验证码区域 - 根据配置显示不同类型的验证码 -->
            <!-- Tab 切换（如果启用群二维码引流） -->
            <div v-if="captchaEnabled && groupQrcodeEnabled" class="captcha-tab-switch">
              <button
                type="button"
                class="captcha-tab-btn"
                :class="{ active: activeCaptchaTab === 'default' }"
                @click="activeCaptchaTab = 'default'"
              >
                图形验证码
              </button>
              <button
                type="button"
                class="captcha-tab-btn"
                :class="{ active: activeCaptchaTab === 'group' }"
                @click="activeCaptchaTab = 'group'"
              >
                群二维码
              </button>
            </div>

            <!-- 默认验证码（图形/滑块/短信） -->
            <div v-if="captchaEnabled && activeCaptchaTab === 'default'" class="form-group">
              <!-- 图形验证码 -->
              <template v-if="captchaType === 'graphical'">
                <label for="captcha" class="form-label">验证码</label>
                <div class="captcha-wrapper">
                  <div class="input-wrapper flex-1">
                    <n-input
                      id="captcha"
                      v-model:value="loginInfo.code"
                      class="modern-input"
                      placeholder="请输入验证码"
                      :maxlength="6"
                      size="large"
                      @keydown.enter="handleLogin()"
                    >
                      <template #prefix>
                        <i class="input-icon ai-icon:key" />
                      </template>
                    </n-input>
                  </div>
                  <div
                    class="captcha-image"
                    title="点击刷新验证码"
                    role="button"
                    tabindex="0"
                    @click="refreshCaptcha"
                    @keydown.enter="refreshCaptcha"
                  >
                    <img
                      v-if="captchaImage"
                      :src="captchaImage"
                      alt="验证码"
                      class="captcha-img"
                    >
                    <div v-else class="captcha-loading">
                      <i class="ai-icon:loader animate-spin" />
                    </div>
                  </div>
                </div>
              </template>

              <!-- 滑块验证码 - 已改为浮层弹出形式，点击登录按钮触发 -->
              <template v-if="captchaType === 'slider'">
                <div class="slider-verify-trigger" :class="{ verified: sliderSuccess }" @click="!sliderSuccess && openSliderModal()">
                  <div class="trigger-icon">
                    <i v-if="sliderSuccess" class="ai-icon:check-circle" style="color:#22C55E" />
                    <i v-else class="ai-icon:shield" />
                  </div>
                  <span>{{ sliderSuccess ? '安全验证已通过' : '点击进行安全验证' }}</span>
                  <i v-if="!sliderSuccess" class="trigger-arrow ai-icon:chevron-right" />
                </div>
              </template>

              <!-- 短信验证码 -->
              <template v-if="captchaType === 'sms'">
                <label for="phone" class="form-label">手机号</label>
                <div class="input-wrapper mb-3">
                  <n-input
                    id="phone"
                    v-model:value="loginInfo.phone"
                    class="modern-input"
                    placeholder="请输入手机号"
                    :maxlength="11"
                    size="large"
                  >
                    <template #prefix>
                      <i class="input-icon ai-icon:phone" />
                    </template>
                  </n-input>
                </div>
                <label for="smsCode" class="form-label">短信验证码</label>
                <div class="captcha-wrapper">
                  <div class="input-wrapper flex-1">
                    <n-input
                      id="smsCode"
                      v-model:value="loginInfo.code"
                      class="modern-input"
                      placeholder="请输入短信验证码"
                      :maxlength="6"
                      size="large"
                      @keydown.enter="handleLogin()"
                    >
                      <template #prefix>
                        <i class="input-icon ai-icon:key" />
                      </template>
                    </n-input>
                  </div>
                  <n-button
                    :disabled="smsCountdown > 0 || !isValidPhone"
                    class="sms-button"
                    size="large"
                    @click="sendSmsCode"
                  >
                    {{ smsCountdown > 0 ? `${smsCountdown}s后重发` : '获取验证码' }}
                  </n-button>
                </div>
              </template>
            </div>

            <!-- 群二维码验证码：独立于验证码总开关，点“获取验证码”在下方浮出二维码 -->
            <div v-if="groupQrcodeEnabled && activeCaptchaTab === 'group'" class="form-group group-qrcode-verify">
              <label for="groupCaptchaCode" class="form-label">验证码</label>
              <div class="captcha-wrapper">
                <div class="input-wrapper flex-1">
                  <n-input
                    id="groupCaptchaCode"
                    v-model:value="loginInfo.code"
                    class="modern-input"
                    placeholder="请输入验证码"
                    :maxlength="10"
                    size="large"
                    @keydown.enter="handleLogin()"
                  >
                    <template #prefix>
                      <i class="input-icon ai-icon:key" />
                    </template>
                  </n-input>
                </div>
                <button
                  type="button"
                  class="qrcode-trigger-btn"
                  :class="{ active: qrcodePopoverVisible }"
                  :aria-expanded="qrcodePopoverVisible"
                  title="点击展示群二维码"
                  @click="toggleQrcodePopover"
                >
                  <i class="i-material-symbols:qr-code-2-outline" />
                  <span>{{ qrcodePopoverVisible ? '收起' : '获取验证码' }}</span>
                </button>
              </div>
              <Transition name="qrcode-pop">
                <div v-if="qrcodePopoverVisible" class="qrcode-popover">
                  <div
                    class="qrcode-container"
                    :class="{ zoomable: !!groupQrcodeImage }"
                    :role="groupQrcodeImage ? 'button' : undefined"
                    :tabindex="groupQrcodeImage ? 0 : undefined"
                    title="点击放大二维码"
                    @click="groupQrcodeImage && (qrcodePreviewVisible = true)"
                    @keydown.enter="groupQrcodeImage && (qrcodePreviewVisible = true)"
                  >
                    <img
                      v-if="groupQrcodeImage"
                      :src="groupQrcodeImage"
                      :alt="groupQrcodeName"
                      class="qrcode-image"
                    >
                    <div v-else class="qrcode-placeholder">
                      <i class="ai-icon:image" />
                      <span>群二维码未配置</span>
                    </div>
                    <i v-if="groupQrcodeImage" class="qrcode-zoom-hint ai-icon:zoom-in" />
                  </div>
                  <div class="qrcode-info">
                    <h4 class="qrcode-title">
                      {{ groupQrcodeName }}
                    </h4>
                    <p class="qrcode-hint">
                      {{ groupQrcodeHint }}
                    </p>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Remember me -->
            <div class="form-options">
              <n-checkbox
                :checked="isRemember"
                :on-update:checked="(val) => (isRemember = val)"
              >
                <span class="checkbox-label">记住我</span>
              </n-checkbox>
              <button
                v-if="canResetPassword"
                type="button"
                class="forgot-link"
                @click="openResetForm"
              >
                忘记密码
              </button>
            </div>

            <!-- Submit button -->
            <n-button
              class="login-button"
              type="primary"
              size="large"
              :loading="loading"
              block
              @click="onLoginClick()"
            >
              <span class="button-text">登录</span>
              <i v-if="!loading" class="button-icon ai-icon:arrow-right" />
            </n-button>

            <div class="login-trust-strip">
              <span><i class="ai-icon:shield" />安全登录</span>
              <span><i class="ai-icon:layers" />租户隔离</span>
              <span><i class="ai-icon:check-circle" />操作审计</span>
            </div>

            <!-- Social login buttons -->
            <div v-if="displaySocialPlatforms.length > 0" class="social-login-section">
              <div class="social-divider">
                <span class="divider-text">其他登录方式</span>
              </div>
              <!-- Gitee 社区登录：突出展示 -->
              <button
                v-if="giteeCommunity.enabled"
                class="social-button-gitee"
                @click="handleSocialLogin('GITEE')"
              >
                <svg class="gitee-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.475 2 2 6.475 2 12a9.994 9.994 0 0 0 6.838 9.488c.5.087.687-.213.687-.476 0-.237-.013-1.024-.013-1.862-2.512.463-3.162-.612-3.362-1.175-.113-.288-.6-1.175-1.025-1.413-.35-.187-.85-.65-.013-.662.788-.013 1.35.725 1.538 1.025.9 1.512 2.338 1.087 2.912.825.088-.65.35-1.088.638-1.338-2.225-.25-4.55-1.112-4.55-4.937 0-1.088.387-1.987 1.025-2.688-.1-.25-.45-1.275.1-2.65 0 0 .837-.262 2.75 1.026a9.29 9.29 0 0 1 2.5-.338c.85 0 1.7.112 2.5.337 1.912-1.3 2.75-1.024 2.75-1.024.55 1.375.2 2.4.1 2.65.637.7 1.025 1.587 1.025 2.687 0 3.838-2.337 4.688-4.562 4.938.362.312.675.912.675 1.85 0 1.337-.013 2.412-.013 2.75 0 .262.188.574.688.474A10.016 10.016 0 0 0 22 12c0-5.525-4.475-10-10-10Z" fill="currentColor" />
                </svg>
                <span class="gitee-label">Gitee 免密登录</span>
              </button>
              <!-- 其他三方平台 -->
              <div v-if="nonGiteePlatforms.length > 0" class="social-buttons">
                <button
                  v-for="platform in nonGiteePlatforms"
                  :key="platform.platform"
                  class="social-button"
                  :title="platform.platformName"
                  @click="handleSocialLogin(platform.platform)"
                >
                  <img
                    v-if="platform.platformLogoBase64"
                    :src="`data:image/png;base64,${platform.platformLogoBase64}`"
                    :alt="platform.platformName"
                    class="social-icon"
                  >
                  <span v-else class="social-icon-letter">
                    {{ (platform.platformName || platform.platform).charAt(0) }}
                  </span>
                </button>
              </div>
            </div>
          </div>

          <div v-else class="form-body">
            <div v-if="resetPasswordChannels.length > 1" class="form-group">
              <label class="form-label">验证方式</label>
              <n-radio-group v-model:value="resetForm.channel" name="resetChannel">
                <n-radio v-if="resetPasswordChannels.includes('sms')" value="sms">
                  手机号
                </n-radio>
                <n-radio v-if="resetPasswordChannels.includes('email')" value="email">
                  邮箱
                </n-radio>
              </n-radio-group>
            </div>
            <div class="form-group">
              <label class="form-label">{{ resetAccountLabel }}</label>
              <div class="input-wrapper">
                <n-input
                  v-model:value="resetForm.account"
                  class="modern-input"
                  :placeholder="resetAccountPlaceholder"
                  size="large"
                >
                  <template #prefix>
                    <i class="input-icon" :class="resetForm.channel === 'email' ? 'ai-icon:mail' : 'ai-icon:phone'" />
                  </template>
                </n-input>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">验证码</label>
              <div class="captcha-wrapper">
                <div class="input-wrapper flex-1">
                  <n-input
                    v-model:value="resetForm.code"
                    class="modern-input"
                    placeholder="请输入验证码"
                    :maxlength="6"
                    size="large"
                  >
                    <template #prefix>
                      <i class="input-icon ai-icon:key" />
                    </template>
                  </n-input>
                </div>
                <n-button
                  :disabled="resetCountdown > 0 || resetSending || !resetAccountValid"
                  class="sms-button"
                  size="large"
                  :loading="resetSending"
                  @click="sendResetCode"
                >
                  {{ resetCountdown > 0 ? `${resetCountdown}s后重发` : '获取验证码' }}
                </n-button>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">新密码</label>
              <div class="input-wrapper">
                <n-input
                  v-model:value="resetForm.newPassword"
                  class="modern-input"
                  type="password"
                  show-password-on="click"
                  placeholder="请输入新密码"
                  :maxlength="20"
                  size="large"
                >
                  <template #prefix>
                    <i class="input-icon ai-icon:lock" />
                  </template>
                </n-input>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">确认密码</label>
              <div class="input-wrapper">
                <n-input
                  v-model:value="resetForm.confirmPassword"
                  class="modern-input"
                  type="password"
                  show-password-on="click"
                  placeholder="请再次输入新密码"
                  :maxlength="20"
                  size="large"
                >
                  <template #prefix>
                    <i class="input-icon ai-icon:lock" />
                  </template>
                </n-input>
              </div>
            </div>
            <n-button
              class="login-button"
              type="primary"
              size="large"
              :loading="resetSubmitting"
              block
              @click="submitResetPassword"
            >
              <span class="button-text">重置密码</span>
            </n-button>
            <button type="button" class="back-login-link" @click="closeResetForm">
              返回登录
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ICP备案号 -->
    <div class="icp-record">
      <span v-if="copyrightInfo">{{ copyrightInfo }}</span>
      <a v-else href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">蒙ICP备2026004895号</a>
    </div>
  </div>

  <!-- 群二维码放大预览浮层 -->
  <Transition name="modal">
    <div
      v-if="qrcodePreviewVisible"
      class="slider-modal-overlay qrcode-preview-overlay"
      role="dialog"
      aria-label="群二维码放大预览"
      @click.self="qrcodePreviewVisible = false"
    >
      <div class="qrcode-preview-modal">
        <button type="button" class="slider-modal-close" aria-label="关闭" @click="qrcodePreviewVisible = false">
          <i class="ai-icon:x" />
        </button>
        <div class="qrcode-preview-header">
          <h3>{{ groupQrcodeName }}</h3>
          <p>{{ groupQrcodeHint }}</p>
        </div>
        <img :src="groupQrcodeImage" :alt="groupQrcodeName" class="qrcode-preview-image" @click.stop>
      </div>
    </div>
  </Transition>

  <!-- 滑块验证浮层 -->
  <Transition name="modal">
    <div v-if="showSliderModal" class="slider-modal-overlay" @click.self="closeSliderModal">
      <div class="slider-modal">
        <!-- 关闭按钮 -->
        <button class="slider-modal-close" @click="closeSliderModal">
          <i class="ai-icon:x" />
        </button>

        <!-- 标题区 -->
        <div class="slider-modal-header">
          <div class="slider-modal-icon">
            <i class="ai-icon:shield" />
          </div>
          <h3 class="slider-modal-title">
            安全验证
          </h3>
          <p class="slider-modal-desc">
            请拖动滑块到正确位置，完成拼图
          </p>
        </div>

        <!-- 滑块验证组件 -->
        <div class="slider-modal-body">
          <SlideVerify
            ref="slideVerifyRef"
            :w="340"
            :h="170"
            :slider-l="42"
            :slider-r="8"
            :accuracy="8"
            :imgs="slideImages"
            :show-refresh="true"
            refresh-text="刷新"
            text="拖动滑块完成拼图"
            success-text="验证成功！"
            fail-text="验证失败，请重试"
            @success="onSlideSuccess"
            @fail="onSlideFail"
            @refresh="onSlideRefresh"
          />
        </div>

        <!-- 底部辅助文字 -->
        <p class="slider-modal-tip">
          <i class="ai-icon:info" />
          如果拖动困难，可点击刷新重试
        </p>
      </div>
    </div>
  </Transition>

  <Transition name="modal">
    <div
      v-if="showWorkspaceModal"
      class="slider-modal-overlay"
      @click.self="closeWorkspaceModal"
    >
      <div
        class="workspace-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workspace-modal-title"
      >
        <button type="button" class="slider-modal-close" aria-label="关闭" @click="closeWorkspaceModal">
          <i class="ai-icon:x" />
        </button>
        <div class="workspace-modal-header">
          <h3 id="workspace-modal-title">
            选择工作区
          </h3>
          <p>该账号可进入多个工作区，请选择后继续登录</p>
        </div>
        <div class="workspace-modal-list">
          <button
            v-for="item in tenantOptions"
            :key="item.value"
            type="button"
            class="workspace-option"
            :class="{ 'is-current': String(item.value) === String(lastUsedTenantId) }"
            :disabled="loading"
            @click="confirmWorkspace(item)"
          >
            <span class="workspace-option-copy">
              <strong>{{ item.label }}</strong>
              <small v-if="item.systemName && item.systemName !== item.label">{{ item.systemName }}</small>
            </span>
            <em v-if="String(item.value) === String(lastUsedTenantId)">上次使用</em>
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script>
import { loginPageLocalComponents } from './loginPageLocalComponents'
import { useLoginPage } from './composables/useLoginPage'

export default {
  name: 'LoginPage',
  components: {
    ...loginPageLocalComponents,
  },
  setup() {
    return useLoginPage()
  },
}
</script>

<style scoped src="./loginPage.css"></style>
