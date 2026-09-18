# 技术规划

> 状态：implementing；M1 已开始，后续模块名称仍为规划，进度以 tasks/execution-log 为准。
>
> 范围依据：[spec.md](spec.md)；执行拆解：[tasks.md](tasks.md)

## 1. 分层与依赖

### 前端

| 拟新增目录 | 职责 |
|---|---|
| `forge-admin-ui/src/components/print/protocol/` | 模板版本、结构校验、字段绑定、单位换算 |
| `forge-admin-ui/src/components/print/engine/` | 资源准备、测量、分页、页码回填、输出树 |
| `forge-admin-ui/src/components/print/renderers/` | 文本/图片/表格/二维码等无编辑行为的渲染器 |
| `forge-admin-ui/src/components/print/designer/` | 画布、选中框、拖拽、属性面板、工具栏 |
| `forge-admin-ui/src/components/print/runtime/` | 模板选择、分页预览、浏览器打印及错误定位 |
| `forge-admin-ui/src/stores/print/` | `printDesignerStore.js`、`printRuntimeStore.js` |
| `forge-admin-ui/src/api/print.js` | 类型明确的 API 封装，沿用 request/加密策略 |
| `forge-admin-ui/src/views/print/` | 模板列表、独立全屏设计路由、运行预览路由 |

渲染器由设计预览和真实打印共用。打印画布使用 mm 物理布局；设计缩放通过外层 transform，不能改变模板坐标。跨面板通信通过 Pinia，页面仅提供入口上下文。

协议与绑定函数不依赖 Vue；测量器依赖浏览器 DOM；分页器只消费标准测量结果，便于用可预测测量结果验证算法。最终分页效果还必须用真实浏览器验证，jsdom 不承担字体排版验证。

### 后端

新增 `forge-server/forge-framework/forge-plugin-parent/forge-plugin-print/`，包名 `com.mdframe.forge.plugin.print`。

- print 插件依赖技术 starter；拥有模板/版本/绑定/执行审计，以及 `PrintDataProvider` SPI。
- generator 插件依赖 print 插件，提供低代码和流程业务适配器。print 不反向依赖 generator。
- 代码业务模块按需实现 SPI；首个采购示例使用独立适配器，复用既有 Service/代码表单 Provider 的受控读取能力。
- Admin 聚合装配；Flow 引擎继续管理审批，不承担纸张渲染。流程信息通过现有 FlowClient/业务流程服务读取。
- 应用发布服务可以调用 print 的版本校验/引用服务；print 数据服务调用 SPI 时避免形成 Bean 环。适配器依赖只读业务能力，不依赖应用发布编排。

## 2. 平台模板协议 v1

### 身份与结构

顶层固定属性采用明确模型：`protocol`、`schemaVersion`、`paper`、`header`、`body`、`footer`、`resources`。模板身份/归属、绑定、权限与发布状态保存于平台元数据，不混入可编辑画布 JSON 充当授权依据。

| 模型 | 核心字段 |
|---|---|
| paper | widthMm、heightMm、orientation、四边 marginMm |
| header/footer | heightMm、repeat、elements |
| fixed section | id、kind=FIXED、heightMm、elements；内部绝对坐标 |
| text section | id、kind=TEXT、绑定/固定文本、字体/行距、段间距、keepWithNext |
| table section | id、kind=TABLE、collectionPath、columns、headerRows、repeatHeader、footer |
| element | id、type、xMm、yMm、widthMm、heightMm、binding、format、style |
| binding | source=FIELD/CONSTANT/SYSTEM、path/value；不接受可执行表达式 |

后端使用显式文档/节点 DTO，必要的受控样式属性白名单化；前端以同一协议约束校验。数据库可保存 JSON，但 Controller 固定字段不使用任意 Map 接收。

`body` 是有序区块。复杂单据头可以放入 fixed section；后续明细表和长文本按实际高度顺排，避免自由绝对定位元素在动态表格增长后互相覆盖。

