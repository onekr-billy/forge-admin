/**
 * Common print-safe fonts for the designer (system + widely available CJK).
 * Values omit quotes so protocol validation (`fontFamily`) accepts them.
 */
export const PRINT_FONT_OPTIONS = [
  { label: '微软雅黑', value: 'Microsoft YaHei, sans-serif' },
  { label: '黑体', value: 'SimHei, Heiti SC, sans-serif' },
  { label: '宋体', value: 'SimSun, Songti SC, serif' },
  { label: '楷体', value: 'KaiTi, Kaiti SC, serif' },
  { label: '仿宋', value: 'FangSong, STFangsong, serif' },
  { label: '苹方', value: 'PingFang SC, sans-serif' },
  { label: '冬青黑体', value: 'Hiragino Sans GB, sans-serif' },
  { label: '华文黑体', value: 'STHeiti, sans-serif' },
  { label: '华文楷体', value: 'STKaiti, serif' },
  { label: '华文宋体', value: 'STSong, serif' },
  { label: 'Arial', value: 'Arial, sans-serif' },
  { label: 'Helvetica', value: 'Helvetica, Arial, sans-serif' },
  { label: 'Times New Roman', value: 'Times New Roman, Times, serif' },
  { label: 'Georgia', value: 'Georgia, serif' },
  { label: 'Verdana', value: 'Verdana, sans-serif' },
  { label: 'Tahoma', value: 'Tahoma, sans-serif' },
  { label: 'Courier New', value: 'Courier New, Courier, monospace' },
]

export const DEFAULT_PRINT_FONT = 'Microsoft YaHei, sans-serif'

export function printFontLabel(value) {
  if (!value)
    return '微软雅黑'
  const hit = PRINT_FONT_OPTIONS.find(item => item.value === value || item.value.startsWith(value) || value.startsWith(item.value.split(',')[0]))
  return hit?.label || value.split(',')[0].trim()
}
