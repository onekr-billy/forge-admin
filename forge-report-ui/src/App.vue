<template>
  <n-config-provider
    :theme="darkTheme"
    :hljs="hljsTheme"
    :locale="locale"
    :date-locale="dateLocale"
    :theme-overrides="overridesTheme"
  >
    <fg-app-provider>
      <I18n></I18n>
      <router-view></router-view>
    </fg-app-provider>
  </n-config-provider>
</template>

<script lang="ts" setup>
import { onMounted } from 'vue'
import { NConfigProvider } from 'naive-ui'
import { FgAppProvider } from '@/components/FgAppProvider'
import { I18n } from '@/components/I18n'
import { useDarkThemeHook, useThemeOverridesHook, useCode, useLang } from '@/hooks'
import { setAppCssTheme } from '@/utils/style'
import { useDesignStore } from '@/store/modules/designStore/designStore'

const darkTheme = useDarkThemeHook()
const overridesTheme = useThemeOverridesHook()
const hljsTheme = useCode()
const { locale, dateLocale } = useLang()

onMounted(() => {
  setAppCssTheme(useDesignStore().getAppTheme)
})
</script>