字段示例：`main.orderNo`、`main.departmentName`、`children.items`、`flow.history`、`system.generatedAt`、`system.pageNumber`。字段目录由提供方返回，模板只可绑定目录中允许的路径。拒绝原型链路径/越界路径。

模板导入只支持本协议 v1；格式错误、未知版本、未知元素、未授权字段、资源超限应定位到具体区块/元素。模板导出包含结构与逻辑资源引用，不携带业务正文和长期文件访问凭证。

## 3. 测量与分页算法

1. 准备经过授权和格式化的打印上下文，解析模板允许的字段。
2. 固定纸张和字体，等待 `document.fonts.ready` 及图片解码；失败则返回可定位错误。
3. 在隔离测量容器中，以输出相同 CSS/宽度测量文本行、表格单元格和区块高度；mm 与 CSS px 使用标准换算，保持小数精度。
4. 扣除页边距和重复页眉/页脚，得到正文可用矩形。
5. 顺序放置区块。固定区块放不下时整体换页；文本按测得行切片续页；表格每页先预留表头再放整行，末页放合计。
6. 遇到空表、最后一行恰好装满、合计需单独换页时使用显式规则，避免多出空白页。超高行/固定区块返回 `ELEMENT_TOO_TALL`，不无限循环。
7. 得到总页数后填入固定宽度页码槽；如果影响布局需要有限次重排，超出次数返回失败。
8. 产生不可变 `PrintLayoutResult`，包含 pages、elementFragments、warnings、templateVersion、generatedAt。预览/打印都消费它。

打印 DOM 采用单独 iframe/隔离容器，复制必要的可信样式和已准备资源；每页明确 page-break，浏览器不再对业务块做第二次任意分页。打印对话框的纸张、缩放 100%、关闭浏览器额外页眉页脚列入人工验收指引。

M1 落地规则：显式字体使用本地 FontFace 加载校验，系统通用族保留浏览器字体回退；罕见字符缺字按人工验收检查。页眉/页脚不重复时，各页仍保留一致的正文矩形。`keepWithNext` 保持整个当前区块与后继首片，连续设置形成约束链，超出单页返回错误。系统页码绑定仅用于固定文本槽或 PAGE_NUMBER 元素，流式文本与编码不接受页码绑定，避免总页数引起无界重排。

撤销重做只记录用户命令，不记录鼠标每个移动事件；拖拽期间展示临时位置，结束时提交一次。删除区块/撤销恢复的元素 ID 必须稳定。

## 4. 统一打印上下文

### 请求身份

`PrintPrepareDTO` 固定字段：templateId、applicationId、pageId/formKey、objectCode、recordId、scene、taskId、processInstanceId、processRunId。只有对应场景所需字段必填，服务端重建其它关联；租户/用户取登录上下文。

客户端不得传入 SQL、任意数据源 URL、任意 provider Bean 名，不能用前端 row 替代服务端读取。模板版本由应用发布快照/节点策略解析；设计预览可以显式指定草稿，但需要设计权，真实样本另需记录读取权。

### 提供方 SPI

拟提供：

- `catalog(AuthorizedPrintSource)`：返回带类型、路径、格式化提示的允许字段目录。
- `authorize(PrintContextRequest)`：检查应用/对象/记录/流程身份，生成不可由客户端伪造的服务端上下文。
- `load(AuthorizedPrintContext, PrintBindingSelection)`：仅读取并组装需要的授权数据。

实现可以组合读取/授权服务；禁止把“页面上看得到按钮”作为安全前提。代码业务提供方只接收已解析场景并再校验业务权限。

输出 `PrintContextVO`：模板版本、main、children、flow.history、system、受控资源引用、warnings、dataMode=CURRENT、generatedAt。动态业务字段可以 Map 表达；固定身份和流程字段使用明确 DTO/VO。

### 低代码适配

