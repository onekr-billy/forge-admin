# 低代码流程发起性能优化执行记录

> 本文件按执行轮次追加命令、结果与环境边界，不覆盖历史记录。

## 2026-09-20 首轮实现与验证

### 红灯基线

- 前端为运行态快照合并和延后刷新调度新增用例；实现前 20 个用例中 2 个失败，错误分别为 `mergeDocumentRuntimeSnapshot is not a function` 和 `scheduleFlowRuntimeRefresh is not a function`。
- Flow 缓存用例首次因 `buildStartConfig` 解析边界尚未存在而编译失败，符合预期红灯。

### 实现结果

- 流程发起、修改后重提和撤回在主请求成功后立即把运行态合并到当前行、拆分子表的同主记录行和当前详情。
- 页面级 loading 不再等待详情和整表刷新；后台校准在下一宏任务中并行执行，且其异常不会反向将已成功主操作报错。
- 应用级流程成功后先在当前行标记对应 `processCode` 已启动，避免后台校准前短暂继续显示发起入口。
- 前端反馈编排拆到 `useFlowActionFeedback.js`，没有继续把运行态逻辑堆入已超限的 `AiCrudPage.vue`。
- Flow 发起配置新增租户级内存缓存，TTL 30 秒、最多 512 项；使用模型 ID/版本/更新时间/BPMN XML 的 SHA-256 指纹即时识别更新。
- 同一缓存键使用 `ConcurrentHashMap.compute` 合并并发未命中，缓存读写均深拷贝 DTO，避免调用方污染共享结果。

### 自动化证据

- Node 20.19.0 下执行前端定向 Vitest：2 个测试文件、23 个用例全部通过。
- Node 20.19.0 下执行本轮 5 个 JS/Vue 文件的定向 ESLint：通过。
- JDK 17 下执行 `FlowModelServiceImplTest`：9 个用例全部通过；同命令完成 Flow 相关 reactor 编译。
- JDK 17 下执行 Flow 插件及上游模块 `compile -DskipTests`：通过。
- Node 20.19.0 下执行前端生产构建：通过，耗时约 2 分 7 秒；仅有存量 Vite native-config、CSS `//` 注释和动态导入拆包警告。
- 定向 ESLint 与 `git diff --check`：通过。

### 执行中修正

- 一次 Maven 命令误从仓库根目录使用 `forge-framework/...` reactor 路径，Maven 报“找不到选定项目”；切换到 `forge-server` 后重跑。
- 定向测试暴露存量“复制模型”用例未模拟租户限定 Mapper，补充 `tenantId` 和 `selectByIdAndTenant` 模拟后通过；生产代码未因此改变。

### 验证边界

- 检查时前端 `3000` 和旧 Flow 服务 `8081` 在运行，Admin `8580` 未运行；无法在真实低代码列表执行浏览器发起验收。
- 本轮未启动、停止或重启用户服务，未写入真实业务数据。
- 当前运行的 Flow 进程仍是改动前代码；要验证 BPMN 发起配置缓存，需重启 Flow 服务后再测。
