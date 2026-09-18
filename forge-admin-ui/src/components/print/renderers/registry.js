import PrintBarcode from './PrintBarcode.vue'
import PrintImage from './PrintImage.vue'
import PrintQrcode from './PrintQrcode.vue'
import PrintShape from './PrintShape.vue'
import PrintTable from './PrintTable.vue'
import PrintText from './PrintText.vue'

export const printRenderers = Object.freeze({
  TEXT: PrintText,
  IMAGE: PrintImage,
  LINE: PrintShape,
  RECTANGLE: PrintShape,
  PAGE_NUMBER: PrintText,
  BARCODE: PrintBarcode,
  QRCODE: PrintQrcode,
  TABLE: PrintTable,
})