- 读取已发布模型及页面/表单元数据，生成同一份字段目录。
- 复用 DynamicCrudService 的数据范围、解密/公式/翻译/脱敏链路；补齐子表列权限与展示转换的验证。
- 主子表不同返回形态在适配层归一化；模板不直接依赖数据库列名。
- 字典/关联标签优先复用服务端权威值。金额格式化采用定点规则，ID 保持字符串。
- 模板绑定字段变更时，在应用发布检查里报告失效路径；运行态使用固定发布模型，不用最新草稿猜字段。

### 流程适配

- 从 taskId/instanceId/runId 解析并核验 objectCode/recordId，禁止请求随意拼接其它业务记录。
- 待办、已办、我发起分别进行访问验证；历史只读接口不作为天然授权证据。
- 节点表单权限与业务字段权限取交集，子表列显式过滤。
- 时间轴按实例取得真实任务记录，顺序稳定；保留 taskId，允许同一节点多次出现和多人会签。
- 签名继续使用文件存储权限；打印资源完成准备后再允许输出。

## 5. 存储设计

所有表包含 id、tenant_id、create_by/create_time/create_dept、update_by/update_time，并显式声明逻辑删除字段。

| 表 | 专属字段与约束 |
|---|---|
| sys_print_template | application_id、template_code/name、source_type/source_key、draft_schema、draft_revision、design_status、published_version_id、status、del_flag BIGINT；唯一 `(tenant_id, application_id, template_code, del_flag)` |
| sys_print_template_version | template_id、version_no、schema_version、schema_json、schema_hash、resource_manifest、publish_time、del_flag；版本号跨历史永久唯一 `(tenant_id, template_id, version_no)`，不因删除复用 |
| sys_print_binding | application_id、page_id/form_key、object_code、template_id、scene、is_default、sort_order、status、binding_revision、del_flag BIGINT；规范化 source_key，唯一 `(tenant_id, application_id, source_key, template_id, scene, del_flag)` |
| sys_print_execution | template_version_id、application_version_id、object_code、record_id、process_instance_id/run_id、actor、data_mode、generated_at、result、error_code、page_count、del_flag；不存单据正文 |

来源身份使用结构化字段及服务端规范化 source_key，不能让客户端自行构造唯一范围。默认模板唯一性在同一绑定范围事务锁内维护。

模板 JSON 使用 TEXT/LONGTEXT 按体积上限校验；发布时稳定序列化求 hash。资源只保存文件引用/字体定义，不把 token 或未经限制的 data URI 塞进 schema。

逻辑删除及引用保护按根规则实施。日志留存清理不在首期，不新增物理删除任务。

### 状态规则

- 设计状态 `DRAFT -> PUBLISHED -> CHANGED -> PUBLISHED`；编辑已发布模板只改变草稿。
- 启停字段独立使用 EnableStatus；停用阻止 prepare，不删除已发布版本。
- 发布成功新增版本并更新 published_version_id，CAS 校验 draft_revision；校验失败不改变已发布引用。
- 执行结果使用专用枚举：`PREPARED`、`DIALOG_OPENED`、`FAILED`。客户端 DIALOG_OPENED 仅为报告事件，不证明物理打印成功。
- 对外“删除”仅在引用校验通过后执行逻辑删除。

## 6. 拟定接口

基础路径 `/print`，最终落位前核实前端代理/统一响应约定；所有写接口为明确 DTO，返回 RespInfo。

| 方法/路径 | DTO 或用途 | 访问条件 |
|---|---|---|
| GET `/templates/page` | pageNum/pageSize、应用筛选 | view + 应用设计可见范围 |
| GET `/templates/:id` | 元数据及草稿 | view + 应用范围 |
| POST `/templates` | PrintTemplateCreateDTO | manage |
| PUT `/templates/:id` | PrintTemplateUpdateDTO，含 revision | manage + CAS |
| DELETE `/templates/:id` | 引用保护/逻辑删除 | manage |
| POST `/templates/:id/copy` | PrintTemplateCopyDTO | 来源可见 + 目标应用 manage |
| POST `/templates/:id/publish` | PrintTemplatePublishDTO，含 revision/hash | publish + 发布校验 |
| PUT `/templates/:id/status` | PrintTemplateStatusDTO | manage |
| GET `/templates/:id/versions` | 不可变版本列表 | view |
| GET/PUT `/bindings` | PrintBindingQueryDTO/PrintBindingSaveDTO | 所属应用设计权限 + manage |
| POST `/catalog` | PrintCatalogQueryDTO | 场景对应的设计/运行权限 |
| POST `/available` | PrintAvailableTemplatesDTO | execute + 场景/记录访问权；不返回草稿 |
| POST `/prepare` | PrintPrepareDTO | execute + 模板/记录/流程/字段权限 |
| POST `/executions/:id/events` | PrintExecutionEventDTO | 执行人/租户一致 + 可接受事件 |

