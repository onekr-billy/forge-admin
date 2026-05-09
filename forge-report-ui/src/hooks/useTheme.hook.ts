import { computed, toRefs } from 'vue'
import { darkTheme, GlobalThemeOverrides } from 'naive-ui'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { borderRadius } from '@/settings/designSetting'
import { alpha, lighten } from '@/utils'

/**
 * * 设置全局主题
 */
export const useThemeOverridesHook = () => {
  const designStore = useDesignStore()
  const { getAppTheme } = toRefs(designStore)
  const darkTheme = computed(
    (): GlobalThemeOverrides => {
      // 通用
      const commonObj = {
        common: {
          primaryColor: getAppTheme.value,
          primaryColorHover: lighten(alpha(getAppTheme.value), 0.1),
          primaryColorPressed: lighten(alpha(getAppTheme.value), 0.05),
          primaryColorSuppl: getAppTheme.value,
          infoColor: '#a78bfa',
          infoColorHover: '#c4b5fd',
          infoColorPressed: '#8b5cf6',
          infoColorSuppl: '#a78bfa',
          successColor: '#2ed573',
          successColorHover: '#5fe88a',
          successColorPressed: '#28c25e',
          successColorSuppl: '#2ed573',
          warningColor: '#ffa502',
          warningColorHover: '#ffbe4d',
          warningColorPressed: '#e69500',
          warningColorSuppl: '#ffa502',
          errorColor: '#ff4757',
          errorColorHover: '#ff6b7a',
          errorColorPressed: '#e63946',
          errorColorSuppl: '#ff4757',
          borderRadius,
          borderRadiusSmall: '3px',
          fontSize: '14px',
          fontSizeSmall: '12px',
          fontSizeMedium: '14px',
          fontSizeLarge: '16px',
          fontSizeHuge: '18px',
          heightSmall: '28px',
          heightMedium: '34px',
          heightLarge: '40px',
        }
      }
      // 亮色主题
      const lightObject = {
        common: {
          ...commonObj.common,
          bodyColor: '#f8fafc',
          cardColor: '#ffffff',
          modalColor: '#ffffff',
          popoverColor: '#ffffff',
          inputColor: '#f1f5f9',
          tableColor: '#ffffff',
          tableColorHover: '#f1f5f9',
          tableColorStriped: '#f8fafc',
          borderColor: '#e2e8f0',
          dividerColor: '#e2e8f0',
        }
      }
      // 暗色主题
      const dartObject = {
        common: {
          ...commonObj.common,
          bodyColor: '#0a0e17',
          cardColor: '#111827',
          modalColor: '#1a1f2e',
          popoverColor: '#1a1f2e',
          inputColor: '#1a1f2e',
          tableColor: '#111827',
          tableColorHover: '#1a1f2e',
          tableColorStriped: '#151d2b',
          borderColor: '#334155',
          dividerColor: '#334155',
          actionColor: '#1a1f2e',
          closeColorHover: '#ff4757',
          closeColorPressed: '#e63946',
        },
        LoadingBar: {
          colorLoading: getAppTheme.value
        },
        Button: {
          colorHover: getAppTheme.value,
          colorPressed: getAppTheme.value,
          colorFocus: getAppTheme.value,
          borderHover: `1px solid ${getAppTheme.value}`,
          borderFocus: `1px solid ${getAppTheme.value}`,
        },
        Input: {
          border: `1px solid #334155`,
          colorFocus: alpha(getAppTheme.value, 0.1),
          borderFocus: `1px solid ${getAppTheme.value}`,
          boxShadowFocus: `0 0 0 2px ${alpha(getAppTheme.value, 0.2)}`,
        },
        InternalSelection: {
          border: `1px solid #334155`,
          borderActive: `1px solid ${getAppTheme.value}`,
          borderFocus: `1px solid ${getAppTheme.value}`,
          boxShadowFocus: `0 0 0 2px ${alpha(getAppTheme.value, 0.15)}`,
          boxShadowActive: `0 0 0 2px ${alpha(getAppTheme.value, 0.15)}`,
        },
        Card: {
          colorEmbedded: '#0a0e17',
          borderRadius: '10px',
        },
        Tag: {
          borderRadius: '6px',
        }
      }
      return designStore.getDarkTheme ? dartObject : lightObject
    }
  )
  return darkTheme
}

// 返回暗黑主题
export const useDarkThemeHook = () => {
  const designStore = useDesignStore()
  return computed(() => (designStore.getDarkTheme ? darkTheme : undefined))
}
