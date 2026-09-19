<script setup>
import { computed } from 'vue'

const props = defineProps({
  lengthMm: { type: Number, required: true },
  orientation: { type: String, default: 'horizontal' },
})

const marks = computed(() => {
  const count = Math.floor(props.lengthMm / 5)
  return Array.from({ length: count + 1 }, (_, index) => {
    const value = index * 5
    return { value, major: value % 10 === 0 }
  })
})
</script>

<template>
  <div class="print-ruler" :class="orientation" aria-hidden="true">
    <span
      v-for="mark in marks"
      :key="mark.value"
      class="ruler-mark"
      :class="{ major: mark.major }"
      :style="orientation === 'horizontal' ? { left: `${mark.value}mm` } : { top: `${mark.value}mm` }"
    >
      <i />
      <small v-if="mark.major">{{ mark.value }}</small>
    </span>
  </div>
</template>

<style scoped>
.print-ruler {
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  color: #6b7280;
  background: #f8fafc;
  user-select: none;
}
.print-ruler.horizontal {
  width: 100%;
  height: 7mm;
  border-bottom: 1px solid #cbd5e1;
}
.print-ruler.vertical {
  width: 7mm;
  height: 100%;
  border-right: 1px solid #cbd5e1;
}
.ruler-mark {
  position: absolute;
  font-family: Arial, sans-serif;
  font-size: 7px;
}
.horizontal .ruler-mark {
  bottom: 0;
  height: 100%;
}
.vertical .ruler-mark {
  right: 0;
  width: 100%;
}
.ruler-mark i {
  position: absolute;
  display: block;
  background: #94a3b8;
}
.horizontal .ruler-mark i {
  bottom: 0;
  width: 1px;
  height: 1.7mm;
}
.vertical .ruler-mark i {
  right: 0;
  width: 1.7mm;
  height: 1px;
}
.horizontal .ruler-mark.major i {
  height: 2.7mm;
  background: #64748b;
}
.vertical .ruler-mark.major i {
  width: 2.7mm;
  background: #64748b;
}
.horizontal .ruler-mark small {
  position: absolute;
  bottom: 2.8mm;
  left: 1px;
  line-height: 1;
}
.vertical .ruler-mark small {
  position: absolute;
  top: 1px;
  right: 2.9mm;
  line-height: 1;
  transform: rotate(-90deg);
  transform-origin: right top;
}
</style>
