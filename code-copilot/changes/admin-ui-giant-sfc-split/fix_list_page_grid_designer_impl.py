#!/usr/bin/env python3
"""Wire __impl + missing imports + hydrate for useListPageGridDesigner parts."""
from __future__ import annotations

import re
from pathlib import Path

BASE = Path("forge-admin-ui/src/components/lowcode-builder/page/composables")
PATHS = {i: BASE / f"useListPageGridDesigner.part{i}.js" for i in range(1, 6)}

IMPORT_BLOCKS = {
    2: """import { computed, nextTick } from 'vue'
import { businessObjectList } from '@/api/business-app'
import { mapBlocksInTree } from '../blockTree'
import {
  LIST_PAGE_GRID_COLS,
  buildGridSyncModelSchema,
  resolveListPageBlockMeta,
  syncGridLayoutWithModel,
} from '../page-schema'
import { expandPanelTypeOptions } from '../listDesignerOptions'
""",
    3: """import { nextTick } from 'vue'
import { businessObjectFields } from '@/api/business-app'
import {
  collectBlocksInTree,
  findBlockInTree,
  mapBlocksInTree,
  removeBlockFromTree,
} from '../blockTree'
import {
  LIST_PAGE_DESIGN_WIDTH,
  LIST_PAGE_GRID_COLS,
  createDefaultBlockStyle,
  createGridBlock,
  resolveListPageBlockMeta,
} from '../page-schema'
import {
  CANVAS_AUTO_SCROLL_EDGE,
  CANVAS_AUTO_SCROLL_MAX_STEP,
} from '../listDesignerOptions'
""",
    4: """import { nextTick, onBeforeUnmount, onMounted } from 'vue'
import {
  findBlockInTree,
  mapBlockSiblingsInTree,
  removeBlockFromTree,
} from '../blockTree'
import {
  LIST_PAGE_DESIGN_WIDTH,
  LIST_PAGE_GRID_COLS,
  createDefaultBlockStyle,
  createDefaultListGridLayout,
  resolveListPageBlockMeta,
} from '../page-schema'
import {
  blockEventActionOptions,
  eventTriggerOptions,
} from '../listDesignerOptions'
""",
    5: """import { nextTick } from 'vue'
import { enabledApiConfigs } from '@/api/business-app'
import { resolveWidgetRenderMode } from '@/components/lowcode-builder/shared/widget-binding-slots'
import { resolveSelectedFieldRefs } from '../fieldDrawerConfig'
import {
  isListFieldSelectable,
  resolveListFieldTitle,
} from '../page-schema'
import {
  actionBehaviorOptions,
  apiMethodOptions,
} from '../listDesignerOptions'
""",
}

CROSS_LATER = {
    2: [
        "clamp",
        "duplicateBlock",
        "normalizeDesignerLayout",
        "normalizeGridItems",
        "patchBlock",
        "patchBlockProps",
        "patchBlockStyle",
        "removeBlock",
        "reorderBlockLayer",
        "resolveCrudFieldKey",
        "resolveCrudFieldLabel",
    ],
    3: ["clamp", "doesFrameOverlapBlocks", "normalizeGridItems"],
    4: ["createActionParam"],
    5: [],
}


def collect_defs(txt: str) -> list[str]:
    return [m.group(2) for m in re.finditer(r"^(async\s+)?function\s+(\w+)\s*\(", txt, re.M)]


def find_function_end(txt: str, name: str) -> int | None:
    pattern = rf"^((?:async\s+)?function\s+{name}\s*\()"
    m = re.search(pattern, txt, re.M)
    if not m:
        return None
    brace = txt.find("{", m.end() - 1)
    depth = 0
    j = brace
    while j < len(txt):
        if txt[j] == "{":
            depth += 1
        elif txt[j] == "}":
            depth -= 1
            if depth == 0:
                return j + 1
        j += 1
    return None


def assign_impl_after_functions(txt: str, names: list[str]) -> str:
    # Insert from end to start so offsets stay valid
    inserts: list[tuple[int, str]] = []
    for name in names:
        end = find_function_end(txt, name)
        if end is None:
            print(f"  WARN: function {name} not found for __impl assign")
            continue
        assign = f"\n__impl.{name} = {name}"
        if txt[end : end + len(assign)] == assign or f"__impl.{name} = {name}" in txt[end : end + 120]:
            continue
        inserts.append((end, assign))
    for end, assign in sorted(inserts, key=lambda x: x[0], reverse=True):
        txt = txt[:end] + assign + txt[end:]
    return txt