prepare 对每次请求重新授权；即使知道旧版本 ID 也不能绕过停用/发布绑定。执行事件不接受正文、不更改业务数据。失败返回具体错误码和元素/字段定位，日志不打印敏感内容。

## 7. 工作台、流程与发布接入

- 应用内新增“打印模板”资源入口，表单上下文进入时自动带 source identity；管理层仍允许按应用查看模板。
- 设计器独立全屏路由；普通操作以“保存草稿/预览/发布”呈现，低频操作收纳。
- 列表行/详情优先通过既有 action 配置投影为受控打印路由，不向 AiCrudPage 巨型文件追加实现。
- 流程入口使用共享详情壳/表单面板的正式上下文，不能从 DOM、组件内部代理或全局变量猜当前任务。
- `FlowTaskDetailShell.vue` 已超过 800 行，接入前拆分时间轴/样式；如果仍需触达 `todo.vue` 等超 2000 行 SFC，先完成独立拆分并回归审批动作。
- 节点打印策略通过现有节点面板保存进节点扩展配置，随 BPMN 版本保留。增加的是打印选择策略，不变更审批动作。
- 应用发布将 printBindings 与 templateVersions 纳入候选快照/校验/hash，失败不产生半发布引用；回滚恢复对应清单。
- 代码下载携带打印协议/模板定义/绑定版本和运行时依赖；原生成器继续保持共用运行协议，不增加另一套解释器。

## 8. 验证与上线边界

首期桌面目标为实际验收机器上的 Chromium 系浏览器，另列待验证的浏览器/打印机。手机可查看预览，但不承诺移动端模板编辑和静默打印。

单位换算/分页/状态/权限用针对性测试；真实浏览器验证字体、分页位置与资源加载；应用/流程真实联调及打印机验收由用户回填。完整矩阵见 test-spec.md。

迁移和权限资源只在编码阶段生成。上线时模板为空，不自动生成真实业务样本或给全员开权限。回退停用入口和模板、回滚应用版本，保留所有用户设计与历史记录。

## M2 实施落位（2026-09-19）

- `stores/print/printDesignerStore.js` 是模板、选中项、缩放、预览开关、错误和历史的共享来源。props 只在工作台入口加载协议/字段目录，内部面板直接使用 store。
- `commands.js` 负责毫米几何命令，`history.js` 保存最多 50 次文档历史。pointermove 不入历史，pointerup 原子提交，取消/卸载恢复手势前状态；字段目录与业务预览上下文不进入撤销记录。
- 编辑器内部固定带使用 header/footer，正文使用 `section:<协议 ID>`，避免合法导入 ID 与固定带重名；序列化协议不增加内部属性。
- Canvas/SelectionOverlay/CanvasElement 与 drag/resize/keyboard 拆分；Palette/FieldTree/SectionList 提供物料、受控字段、顺序编辑；六个小属性面板含表头横向合并拆分与合计绑定。
- `draftStorage.js` 校验导入体积和协议、只存模板。`PrintDesigner` 支持注入 `saveDraft(document)`，M3 接口就绪后替换默认本地适配器。`views/print/designer.vue` 提供页面组件与路由离开保护；菜单种子留给 M3。
- 当前一个页面只挂载一个 PrintDesigner；验证入口专用独立 Pinia。每次加载模板清空旧选择/历史/剪贴板，保存通过 generation 避免异步回调污染后来加载的文档。
