# 低代码流程发起运行链路性能优化测试计划

## P0 单元回归

1. Flow HTTP 客户端
   - 默认 `flowRestTemplate` 使用 `HttpComponentsClientHttpRequestFactory`。
   - 连接、读取、连接池申请超时和连接池并发参数按配置生效。
   - 自定义 `flowRestTemplate` 仍可覆盖默认实现。
2. BPMN 上下文需求
   - 无相关变量时五类需求均为 false。
   - 节点属性、候选人和条件表达式中的变量能被识别。
   - `regionCode` 与 `startUserRegionCode` 都触发区域上下文。
   - 空模型、转换失败或存在不透明委托/脚本执行点时保守返回全部需求。
   - 同一流程定义命中缓存，超过 512 个定义后缓存仍有界。
3. 启动服务集成
   - 无相关变量的模型不调用领导、区域、角色、组织查询。
   - 仅引用某类变量时只加载该类数据。
   - 调用方变量进入 Flowable 启动 Map，未因按需加载被过滤。
   - 实例使用启动前已经加载的 `processDefinitionId` 创建，不再调用按 Key 启动重载。
   - 发起人与可信 Session 用户一致时不再查询用户资料；Session 不可信时仍保留查询兜底。

## P1 构建与静态检查

- 使用 JDK 17 执行 `forge-flow-client` 定向测试。
- 使用 JDK 17 执行 `forge-plugin-flow` 上下文需求和启动服务定向测试。
- 使用 JDK 17 编译上述模块及上游依赖。
- 执行 `git diff --check`。

## 真实环境验收

- 重启 Admin 和 Flow 后，连续发起同一模型的不同单据，对比首次与后续发起耗时。
- 通过连接池指标或调试日志确认连续请求复用连接。
- 分别验证无组织变量、直属领导、区域、角色和组织路由模型的首个待办处理人不变。

## 验证边界

- 自动化验证不自动修改真实业务数据。
- 未完整启动 Admin、Flow、MySQL 和 Redis 时，不伪造端到端结果。
