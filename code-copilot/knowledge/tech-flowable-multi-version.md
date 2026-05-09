# Flowable 多版本并发运行机制

> 来源：变更 flow-model-version-management  
> 时间：2026-05-09

## 问题描述
流程模型部署后，Flowable 会创建新的流程定义版本（VERSION_ 自增）。如何在运行时选择正确的版本，以及如何处理版本升级后的兼容性问题？

## 解决方案

### Flowable 版本机制
- 每次部署同一 processDefinitionKey 的流程，Flowable 自动 VERSION_ + 1
- 新发起的流程实例默认使用最新版本（VERSION_ 最大值）
- 运行中的实例继续使用其启动时的版本

### 多版本并发场景
```
流程A-VERSION_1：正在运行 10 个实例（旧流程）
流程A-VERSION_2：已部署，新发起的流程使用此版本
流程A-VERSION_3：测试版本，尚未发布
```

### 版本选择策略
```java
// 场景1：发起新流程（使用最新发布版本）
ProcessInstance pi = runtimeService.startProcessInstanceByKey(
  processDefinitionKey,
  businessKey,
  variables
);

// 场景2：发起指定版本流程（测试/回退）
ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
  .processDefinitionKey(processDefinitionKey)
  .processDefinitionVersion(targetVersion)
  .singleResult();
ProcessInstance pi = runtimeService.startProcessInstanceById(
  pd.getId(),
  businessKey,
  variables
);

// 场景3：查询运行实例（跨版本）
List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
  .processDefinitionKey(processDefinitionKey)
  .list();
```

### 兼容性回退策略
| 变化类型 | 兼容性 | 处理方式 |
|---------|--------|---------|
| 修改节点名称 | ✅ 兼容 | 无需特殊处理 |
| 新增节点 | ✅ 兼容 | 运行实例自动跳过新节点 |
| 删除节点 | ⚠️ 警告 | 运行实例可能卡在已删除节点 |
| 修改连线条件 | ❌ 风险 | 可能导致流程路径错误 |
| 修改变量名 | ❌ 风险 | 运行实例变量不匹配 |

### 推荐实践
1. **灰度发布**：新版本先标记为"测试"，验证后再发布
2. **版本隔离**：关键流程升级时，暂停旧版本发起，等待实例完成
3. **回退机制**：保留历史版本 XML，必要时可重新部署
4. **变量兼容**：变量名变更时，保留旧变量名映射

## 相关文件
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/FlowModelService.java
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowModelServiceImpl.java