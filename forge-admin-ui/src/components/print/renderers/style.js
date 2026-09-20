export function printStyle(style = {}) {
  const verticalAlign = style.verticalAlign || 'top'
  const justifyContent = verticalAlign === 'middle'
    ? 'center'
    : verticalAlign === 'bottom'
      ? 'flex-end'
      : 'flex-start'
  return {
    boxSizing: 'border-box',
    display: 'flex',
    flexDirection: 'column',
    justifyContent,
    width: '100%',
    height: '100%',
    fontFamily: style.fontFamily || 'Microsoft YaHei, sans-serif',
    fontSize: `${style.fontSizePt ?? 10}pt`,
    fontWeight: style.fontWeight ?? 400,
    fontStyle: style.fontStyle || 'normal',
    lineHeight: style.lineHeight ?? 1.4,
    textAlign: style.textAlign || 'left',
    textDecoration: style.textDecoration || 'none',
    color: style.color || '#000000',
    backgroundColor: style.backgroundColor || 'transparent',
    border: `${style.borderWidthMm ?? 0}mm ${style.borderStyle || 'solid'} ${style.borderColor || '#000000'}`,
    borderRadius: `${style.borderRadiusMm ?? 0}mm`,
    padding: `${style.paddingMm ?? 0}mm`,
    margin: 0,
    whiteSpace: 'pre-wrap',
    overflowWrap: 'anywhere',
    wordBreak: 'normal',
  }
}

export function elementStyle(element) {
  return {
    position: 'absolute',
    left: `${element.xMm}mm`,
    top: `${element.yMm}mm`,
    width: `${element.widthMm}mm`,
    height: `${element.heightMm}mm`,
    transform: `rotate(${element.rotationDeg || 0}deg) scaleX(${element.flipX ? -1 : 1}) scaleY(${element.flipY ? -1 : 1})`,
    transformOrigin: 'center center',
    ...printStyle(element.style),
  }
}

export function cellStyle(style = {}) {
  const textAlign = style.textAlign || 'left'
  const base = printStyle({ paddingMm: 1, borderWidthMm: 0.15, verticalAlign: 'middle', ...style })
  return {
    ...base,
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: textAlign === 'center' ? 'center' : textAlign === 'right' ? 'flex-end' : 'flex-start',
    textAlign,
  }
}

/** 表格外框：只画上/左边，避免与单元格双边框叠粗。 */
export function tableFrameStyle(style = {}) {
  const width = style.borderWidthMm ?? 0.15
  const color = style.borderColor || '#000000'
  const borderStyle = style.borderStyle || 'solid'
  const line = width > 0 ? `${width}mm ${borderStyle} ${color}` : 'none'
  return {
    boxSizing: 'border-box',
    borderTop: line,
    borderLeft: line,
    borderRight: 'none',
    borderBottom: 'none',
  }
}

/**
 * 表格单元格：只画右/下边，与 tableFrameStyle 组成单线网格。
 * 相邻格不再叠成更粗的内线，外框与内线同粗。
 */
export function tableCellStyle(style = {}) {
  const width = style.borderWidthMm ?? 0.15
  const color = style.borderColor || '#000000'
  const borderStyle = style.borderStyle || 'solid'
  const line = width > 0 ? `${width}mm ${borderStyle} ${color}` : 'none'
  const base = cellStyle({ ...style, borderWidthMm: 0 })
  return {
    ...base,
    border: 'none',
    borderRight: line,
    borderBottom: line,
  }
}
