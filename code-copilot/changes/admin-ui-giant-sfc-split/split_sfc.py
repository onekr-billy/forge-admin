#!/usr/bin/env python3
"""Split a Vue SFC over 2000 lines into Options shell + CSS + composable parts."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


def parse_imports(src: str):
    imports, i, n = [], 0, len(src)
    while i < n:
        while i < n:
            if src[i] == "\n":
                i += 1
                continue
            if src.startswith("//", i):
                while i < n and src[i] != "\n":
                    i += 1
                continue
            break
        if not src.startswith("import ", i):
            break
        start = i
        brace = paren = 0
        i += 6
        while i < n:
            ch = src[i]
            if ch == "{":
                brace += 1
            elif ch == "}":
                brace -= 1
            elif ch == "(":
                paren += 1
            elif ch == ")":
                paren -= 1
            elif ch == "\n" and brace == 0 and paren == 0:
                i += 1
                break
            i += 1
        imports.append(src[start:i])
    return imports, src[i:]


def extract_balanced(src: str, start_idx: int):
    depth = 0
    i = start_idx
    while i < len(src):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start_idx : i + 1], i + 1
        i += 1
    raise ValueError("unbalanced braces")


def rewrite_imp_for_composables(imp: str) -> str:
    def repl(m):
        q, path = m.group(1), m.group(2)
        if path.startswith("@/") or not path.startswith("."):
            return m.group(0)
        if path.startswith("./"):
            return f"from {q}../{path[2:]}{q}"
        return m.group(0)

    return re.sub(r"from\s+(['\"])([^'\"]+)\1", repl, imp)


def chunk_keys(keys, per_line=8):
    return [", ".join(keys[i : i + per_line]) for i in range(0, len(keys), per_line)]


def node_check(path: Path) -> str:
    r = subprocess.run(["node", "--check", str(path)], capture_output=True, text=True)
    return "OK" if r.returncode == 0 else r.stderr[:400]


def split_sfc(
    vue_rel: str,
    *,
    name_prefix: str,
    composable_name: str,
    apply_prefix: str,
    local_export_name: str,
    target_body: int = 1100,
) -> bool:
    root = Path("forge-admin-ui") if Path("forge-admin-ui").exists() else Path(".")
    # Allow running from repo root or forge-admin-ui
    vue_path = Path(vue_rel)
    if not vue_path.exists() and (Path("forge-admin-ui") / vue_rel).exists():
        vue_path = Path("forge-admin-ui") / vue_rel
    if not vue_path.exists() and vue_rel.startswith("src/") and (Path("forge-admin-ui") / vue_rel).exists():
        vue_path = Path("forge-admin-ui") / vue_rel

    out_dir = vue_path.parent
    comp_dir = out_dir / "composables"
    comp_dir.mkdir(exist_ok=True)
    vue = vue_path.read_text()

    tm = re.search(r"^<template>\n[\s\S]*?\n</template>\n", vue, re.M)
    sm = re.search(r"^<script\s+setup([^>]*)>\n([\s\S]*?)\n</script>\n", vue, re.M)
    if not tm:
        raise SystemExit(f"no template: {vue_path}")
    if not sm:
        # Already Options or no script setup — skip
        raise SystemExit(f"no script setup (already split?): {vue_path}")

    template = tm.group(0)
    script_attrs = sm.group(1) or ""
    script_body = sm.group(2)
    script_is_ts = "lang=\"ts\"" in script_attrs or "lang='ts'" in script_attrs

    style_bodies = []
    style_ext = ".css"
    for m in re.finditer(r"^<style([^>]*)>\n([\s\S]*?)\n</style>\s*", vue, re.M):
        attrs, body = m.group(1), m.group(2)
        if "src=" in attrs:
            continue
        if "scss" in attrs or "sass" in attrs:
            style_ext = ".scss"
        style_bodies.append(body)
    style_body = "\n".join(style_bodies)

    css_files = []
    if style_body.strip():
        lines = style_body.splitlines(True)
        if len(lines) >= 2000:
            mid = len(lines) // 2
            cut = mid
            for i in range(mid, min(mid + 120, len(lines))):
                if not lines[i].strip() or lines[i].lstrip().startswith("/*"):
                    cut = i
                    break
            names = [f"{name_prefix}-shell{style_ext}", f"{name_prefix}-panels{style_ext}"]
            (out_dir / names[0]).write_text("".join(lines[:cut]))
            (out_dir / names[1]).write_text("".join(lines[cut:]))
            css_files = names
        else:
            name = f"{name_prefix}{style_ext}"
            (out_dir / name).write_text(style_body)
            css_files = [name]

    # strip defineProps / defineEmits
    props_obj = None
    emit_list = None
    script_wo = script_body
    if "defineProps" in script_body:
        pi = script_body.find("defineProps(")
        obj_start = script_body.find("{", pi)
        props_obj, after = extract_balanced(script_body, obj_start)
        stmt = script_body.rfind("\n", 0, pi) + 1
        end = after
        if end < len(script_body) and script_body[end] == ")":
            end += 1
        if end < len(script_body) and script_body[end] == "\n":
            end += 1
        script_wo = script_body[:stmt] + script_body[end:]
    if "defineEmits" in script_wo:
        ei = script_wo.find("defineEmits(")
        bs = script_wo.find("[", ei)
        be = script_wo.find("]", bs)
        emit_list = script_wo[bs + 1 : be]
        stmt = script_wo.rfind("\n", 0, ei) + 1
        end = be + 1
        if script_wo[end : end + 1] == ")":
            end += 1
        if script_wo[end : end + 1] == "\n":
            end += 1
        script_wo = script_wo[:stmt] + script_wo[end:]

    imports, body = parse_imports(script_wo)
    body = body.lstrip("\n")
    if not body.endswith("\n"):
        body += "\n"

    import_block = "".join(rewrite_imp_for_composables(i) for i in imports)
    imp_text = "".join(imports)

    vue_comps = re.findall(r"import (\w+) from ['\"][^'\"]+\.vue['\"]", imp_text)
    named_vue = []
    for m in re.finditer(r"import \{([^}]+)\} from ['\"]@/components/[^'\"]+['\"]", imp_text):
        for part in m.group(1).split(","):
            name = part.strip().split(" as ")[-1].strip()
            if name and name[:1].isupper() and not name.startswith("use"):
                named_vue.append(name)

    tags = set(re.findall(r"</?([A-Z][A-Za-z0-9]+)", template))
    icon_m = re.search(r"import \{([^}]+)\} from '@vicons/ionicons5'", imp_text)
    icons = [x.strip() for x in icon_m.group(1).split(",") if x.strip()] if icon_m else []
    has_nicon = "NIcon" in imp_text or "NIcon" in tags
    has_drag = "from 'vuedraggable'" in imp_text
    naive_tags = sorted(
        t
        for t in tags
        if t.startswith("N") and t not in {"NaN"}
    )

    loc = [f"/** Components for {name_prefix} Options shell. */\n"]
    naive_import = []
    if has_nicon:
        naive_import.append("NIcon")
    for t in naive_tags:
        if t not in naive_import:
            naive_import.append(t)
    if naive_import:
        loc.append("import { " + ", ".join(naive_import) + " } from 'naive-ui'\n")
    if has_drag:
        loc.append("import draggable from 'vuedraggable'\n")
    if icons:
        loc.append("import {\n  " + ",\n  ".join(icons) + ",\n} from '@vicons/ionicons5'\n")
    for imp in imports:
        if ".vue" in imp:
            loc.append(imp)
    # named component imports from @/components (only .vue-looking re-exports skipped; keep AiCrudPage style)
    for m in re.finditer(r"^import \{([^}]+)\} from (['\"]@/components/[^'\"]+['\"])\n", imp_text, re.M):
        names = []
        for part in m.group(1).split(","):
            name = part.strip().split(" as ")[-1].strip()
            if name and name[:1].isupper() and not name.startswith("use"):
                names.append(name)
        if names:
            loc.append(f"import {{ {', '.join(names)} }} from {m.group(2)}\n")
            for n in names:
                if n not in named_vue:
                    named_vue.append(n)

    loc.append(f"\nexport const {local_export_name} = {{\n")
    for n in naive_import:
        loc.append(f"  {n},\n")
    if has_drag:
        loc.append("  draggable,\n")
    if icons:
        loc.append("  " + ",\n  ".join(icons) + ",\n")
    for c in vue_comps + named_vue:
        loc.append(f"  {c},\n")
    loc.append("}\n")
    local_path = out_dir / f"{name_prefix}LocalComponents.js"
    local_path.write_text("".join(loc))

    body_ind = "".join(("  " + l if l.strip() else l) for l in body.splitlines(True))
    raw_lets = re.findall(r"^  let (\w+)(?:\s*=\s*(.+))?$", body_ind, re.M)
    fn_decl = re.findall(r"^  (?:async\s+)?function (\w+)\s*\(", body_ind, re.M)
    cfn = [
        m.group(1)
        for m in re.finditer(
            r"^  const (\w+)\s*=\s*(async\s+)?(function\b|\([^)]*\)\s*=>|[A-Za-z_][\w.]*\s*=>)",
            body_ind,
            re.M,
        )
    ]
    impl = sorted(set(fn_decl) | set(cfn))

    body2 = body_ind
    for n, _ in raw_lets:
        body2 = re.sub(rf"^  let {n}\b.*$", "", body2, count=1, flags=re.M)
    for n, _ in raw_lets:
        body2 = re.sub(rf"(?<![\w.$]){n}(?![\w$])", f"mut.{n}", body2)
    body2 = body2.replace("mut.mut.", "mut.")
    body2 = re.sub(r"^  async function (\w+)\s*\(", r"  __impl.\1 = async function \1(", body2, flags=re.M)
    body2 = re.sub(r"^  function (\w+)\s*\(", r"  __impl.\1 = function \1(", body2, flags=re.M)
    for n in cfn:
        body2 = re.sub(rf"^  const {n}\s*=\s*", f"  __impl.{n} = ", body2, count=1, flags=re.M)
    body2 = re.sub(r"\n{3,}", "\n\n", body2)

    lines = body2.splitlines(True)
    cps = [0]
    acc = 0
    last = 0
    for i, line in enumerate(lines):
        acc += 1
        if re.match(r"^  (const |__impl\.|watch\(|onMounted\(|onBeforeUnmount\(|//)", line):
            last = i
        if acc >= target_body and last > cps[-1]:
            cps.append(last)
            acc = i - last
    cps.append(len(lines))
    cuts = []
    for c in cps:
        if not cuts or c > cuts[-1]:
            cuts.append(c)
    parts = ["".join(lines[cuts[i] : cuts[i + 1]]) for i in range(len(cuts) - 1)]
    n_parts = len(parts)

    def decls(t):
        return re.findall(r"^  const (\w+)\s*=", t, re.M)

    part_decls = [decls(p) for p in parts]
    let_inits = {n: (v.rstrip() if v else "undefined") for n, v in raw_lets}
    mut_init = (
        "  const mut = {\n" + ",\n".join(f"    {k}: {v}" for k, v in let_inits.items()) + ",\n  }\n"
        if let_inits
        else "  const mut = {}\n"
    )
    lazies = [f"  const {n} = (...args) => __impl.{n}(...args)" for n in impl]
    has_props = props_obj is not None

    oks = []
    for idx, pb in enumerate(parts, 1):
        fn = f"{apply_prefix}Part{idx}"
        chunks = [f"/** {vue_path.name} setup part {idx}. */\n", import_block]
        if idx == 1:
            if has_props:
                chunks.append(f"export function {fn}(props, emit) {{\n")
            else:
                chunks.append(f"export function {fn}() {{\n")
            chunks.append("  const __impl = {}\n" + mut_init + "\n".join(lazies) + "\n")
        else:
            if has_props:
                chunks.append(f"export function {fn}(props, emit, deps = {{}}) {{\n")
            else:
                chunks.append(f"export function {fn}(deps = {{}}) {{\n")
            prev = ["__impl", "mut"] + impl
            for j in range(idx - 1):
                prev.extend(part_decls[j])
            sk, seen = [], set()
            for k in prev:
                if k not in seen:
                    seen.add(k)
                    sk.append(k)
            chunks.append("  const {\n")
            for row in chunk_keys(sk):
                chunks.append(f"    {row},\n")
            chunks.append("  } = deps\n")
        chunks.append(pb)
        if idx == 1:
            ret = (["props", "emit"] if has_props else []) + ["__impl", "mut"] + impl + part_decls[0]
            chunks.append("  return {\n")
            for row in chunk_keys(ret):
                chunks.append(f"    {row},\n")
            chunks.append("  }\n}\n")
        else:
            chunks.append("  return {\n    ...deps,\n")
            if part_decls[idx - 1]:
                for row in chunk_keys(part_decls[idx - 1]):
                    chunks.append(f"    {row},\n")
            chunks.append("  }\n}\n")
        text = "".join(chunks)
        if idx == 1:
            text = re.sub(r"  const mut = \{[\s\S]*?\n  \}\n", mut_init, text, count=1)
        ext = ".ts" if script_is_ts else ".js"
        path = comp_dir / f"{composable_name}.part{idx}{ext}"
        path.write_text(text)
        status = "OK (ts skip node --check)" if script_is_ts else node_check(path)
        print(f"  {path.name}: {text.count(chr(10)) + 1} {status}")
        oks.append(status.startswith("OK"))

    ext = ".ts" if script_is_ts else ".js"
    orch = ""
    for i in range(1, n_parts + 1):
        orch += f"import {{ {apply_prefix}Part{i} }} from './{composable_name}.part{i}{ext}'\n"
    if has_props:
        orch += f"\nexport function {composable_name}(props, emit) {{\n  let api = {apply_prefix}Part1(props, emit)\n"
        for i in range(2, n_parts + 1):
            orch += f"  api = {apply_prefix}Part{i}(props, emit, api)\n"
        orch += "  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api\n  return publicApi\n}\n"
    else:
        orch += f"\nexport function {composable_name}() {{\n  let api = {apply_prefix}Part1()\n"
        for i in range(2, n_parts + 1):
            orch += f"  api = {apply_prefix}Part{i}(api)\n"
        orch += "  const { __impl, mut, ...publicApi } = api\n  return publicApi\n}\n"
    orch_path = comp_dir / f"{composable_name}{ext}"
    orch_path.write_text(orch)
    status = "OK (ts)" if script_is_ts else node_check(orch_path)
    print(f"  {orch_path.name}: {status}")

    shell = template + "\n<script>\n"
    shell += f"import {{ {local_export_name} }} from './{name_prefix}LocalComponents'\n"
    shell += f"import {{ {composable_name} }} from './composables/{composable_name}'\n\n"
    # note: Vite resolves .ts/.js automatically from extensionless import
    shell += "export default {\n"
    shell += f"  name: '{name_prefix[0].upper() + name_prefix[1:]}',\n"
    shell += f"  components: {{\n    ...{local_export_name},\n  }},\n"
    if props_obj:
        shell += f"  props: {props_obj},\n"
    if emit_list is not None:
        shell += f"  emits: [{emit_list}],\n"
    if props_obj:
        shell += f"  setup(props, {{ emit }}) {{\n    return {composable_name}(props, emit)\n  }},\n"
    else:
        shell += f"  setup() {{\n    return {composable_name}()\n  }},\n"
    shell += "}\n</script>\n\n"
    for css in css_files:
        lang_attr = ' lang="scss"' if css.endswith(".scss") else ""
        shell += f'<style scoped{lang_attr} src="./{css}"></style>\n'
    # Preserve already-external style src tags from original (e.g. todo.css)
    for m in re.finditer(r'^<style([^>]*\bsrc=[^>]*)>\s*</style>\s*$', vue, re.M):
        shell += f"<style{m.group(1)}></style>\n"
    vue_path.write_text(shell)

    print(
        f"{vue_path}: vue={shell.count(chr(10)) + 1} parts={n_parts} "
        f"css={css_files} ok={all(oks)} body={body.count(chr(10))} impl={len(impl)}"
    )
    return all(oks)


PRESETS = {
    "BusinessRelationDesigner": dict(
        vue_rel="src/views/app-center/components/designer/BusinessRelationDesigner.vue",
        name_prefix="businessRelationDesigner",
        composable_name="useBusinessRelationDesigner",
        apply_prefix="applyBusinessRelationDesigner",
        local_export_name="businessRelationDesignerLocalComponents",
    ),
    "BusinessFieldPropertyPanel": dict(
        vue_rel="src/views/app-center/components/designer/BusinessFieldPropertyPanel.vue",
        name_prefix="businessFieldPropertyPanel",
        composable_name="useBusinessFieldPropertyPanel",
        apply_prefix="applyBusinessFieldPropertyPanel",
        local_export_name="businessFieldPropertyPanelLocalComponents",
    ),
    "NodePropertiesPanel": dict(
        vue_rel="src/components/bpmn/NodePropertiesPanel.vue",
        name_prefix="nodePropertiesPanel",
        composable_name="useNodePropertiesPanel",
        apply_prefix="applyNodePropertiesPanel",
        local_export_name="nodePropertiesPanelLocalComponents",
    ),
    "AiFormItem": dict(
        vue_rel="src/components/ai-form/AiFormItem.vue",
        name_prefix="aiFormItem",
        composable_name="useAiFormItem",
        apply_prefix="applyAiFormItem",
        local_export_name="aiFormItemLocalComponents",
    ),
    "BusinessListDesigner": dict(
        vue_rel="src/views/app-center/components/designer/BusinessListDesigner.vue",
        name_prefix="businessListDesigner",
        composable_name="useBusinessListDesigner",
        apply_prefix="applyBusinessListDesigner",
        local_export_name="businessListDesignerLocalComponents",
    ),
    "login": dict(
        vue_rel="src/views/login/index.vue",
        name_prefix="loginPage",
        composable_name="useLoginPage",
        apply_prefix="applyLoginPage",
        local_export_name="loginPageLocalComponents",
    ),
    "menu": dict(
        vue_rel="src/views/system/menu.vue",
        name_prefix="menuPage",
        composable_name="useMenuPage",
        apply_prefix="applyMenuPage",
        local_export_name="menuPageLocalComponents",
    ),
    "BusinessFormDesigner": dict(
        vue_rel="src/views/app-center/components/designer/BusinessFormDesigner.vue",
        name_prefix="businessFormDesigner",
        composable_name="useBusinessFormDesigner",
        apply_prefix="applyBusinessFormDesigner",
        local_export_name="businessFormDesignerLocalComponents",
    ),
    "ForgeFormCanvasNode": dict(
        vue_rel="src/views/app-center/components/designer/forge-form-designer/ForgeFormCanvasNode.vue",
        name_prefix="forgeFormCanvasNode",
        composable_name="useForgeFormCanvasNode",
        apply_prefix="applyForgeFormCanvasNode",
        local_export_name="forgeFormCanvasNodeLocalComponents",
    ),
    "ExtensionEditorDrawer": dict(
        vue_rel="src/views/app-center/application-workspace/ExtensionEditorDrawer.vue",
        name_prefix="extensionEditorDrawer",
        composable_name="useExtensionEditorDrawer",
        apply_prefix="applyExtensionEditorDrawer",
        local_export_name="extensionEditorDrawerLocalComponents",
    ),
    "role": dict(
        vue_rel="src/views/system/role.vue",
        name_prefix="rolePage",
        composable_name="useRolePage",
        apply_prefix="applyRolePage",
        local_export_name="rolePageLocalComponents",
    ),
    "StructuredListPageDesigner": dict(
        vue_rel="src/components/lowcode-builder/page/StructuredListPageDesigner.vue",
        name_prefix="structuredListPageDesigner",
        composable_name="useStructuredListPageDesigner",
        apply_prefix="applyStructuredListPageDesigner",
        local_export_name="structuredListPageDesignerLocalComponents",
    ),
    "flowModel": dict(
        vue_rel="src/views/flow/model.vue",
        name_prefix="flowModel",
        composable_name="useFlowModel",
        apply_prefix="applyFlowModel",
        local_export_name="flowModelLocalComponents",
    ),
    "objectDesigner": dict(
        vue_rel="src/views/app-center/object-designer.[objectCode].vue",
        name_prefix="objectDesigner",
        composable_name="useObjectDesigner",
        apply_prefix="applyObjectDesigner",
        local_export_name="objectDesignerLocalComponents",
    ),
    "ForgeFormDesigner": dict(
        vue_rel="src/views/app-center/components/designer/forge-form-designer/ForgeFormDesigner.vue",
        name_prefix="forgeFormDesignerPage",
        composable_name="useForgeFormDesignerPage",
        apply_prefix="applyForgeFormDesignerPage",
        local_export_name="forgeFormDesignerPageLocalComponents",
    ),
    "BusinessActionDesigner": dict(
        vue_rel="src/views/app-center/components/designer/BusinessActionDesigner.vue",
        name_prefix="businessActionDesigner",
        composable_name="useBusinessActionDesigner",
        apply_prefix="applyBusinessActionDesigner",
        local_export_name="businessActionDesignerLocalComponents",
    ),
    "knowledgeList": dict(
        vue_rel="src/views/ai/knowledge/list.vue",
        name_prefix="knowledgeList",
        composable_name="useKnowledgeList",
        apply_prefix="applyKnowledgeList",
        local_export_name="knowledgeListLocalComponents",
    ),
    "crudGenerator": dict(
        vue_rel="src/views/ai/crud-generator.vue",
        name_prefix="crudGenerator",
        composable_name="useCrudGenerator",
        apply_prefix="applyCrudGenerator",
        local_export_name="crudGeneratorLocalComponents",
    ),
    "providerModel": dict(
        vue_rel="src/views/ai/provider-model.vue",
        name_prefix="providerModel",
        composable_name="useProviderModel",
        apply_prefix="applyProviderModel",
        local_export_name="providerModelLocalComponents",
    ),
    "crudPage": dict(
        vue_rel="src/views/ai/crud-page.vue",
        name_prefix="crudPage",
        composable_name="useCrudPageView",
        apply_prefix="applyCrudPageView",
        local_export_name="crudPageLocalComponents",
    ),
    "flowMonitor": dict(
        vue_rel="src/views/flow/monitor.vue",
        name_prefix="flowMonitor",
        composable_name="useFlowMonitor",
        apply_prefix="applyFlowMonitor",
        local_export_name="flowMonitorLocalComponents",
    ),
    "externalManage": dict(
        vue_rel="src/views/external/manage.vue",
        name_prefix="externalManage",
        composable_name="useExternalManage",
        apply_prefix="applyExternalManage",
        local_export_name="externalManageLocalComponents",
    ),
    "flowTodo": dict(
        vue_rel="src/views/flow/todo.vue",
        name_prefix="flowTodo",
        composable_name="useFlowTodo",
        apply_prefix="applyFlowTodo",
        local_export_name="flowTodoLocalComponents",
    ),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("preset", nargs="*", help="preset names or 'all'")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()
    if args.list:
        for k in PRESETS:
            print(k)
        return
    names = list(PRESETS) if args.preset == ["all"] or not args.preset else args.preset
    failed = []
    for name in names:
        if name not in PRESETS:
            print(f"unknown preset {name}", file=sys.stderr)
            failed.append(name)
            continue
        print(f"=== {name} ===")
        try:
            ok = split_sfc(**PRESETS[name])
            if not ok:
                failed.append(name)
        except Exception as e:
            print(f"FAIL {name}: {e}")
            failed.append(name)
    if failed:
        print("FAILED:", ", ".join(failed))
        sys.exit(1)
    print("ALL OK")


if __name__ == "__main__":
    main()
