export function printStyle(style = {}) {
  return {
    boxSizing: 'border-box',
    fontFamily: style.fontFamily || 'Arial, sans-serif',
    fontSize: `${style.fontSizePt ?? 10}pt`,
    fontWeight: style.fontWeight ?? 400,
    fontStyle: style.fontStyle || 'normal',
    lineHeight: style.lineHeight ?? 1.4,
    textAlign: style.textAlign || 'left',
    textDecoration: style.textDecoration || 'none',
    color: style.color || '#000000',
    backgroundColor: style.backgroundColor || 'transparent',
    border: `${style.borderWidthMm ?? 0}mm solid ${style.borderColor || '#000000'}`,
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
    ...printStyle(element.style),
  }
}

export function cellStyle(style = {}) {
  return printStyle({ paddingMm: 1, borderWidthMm: 0.15, ...style })
}