def ensure_impl_in_deps(txt: str) -> str:
    def repl(m: re.Match) -> str:
        body = m.group(1)
        if re.search(r"\b__impl\b", body):
            return m.group(0)
        return "const {\n    __impl,\n" + body.lstrip("\n") + "} = deps"

    new, n = re.subn(r"const \{([^}]+)\} = deps", repl, txt, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("deps destructure not found/unique")
    return new


def prepend_imports(txt: str, imports: str) -> str:
    first_line = imports.split("\n", 1)[0]
    if first_line and first_line in txt[:800]:
        return txt
    if txt.startswith("/**"):
        end_comment = txt.find("*/") + 2
        if txt[end_comment:].startswith("\n"):
            end_comment += 1
        return txt[:end_comment] + imports + txt[end_comment:]
    return imports + txt


def main() -> None:
    parts = {i: PATHS[i].read_text() for i in range(1, 6)}
    defs = {i: collect_defs(parts[i]) for i in range(1, 6)}
    def_part = {n: i for i, names in defs.items() for n in names}

    p1_later = sorted(
        name
        for name, src in def_part.items()
        if src != 1 and re.search(rf"\b{re.escape(name)}\s*\(", parts[1])
    )
    CROSS_LATER[1] = p1_later
    print("part1 wrappers:", len(p1_later))

    # ----- part1 -----
    p1 = parts[1]
    if "runtime-tree-table" not in p1:
        needle = "import { provideListPageDesignerApi } from '../listPageDesignerContext'\n"
        p1 = p1.replace(
            needle,
            needle
            + "import {\n  alignSearchSchemaWithLeftTree,\n  findTreePanelProps,\n} from '@/components/lowcode-builder/shared/runtime-tree-table'\n",
            1,
        )

    marker = "provideListPageDesignerApi()\n"
    if "const __impl = {}" not in p1:
        wrapper_block = (
            "\nconst __impl = {}\n"
            + "\n".join(f"const {n} = (...args) => __impl.{n}(...args)" for n in p1_later)
            + "\n"
        )
        if marker not in p1:
            raise SystemExit("provideListPageDesignerApi marker missing")
        p1 = p1.replace(marker, marker + wrapper_block, 1)

    m = re.search(
        r"localLayout\.value = normalizeDesignerLayout\(syncGridLayoutWithModel\([\s\S]*?\{ layoutType: props\.layoutType \},\s*\)\)\n",
        p1,
    )
    if not m:
        raise SystemExit("eager localLayout init not found")
    p1 = (
        p1[: m.start()]
        + "// layout hydrated after later parts wire __impl (see hydrateInitialState)\n"
        + p1[m.end() :]
    )

    # Keep tree watch in part1 but drop immediate so __impl is ready before first run.
    tree_watch = re.search(
        r"(watch\(\s*\(\)\s*=>\s*\[selectedBlock\.value\?\.blockType, selectedTreeSourceValue\.value\],\s*"
        r"async \(\[blockType, sourceValue\]\)\s*=>\s*\{[\s\S]*?\},\s*)\{\s*immediate:\s*true,\s*\}(\s*\))",
        p1,
    )
    if tree_watch:
        p1 = p1[: tree_watch.start()] + tree_watch.group(1) + "{}" + tree_watch.group(2) + p1[tree_watch.end() :]
        print("removed immediate from tree-panel watch")
    else:
        print("WARN: tree watch not found")

    hydrate_fn = """
async function hydrateInitialState() {
  localLayout.value = normalizeDesignerLayout(syncGridLayoutWithModel(
    props.modelValue || createDefaultListGridLayout(props.modelSchema, { layoutType: props.layoutType }),
    buildGridSyncModelSchema(props.modelSchema, props.fields),
    { layoutType: props.layoutType },
  ))
  // one-shot equivalent of the former immediate tree-panel watch
  const blockType = selectedBlock.value?.blockType
  const sourceValue = selectedTreeSourceValue.value
  if (blockType === 'tree-panel') {
    await ensureTreeSourceCatalog()
    if (sourceValue)
      await loadTreeSourceFields(sourceValue)
    else
      treeSourceFields.value = []
  }
}
__impl.hydrateInitialState = hydrateInitialState

"""
    ret_idx = p1.rfind("\n  return {")
    if ret_idx < 0:
        raise SystemExit("part1 return not found")
    p1 = p1[:ret_idx] + hydrate_fn + p1[ret_idx:]
    p1 = p1.replace(
        "\n  return {\n    designerStore,",
        "\n  return {\n    __impl,\n    hydrateInitialState,\n    designerStore,",
        1,
    )
    p1 = assign_impl_after_functions(p1, defs[1])
    PATHS[1].write_text(p1)
    print("wrote part1")

    # ----- parts 2-5 -----
    for i in range(2, 6):
        txt = parts[i]
        txt = prepend_imports(txt, IMPORT_BLOCKS[i])
        txt = ensure_impl_in_deps(txt)
        m = re.search(r"\} = deps\n", txt)
        if not m:
            raise SystemExit(f"part{i}: deps end marker missing")
        names = CROSS_LATER[i]
        wrap_lines = "\n".join(f"  const {n} = (...args) => __impl.{n}(...args)" for n in names)
        if wrap_lines:
            txt = txt[: m.end()] + wrap_lines + "\n" + txt[m.end() :]
        txt = assign_impl_after_functions(txt, defs[i])
        PATHS[i].write_text(txt)
        print(f"wrote part{i}")

    orch = BASE / "useListPageGridDesigner.js"
    orch.write_text(
        """import { useListPageGridDesignerPart1 } from './useListPageGridDesigner.part1'
import { useListPageGridDesignerPart2 } from './useListPageGridDesigner.part2'
import { useListPageGridDesignerPart3 } from './useListPageGridDesigner.part3'
import { useListPageGridDesignerPart4 } from './useListPageGridDesigner.part4'
import { useListPageGridDesignerPart5 } from './useListPageGridDesigner.part5'

export function useListPageGridDesigner(props, emit, expose) {
  const d1 = useListPageGridDesignerPart1(props, emit)
  const d2 = useListPageGridDesignerPart2(props, emit, d1)
  const d3 = useListPageGridDesignerPart3(props, emit, d2)
  const d4 = useListPageGridDesignerPart4(props, emit, d3)
  const d5 = useListPageGridDesignerPart5(props, emit, d4, expose)
  d5.__impl?.hydrateInitialState?.()
  const { __impl, hydrateInitialState, ...publicApi } = d5
  return publicApi
}
"""
    )
    print("wrote orchestrator")
    print("DONE")


if __name__ == "__main__":
    main()
