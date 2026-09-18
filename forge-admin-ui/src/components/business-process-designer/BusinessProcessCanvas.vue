<script setup>
import { computed, ref } from 'vue'
import EdgeLayer from '../flow-designer/canvas/EdgeLayer.vue'
import FlowCanvas from '../flow-designer/canvas/FlowCanvas.vue'
import { layoutBusinessProcess } from './business-process-layout.js'
import {
  BUSINESS_PROCESS_NODE_DRAG_MIME,
  getBusinessProcessNodeDefinition,
} from './business-process-node-types.js'
import BusinessProcessAddNodeButton from './BusinessProcessAddNodeButton.vue'
import BusinessProcessNodeRenderer from './BusinessProcessNodeRenderer.vue'

const props = defineProps({
  schema: { type: Object, required: true },
  selectedNodeId: { type: String, default: null },
  readonly: { type: Boolean, default: false },
  palette: { type: Array, default: () => [] },
  draggingNodeType: { type: String, default: '' },
})

const emit = defineEmits(['nodeSelect', 'nodeDelete', 'canvasClick', 'insertNode'])

const DEFAULT_PALETTE = ['CONDITION', 'ACTION', 'APPROVAL', 'SUB_PROCESS']
  .map(type => ({ type, ...getBusinessProcessNodeDefinition(type) }))

const canvasRef = ref(null)
const activeDropEdgeId = ref('')
const effectivePalette = computed(() => props.palette.length ? props.palette : DEFAULT_PALETTE)

const layoutInput = computed(() => ({
  nodes: props.schema?.nodes || [],
  edges: props.schema?.edges || [],
}))

const layoutResult = computed(() => layoutBusinessProcess(layoutInput.value))

const insertionTargets = computed(() => layoutInput.value.edges.map((edge) => {
  const path = layoutResult.value.edgePaths.get(edge.id)?.points || []
  return {
    edgeId: edge.id,
    position: insertionPosition(path, edge),
  }
}).filter(target => target.position))

function handleNodeSelect(node) {
  emit('nodeSelect', node)
}

function insertionPosition(points, edge) {
  let longest = null
  for (let index = 1; index < points.length; index += 1) {
    const start = points[index - 1]
    const end = points[index]
    const length = Math.hypot(end.x - start.x, end.y - start.y)
    if (!longest || length > longest.length)
      longest = { start, end, length }
  }
  if (longest) {
    return {
      x: (longest.start.x + longest.end.x) / 2,
      y: (longest.start.y + longest.end.y) / 2,
    }
  }
  const source = layoutResult.value.nodePositions.get(edge.source)
  const target = layoutResult.value.nodePositions.get(edge.target)
  if (!source || !target)
    return null
  return {
    x: (source.x + source.width / 2 + target.x + target.width / 2) / 2,
    y: (source.y + source.height / 2 + target.y + target.height / 2) / 2,
  }
}

function handleCanvasDragOver(event) {
  if (props.readonly || !props.draggingNodeType || !insertionTargets.value.length)
    return
  event.preventDefault()
  if (event.dataTransfer)
    event.dataTransfer.dropEffect = 'copy'
  const container = canvasRef.value?.containerRef
  const rect = container?.getBoundingClientRect?.()
  if (!rect || !canvasRef.value?.screenToCanvas)
    return
  const point = canvasRef.value.screenToCanvas(event.clientX - rect.left, event.clientY - rect.top)
  activeDropEdgeId.value = nearestInsertionTarget(insertionTargets.value, point)?.edgeId || ''
}

function handleCanvasDrop(event) {
  if (props.readonly)
    return
  event.preventDefault()
  const edgeId = activeDropEdgeId.value
  const type = draggedNodeType(event) || props.draggingNodeType
  activeDropEdgeId.value = ''
  emitInsertion(edgeId, type)
}

function handleTargetDrop({ edgeId, event }) {
  const type = draggedNodeType(event) || props.draggingNodeType
  activeDropEdgeId.value = ''
  emitInsertion(edgeId, type)
}

function emitInsertion(edgeId, type) {
  if (!edgeId || !effectivePalette.value.some(item => item.type === type))
    return
  emit('insertNode', { edgeId, type })
}

function draggedNodeType(event) {
  return event?.dataTransfer?.getData(BUSINESS_PROCESS_NODE_DRAG_MIME)
    || event?.dataTransfer?.getData('text/plain')
    || ''
}

function nearestInsertionTarget(targets, point) {
  return targets.reduce((nearest, target) => {
    const distance = (target.position.x - point.x) ** 2 + (target.position.y - point.y) ** 2
    return !nearest || distance < nearest.distance ? { ...target, distance } : nearest
  }, null)
}

defineExpose({
  canvasRef,
  layoutResult,
})
</script>

<template>
  <div
    class="business-process-canvas relative h-full min-h-120 w-full overflow-hidden"
    :class="{ 'is-dragging-node': Boolean(draggingNodeType) }"
    @dragover="handleCanvasDragOver"
    @drop="handleCanvasDrop"
    @dragleave.self="activeDropEdgeId = ''"
  >
    <FlowCanvas ref="canvasRef" :readonly="readonly" @canvas-click="emit('canvasClick', $event)">
      <template #edges>
        <EdgeLayer
          :edges="layoutInput.edges"
          :paths="layoutResult.edgePaths"
          :canvas-bounds="layoutResult.canvasBounds"
          :show-labels="false"
        />
      </template>

      <template #nodes>
        <BusinessProcessNodeRenderer
          v-for="node in schema.nodes"
          :key="node.id"
          :node="node"
          :position="layoutResult.nodePositions.get(node.id)"
          :selected="selectedNodeId === node.id"
          :readonly="readonly"
          @select="handleNodeSelect"
          @delete="emit('nodeDelete', $event)"
        />

        <BusinessProcessAddNodeButton
          v-for="target in insertionTargets"
          :key="target.edgeId"
          :edge-id="target.edgeId"
          :position="target.position"
          :items="effectivePalette"
          :readonly="readonly"
          :dragging="Boolean(draggingNodeType)"
          :active="activeDropEdgeId === target.edgeId"
          @select="emit('insertNode', $event)"
          @drag-target="activeDropEdgeId = $event"
          @drop-node="handleTargetDrop"
        />
      </template>
    </FlowCanvas>
  </div>
</template>

<style scoped>
.business-process-canvas {
  background-color: var(--body-color, #f7f9fa);
  background-image: radial-gradient(circle, rgba(148, 163, 184, 0.18) 1px, transparent 1px);
  background-size: 20px 20px;
}

.business-process-canvas.is-dragging-node {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.2);
  background-color: rgba(37, 99, 235, 0.015);
}
</style>
