# 流程模型版本管理功能
> status: done
> created: 2026-05-08
> confirmed: 2026-05-08
> complexity: 🟡中等

## 1. 背景与目标

### 为什么做
现有流程管理功能缺少完善的版本管理能力，导致以下问题：
1. **版本信息分散**：版本号存储在 FlowModel 表，历史版本依赖 Flowable ProcessDefinition，两者未统一管理
2. **历史关联丢失**：每次发布后，旧的 `deploymentId` 被新值覆盖，无法追溯某个版本对应的 BPMN XML
3. **缺少变更追溯**：无法记录版本变更人、变更时间、变更说明等关键信息
4. **无法版本回退**：发布错误版本后，无法快速恢复到历史稳定版本，需重新设计流程

### 做完后效果
完成后可验证以下能力：
1. **版本历史完整存储**：每个版本包含完整的 BPMN XML、表单配置、变更说明、发布时间、发布人
2. **版本可视化对比**：用户可查看任意两个版本的差异（新增节点、修改连线、删除配置）
3. **快速版本回退**：管理员选择历史版本后，一键回退并发布新版本，新发起流程立即使用回退后的定义
4. **权限分级控制**：普通流程管理员可直接回退，关键流程需超级管理员权限
5. **不影响运行实例**：正在运行的流程实例继续按当前版本执行，新实例使用回退后的版本

## 2. 代码现状（Research Findings）

### 2.1 相关入口与链路

**后端链路**：
- 入口：`forge-plugin-flow/service/impl/FlowModelServiceImpl.java:126-224`（deployModel 方法）
- 流程：FlowModel 发布 → 部署到 Flowable → ProcessDefinition 生成新版本 → 更新 FlowModel 的 deploymentId
- 版本查询：`FlowModelServiceImpl.java:378-413`（getModelVersions 方法）从 Flowable 查询历史版本

**前端链路**：
- 入口：`forge-admin-ui/src/views/flow/model.vue:1-100`（流程模型管理页面）
- 功能：模型列表展示、状态筛选、新增模型、设计流程、发布模型
- 缺失：无版本历史查看入口、无版本对比入口、无版本回退入口

**数据库现状**：
- 表：`sys_flow_model`（FlowModel.java:14-140）
- 版本相关字段：
  - `version` (Integer)：当前版本号，每次发布 +1
  - `deploymentId` (String)：Flowable 部署 ID，每次发布更新为新值
  - `processDefinitionId` (String)：Flowable 流程定义 ID
  - `bpmnXml` (String)：当前流程设计 XML，每次保存覆盖
- **缺陷**：缺少独立的历史版本表，无法存储历史 BPMN XML

### 2.2 现有实现

**版本号管理**：
- 文件：`FlowModelServiceImpl.java:158-167`
- 实现：每次发布检查状态，已发布模型 version +1，未发布模型使用初始版本 1
- 缺陷：仅更新当前版本号，旧版本信息丢失

**Flowable 部署**：
- 文件：`FlowModelServiceImpl.java:183-200`
- 实现：调用 `repositoryService.createDeployment()` 部署 BPMN XML 和流程图
- 部署 Key：`modelKey + "_v" + version`（如 `leave_v2`）
- 缺陷：Flowable 自动管理 ProcessDefinition 版本，但 FlowModel 表仅记录最新 deploymentId

**历史版本查询**：
- 文件：`FlowModelServiceImpl.java:378-413`
- 实现：从 Flowable 查询 `processDefinitionKey = modelKey` 的所有 ProcessDefinition
- 返回：版本号、部署时间、部署 ID、是否挂起
- 缺陷：无法获取历史 BPMN XML，无法获取变更说明、变更人

### 2.3 发现与风险

**发现 1：版本信息分散**
- FlowModel 表仅存储当前版本号和最新部署 ID
- Flowable ProcessDefinition 存储历史版本，但无法获取历史 BPMN XML（部署后 XML 存在 Flowable 内部存储）
- 需要独立版本历史表，统一管理版本生命周期

**发现 2：回退技术可行性**
- Flowable 支持多版本并发运行（ProcessDefinition 按版本隔离）
- 正在运行的实例继续按当前版本执行，新实例使用新版本（符合"兼容性回退"策略）
- 回退本质是"发布历史版本作为新版本"，版本号递增，内容复用历史 BPMN XML

**发现 3：权限控制缺失**
- 当前 FlowModel 无"重要性等级"字段
- 发布/挂起/删除操作无权限分级控制
- 需新增字段区分流程重要性，并在接口层增加权限校验

**风险 1：存储成本增加**
- 全量存储历史 BPMN XML，每个版本可能几十 KB 到几百 KB
- 假设每个模型平均 50 个历史版本，存储成本约 2.5 MB
- 缓解措施：提供版本清理功能，删除无价值的草稿版本

**风险 2：版本回退误操作**
- 用户误选择错误版本回退，可能导致业务流程中断
- 缓解措施：
  1. 回退前展示版本对比，用户确认差异后执行
  2. 权限分级，关键流程需更高权限
  3. 版本标记清晰，区分"正式发布"、"测试版本"、"已废弃"

## 3. 功能点

### 3.1 核心功能
- [ ] **版本历史查看**：用户点击"版本历史"按钮，弹窗展示该模型的所有历史版本（版本号、发布时间、发布人、版本标记、变更说明）
- [ ] **版本对比**：用户选择两个版本后，展示 BPMN XML 差异（新增节点、修改连线、删除配置），支持图形化对比
- [ ] **版本回退**：用户选择历史版本后，系统复制该版本的 BPMN XML 并发布为新版本，版本号递增，版本名称自动标记"回退自 vX"
- [ ] **版本标记管理**：用户手动标记版本状态（草稿、测试、正式发布、已废弃），支持版本删除功能

### 3.2 辅助功能
- [ ] **版本详情查看**：点击版本行展示该版本的完整 BPMN XML、表单配置、流程图预览
- [ ] **版本清理**：管理员批量删除无价值的草稿版本，减少存储成本
- [ ] **版本下载**：用户下载指定版本的 BPMN XML 文件

### 3.3 输入→处理→输出

**功能 1：版本历史查看**
- **输入**：模型 ID（modelId）
- **处理**：查询 `sys_flow_model_version` 表，按版本号倒序排列
- **输出**：版本列表（版本号、发布时间、发布人、版本标记、变更说明、操作按钮）

**功能 2：版本对比**
- **输入**：模型 ID + 两个版本号（version1, version2）
- **处理**：解析两个 BPMN XML，提取节点、连线、配置差异
- **输出**：差异列表（新增节点、修改节点、删除节点、新增连线、修改连线、删除连线）

**功能 3：版本回退**
- **输入**：模型 ID + 目标版本号（targetVersion）
- **处理**：
  1. 查询目标版本的 BPMN XML 和表单配置
  2. 权限校验（检查流程重要性等级和用户权限）
  3. 复制目标版本内容，生成新版本号（当前版本号 +1）
  4. 部署到 Flowable，生成新的 deploymentId
  5. 插入新版本历史记录，版本名称标记"回退自 v{targetVersion}"
- **输出**：新版本 ID、新版本号、部署成功提示

**功能 4：版本标记更新**
- **输入**：版本 ID + 新标记状态（draft/test/release/deprecated）
- **处理**：更新 `sys_flow_model_version` 的 version_tag 字段
- **输出**：更新成功提示

## 4. 业务规则

### 4.1 版本生命周期规则
1. **版本创建时机**：
   - 每次点击"发布"按钮时，系统自动插入版本历史记录
   - 版本号 = FlowModel 当前版本号
   - 版本标记自动设置为"正式发布"
   - 变更说明由用户填写（可选，默认为"版本发布")

2. **版本号递增规则**：
   - 发布操作：版本号 +1（如 v1 → v2）
   - 回退操作：版本号 +1（如 v3 回退 v1 → 生成 v4）
   - 版本号永不减少，保持单调递增

3. **版本标记规则**：
   - **草稿（draft）**：用户手动保存未发布的版本
   - **测试（test）**：用户手动标记为测试版本
   - **正式发布（release）**：系统自动标记所有发布版本
   - **已废弃（deprecated）**：用户手动标记已不再使用的版本

4. **版本删除规则**：
   - 已发布版本不可删除（防止误删导致历史丢失）
   - 草稿/测试版本可删除
   - 删除版本不影响 FlowModel 当前版本

### 4.2 权限分级规则

**流程重要性等级**：
- 等级 1（普通流程）：请假、通用审批等低风险流程
- 等级 2（重要流程）：财务审批、合同签署等高风险流程

**权限矩阵**：
| 操作 | 普通流程（等级1） | 重要流程（等级2） |
|------|------------------|------------------|
| 版本回退 | 模型管理员权限 | 超级管理员权限 |
| 版本删除 | 模型管理员权限 | 超级管理员权限 |
| 版本标记更新 | 模型管理员权限 | 模型管理员权限 |
| 版本历史查看 | 所有用户 | 所有用户 |

**权限校验逻辑**：
1. 查询 FlowModel 的 importance_level 字段
2. 获取当前用户的角色权限列表
3. 根据权限矩阵判断是否有操作权限
4. 无权限返回错误提示"权限不足，请联系管理员"

### 4.3 兼容性回退规则

**回退时正在运行实例的处理**：
- 正在运行的流程实例继续按当前版本（ProcessDefinition）执行
- 新发起的流程使用回退后发布的新版本
- 系统提示"回退成功，正在运行的 N 个实例将继续按旧版本执行"

**多版本并发运行管理**：
- Flowable 自动管理多个 ProcessDefinition 版本
- 模型详情页展示当前活跃版本数量
- 支持查看每个版本的运行实例数量

### 4.4 版本命名和说明规则

**自动命名规则**：
- 正常发布：版本名称 = "v{version}"（如 v1、v2、v3）
- 回退发布：版本名称 = "回退自 v{targetVersion}"（如 "回退自 v1")

**变更说明规则**：
- 用户可在发布时填写变更说明（如"新增财务审批节点"）
- 回退时系统自动填充变更说明 = "回退到 v{targetVersion}"
- 变更说明长度限制：最大 500 字符

## 5. 数据变更

### 5.1 新增表：sys_flow_model_version

| 字段 | 类型 | 说明 | 约束 |
|------|------|------|------|
| id | VARCHAR(64) | 主键（UUID） | PRIMARY KEY |
| model_id | VARCHAR(64) | 模型 ID | INDEX, NOT NULL |
| version | INT | 版本号 | NOT NULL |
| version_name | VARCHAR(100) | 版本名称 | NOT NULL |
| version_tag | VARCHAR(20) | 版本标记（draft/test/release/deprecated） | NOT NULL, DEFAULT 'draft' |
| bpmn_xml | TEXT | BPMN 流程定义 XML | NOT NULL |
| form_json | TEXT | 表单配置 JSON | NULLABLE |
| change_description | VARCHAR(500) | 变更说明 | NULLABLE |
| deployment_id | VARCHAR(64) | Flowable 部署 ID | NULLABLE |
| process_definition_id | VARCHAR(64) | Flowable 流程定义 ID | NULLABLE |
| publish_by | VARCHAR(64) | 发布人 | NOT NULL |
| publish_time | DATETIME | 发布时间 | NOT NULL |
| tenant_id | BIGINT | 租户 ID | NOT NULL, DEFAULT 1 |
| create_time | DATETIME | 创建时间 | NOT NULL |
| del_flag | INT | 删除标志（0-正常/1-删除） | NOT NULL, DEFAULT 0 |

**索引设计**：
- PRIMARY KEY: `id`
- INDEX: `idx_model_version` (`model_id`, `version`) — 快速查询模型版本历史
- INDEX: `idx_model_id` (`model_id`) — 查询模型所有版本

**约束设计**：
- UNIQUE: (`model_id`, `version`) — 同一模型版本号唯一
- FOREIGN KEY: `model_id` REFERENCES `sys_flow_model(id)` — 关联模型

### 5.2 修改表：sys_flow_model

| 操作 | 字段 | 类型 | 说明 |
|------|------|------|------|
| 新增 | importance_level | INT | 流程重要性等级（1-普通/2-重要），默认值 1 |

**SQL 变更脚本**：
```sql
-- 新增版本历史表
CREATE TABLE `sys_flow_model_version` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT '主键',
  `model_id` VARCHAR(64) NOT NULL COMMENT '模型ID',
  `version` INT NOT NULL COMMENT '版本号',
  `version_name` VARCHAR(100) NOT NULL COMMENT '版本名称',
  `version_tag` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '版本标记',
  `bpmn_xml` TEXT NOT NULL COMMENT 'BPMN XML',
  `form_json` TEXT COMMENT '表单配置',
  `change_description` VARCHAR(500) COMMENT '变更说明',
  `deployment_id` VARCHAR(64) COMMENT '部署ID',
  `process_definition_id` VARCHAR(64) COMMENT '流程定义ID',
  `publish_by` VARCHAR(64) NOT NULL COMMENT '发布人',
  `publish_time` DATETIME NOT NULL COMMENT '发布时间',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `del_flag` INT NOT NULL DEFAULT 0 COMMENT '删除标志',
  UNIQUE KEY `uk_model_version` (`model_id`, `version`),
  INDEX `idx_model_id` (`model_id`)
) ENGINE=InnoDB CHARSET=utf8mb4 COMMENT='流程模型版本历史表';

-- 修改模型表新增重要性等级字段
ALTER TABLE `sys_flow_model` 
ADD COLUMN `importance_level` INT NOT NULL DEFAULT 1 COMMENT '重要性等级（1-普通/2-重要）' AFTER `status`;
```

## 6. 接口变更

### 6.1 后端接口（Controller 层）

| 操作 | 接口路径 | 方法 | 变更内容 |
|------|---------|------|---------|
| 新增 | GET /flow/model/version/list | pageVersionList | 分页查询版本历史（参数：modelId, pageNum, pageSize） |
| 新增 | GET /flow/model/version/{versionId} | getVersionDetail | 查询版本详情（返回：BPMN XML、表单配置、流程图） |
| 新增 | POST /flow/model/version/compare | compareVersions | 版本对比（参数：modelId, version1, version2） |
| 新增 | POST /flow/model/version/revert | revertVersion | 版本回退（参数：modelId, targetVersion, changeDescription） |
| 新增 | PUT /flow/model/version/{versionId}/tag | updateVersionTag | 更新版本标记（参数：versionId, versionTag） |
| 新增 | DELETE /flow/model/version/{versionId} | deleteVersion | 删除版本（仅允许删除草稿/测试版本） |
| 新增 | GET /flow/model/version/download/{versionId} | downloadVersion | 下载版本 BPMN XML |
| 修改 | POST /flow/model/deploy/{modelId} | deployModel | 发布时插入版本历史记录（参数新增：changeDescription） |

### 6.2 接口详细定义

**1. GET /flow/model/version/list**
- **请求参数**：
  - `modelId` (String): 模型 ID
  - `pageNum` (Integer): 页码，默认 1
  - `pageSize` (Integer): 每页数量，默认 20
- **响应数据**：
  ```json
  {
    "code": 200,
    "data": {
      "records": [
        {
          "id": "v1_uuid",
          "modelId": "model_uuid",
          "version": 1,
          "versionName": "v1",
          "versionTag": "release",
          "changeDescription": "初始版本",
          "publishBy": "admin",
          "publishTime": "2026-05-01 10:00:00",
          "deploymentId": "dep_v1"
        }
      ],
      "total": 10,
      "pageNum": 1,
      "pageSize": 20
    }
  }
  ```

**2. POST /flow/model/version/compare**
- **请求参数**：
  ```json
  {
    "modelId": "model_uuid",
    "version1": 1,
    "version2": 2
  }
  ```
- **响应数据**：
  ```json
  {
    "code": 200,
    "data": {
      "addedNodes": [{"id": "node_3", "name": "财务审批"}],
      "modifiedNodes": [{"id": "node_1", "oldName": "经理审批", "newName": "部门审批"}],
      "deletedNodes": [],
      "addedFlows": [{"id": "flow_3", "source": "node_1", "target": "node_3"}],
      "modifiedFlows": [],
      "deletedFlows": [{"id": "flow_2"}]
    }
  }
  ```

**3. POST /flow/model/version/revert**
- **请求参数**：
  ```json
  {
    "modelId": "model_uuid",
    "targetVersion": 1,
    "changeDescription": "回退到稳定版本"
  }
  ```
- **响应数据**：
  ```json
  {
    "code": 200,
    "data": {
      "newVersionId": "v4_uuid",
      "newVersion": 4,
      "deploymentId": "dep_v4",
      "runningInstances": 5
    },
    "message": "回退成功，正在运行的 5 个实例将继续按旧版本执行"
  }
  ```

**4. PUT /flow/model/version/{versionId}/tag**
- **请求参数**：
  - `versionId` (String): 版本 ID
  - `versionTag` (String): 新标记（draft/test/release/deprecated）
- **响应数据**：
  ```json
  {
    "code": 200,
    "message": "版本标记更新成功"
  }
  ```

**5. DELETE /flow/model/version/{versionId}**
- **请求参数**：
  - `versionId` (String): 版本 ID
- **响应数据**：
  ```json
  {
    "code": 200,
    "message": "版本删除成功"
  }
  ```
- **异常情况**：
  - 版本标记为"release"时返回错误："已发布版本不可删除"

**6. POST /flow/model/deploy/{modelId}（修改）**
- **请求参数新增**：
  - `changeDescription` (String): 变更说明，可选
- **处理逻辑新增**：
  1. 发布成功后，插入版本历史记录到 `sys_flow_model_version`
  2. 版本号 = FlowModel 当前版本号
  3. 版本标记自动设置为"release"
  4. 变更说明 = 用户填写或"版本发布"

### 6.3 前端 API 调用层（TypeScript）

**新增文件**：`forge-admin-ui/src/api/flow/version.ts`

```typescript
// 版本历史查询
export function getVersionList(modelId: string, pageNum: number, pageSize: number) {
  return request.get('/flow/model/version/list', { params: { modelId, pageNum, pageSize } })
}

// 版本详情查询
export function getVersionDetail(versionId: string) {
  return request.get(`/flow/model/version/${versionId}`)
}

// 版本对比
export function compareVersions(modelId: string, version1: number, version2: number) {
  return request.post('/flow/model/version/compare', { modelId, version1, version2 })
}

// 版本回退
export function revertVersion(modelId: string, targetVersion: number, changeDescription?: string) {
  return request.post('/flow/model/version/revert', { modelId, targetVersion, changeDescription })
}

// 更新版本标记
export function updateVersionTag(versionId: string, versionTag: string) {
  return request.put(`/flow/model/version/${versionId}/tag`, { versionTag })
}

// 删除版本
export function deleteVersion(versionId: string) {
  return request.delete(`/flow/model/version/${versionId}`)
}

// 下载版本 BPMN XML
export function downloadVersion(versionId: string) {
  return request.get(`/flow/model/version/download/${versionId}`, { responseType: 'blob' })
}
```

### 6.4 权限注解

**Controller 权限注解示例**：
```java
@PostMapping("/version/revert")
@SaCheckPermission("flow:model:revert")  // 权限标识
@OperationLog(module = "流程管理", operation = "版本回退", description = "回退到 v{targetVersion}")
public RespInfo<VersionRevertVO> revertVersion(@RequestBody VersionRevertDTO dto) {
    // 权限分级校验逻辑在 Service 层实现
    return RespInfo.success(flowModelVersionService.revertVersion(dto));
}
```

## 7. 影响范围

### 7.1 后端模块影响
- **forge-plugin-flow**：新增 FlowModelVersion 实体、Mapper、Service、Controller，修改 FlowModelServiceImpl 的 deployModel 方法
- **forge-starter-auth**：新增权限标识 `flow:model:revert`、`flow:model:version:delete`，需在系统菜单管理中配置
- **数据库**：新增 sys_flow_model_version 表，修改 sys_flow_model 表新增 importance_level 字段

### 7.2 前端模块影响
- **forge-admin-ui/src/views/flow/model.vue**：新增"版本历史"按钮，新增版本历史弹窗组件
- **forge-admin-ui/src/views/flow**：新增版本对比页面、版本详情页面（或采用弹窗方式）
- **forge-admin-ui/src/api/flow**：新增 version.ts API 接口文件
- **菜单管理**：新增"版本管理"菜单项（或在现有"流程模型"菜单下新增"版本历史"按钮）

### 7.3 Flowable 引擎影响
- **ProcessDefinition 多版本管理**：回退操作会部署新的 ProcessDefinition，Flowable 自动管理版本隔离
- **运行实例不受影响**：正在运行的实例继续按当前 ProcessDefinition 执行（兼容性回退策略）
- **部署历史保留**：历史版本的 deploymentId 在 Flowable RepositoryService 中完整保留

### 7.4 系统配置影响
- **字典管理**：新增字典类型 `flow_version_tag`（草稿/测试/正式发布/已废弃）
- **字典管理**：新增字典类型 `flow_importance_level`（普通流程/重要流程）
- **权限配置**：超级管理员角色需增加"重要流程版本回退"权限

### 7.5 用户操作影响
- **模型管理员**：新增版本管理能力（查看历史、版本对比、版本回退、版本标记）
- **超级管理员**：新增重要流程版本回退权限、版本删除权限
- **流程发起人**：不受影响，新发起流程使用回退后的版本

## 8. 风险与关注点

> ⚠️ 涉及资金/状态流转/权限变更，已标注并需经人工审查

### 8.1 数据安全风险 ⚠️
**风险描述**：
- 版本回退可能导致正在运行的流程实例与新流程定义不一致
- 例如：v2 有 3 个审批节点，回退到 v1（2 个节点），正在 v2 节点 2 等待审批的实例无法继续

**缓解措施**：
- 采用"兼容性回退"策略，正在运行的实例继续按当前版本执行
- 系统明确提示"正在运行的 N 个实例将继续按旧版本执行"
- 回退前展示版本对比，用户确认节点差异后执行

**审查要点**：
- 验证回退后正在运行实例的完整性（能否正常审批、流程能否正常结束）
- 测试场景：回退后正在运行实例包含已删除节点的审批任务

### 8.2 权限控制风险 ⚠️
**风险描述**：
- 重要流程（财务审批、合同签署）误回退可能导致业务中断
- 权限分级配置错误可能导致权限混乱

**缓解措施**：
- 权限分级：重要流程需超级管理员权限（importance_level = 2）
- 操作日志：所有版本回退操作记录日志，可追溯
- 二次确认：回退前弹窗展示版本对比，用户确认后执行

**审查要点**：
- 验证权限矩阵配置正确性（普通流程 vs 重要流程）
- 测试场景：普通管理员尝试回退重要流程，应返回"权限不足"错误

### 8.3 存储成本风险
**风险描述**：
- 全量存储 BPMN XML，每个版本可能 50KB~500KB
- 假设每个模型平均 50 个历史版本，存储成本约 2.5MB
- 系统有 100 个模型时，版本表存储约 250MB

**缓解措施**：
- 提供版本清理功能，删除无价值的草稿/测试版本
- 版本标记清晰，用户可快速识别废弃版本并删除
- 存储监控：定期检查版本表大小，超过阈值时告警

**审查要点**：
- 验证版本删除功能（仅允许删除草稿/测试版本）
- 测试场景：批量删除 10 个草稿版本，存储空间是否释放

### 8.4 版本号混乱风险
**风险描述**：
- 反复回退可能导致版本号快速增长（如 v1 → v2 → v3 → 回退 v1 → v4 → 回退 v2 → v5）
- 版本名称可能混淆（v4 的内容实际是 v1 的内容）

**缓解措施**：
- 版本名称自动标记"回退自 vX"，清晰表达版本来源
- 版本历史列表显示版本名称、变更说明，用户可快速理解
- 版本详情展示完整 BPMN XML 和流程图预览

**审查要点**：
- 验证版本名称生成逻辑（回退版本名称格式："回退自 v{targetVersion}"）
- 测试场景：连续回退 3 次，版本号和版本名称是否正确

### 8.5 Flowable 版本隔离风险
**风险描述**：
- Flowable ProcessDefinition 按版本隔离，但多版本并发运行可能导致管理混乱
- 正在运行的实例可能分散在多个 ProcessDefinition 中

**缓解措施**：
- 模型详情页展示当前活跃版本数量和运行实例数量
- 流程监控页面按 ProcessDefinition 版本筛选实例
- 提供版本挂起功能，挂起旧版本 ProcessDefinition，停止接收新实例

**审查要点**：
- 验证多版本并发运行时的实例管理（能否正确查询、审批、结束）
- 测试场景：回退后同时存在 v3 和 v4 实例，分别审批验证

### 8.6 前端性能风险
**风险描述**：
- 版本对比需要解析两个 BPMN XML，前端处理可能耗时
- 版本历史列表加载大量数据可能导致页面卡顿

**缓解措施**：
- 版本对比采用后端处理，前端仅展示差异结果
- 版本历史列表采用分页加载（默认 20 条/页）
- 版本详情采用弹窗方式，避免页面跳转

**审查要点**：
- 验证版本对比接口性能（解析两个 500KB BPMN XML 是否超时）
- 测试场景：加载 100 条版本历史，页面是否流畅

## 8.5 测试策略

### 8.5.1 测试范围
**单元测试**：
- FlowModelVersionService 业务逻辑测试（版本插入、版本对比、版本回退）
- FlowModelServiceImpl deployModel 方法测试（发布时版本插入）
- 权限校验逻辑测试（普通流程 vs 重要流程）

**集成测试**：
- 版本回退与 Flowable 部署集成测试（ProcessDefinition 版本隔离）
- 正在运行实例兼容性测试（回退后实例继续执行）
- 数据库约束测试（版本号唯一性、外键关联）

**端到端测试**：
- 用户操作流程测试（查看历史 → 版本对比 → 版本回退 → 版本标记）
- 权限分级测试（普通管理员 vs 超级管理员）
- 异常场景测试（删除已发布版本、回退不存在版本）

### 8.5.2 覆盖率目标
- **单元测试覆盖率**：≥80%（Service 层核心逻辑）
- **集成测试覆盖率**：≥60%（Controller → Service → Mapper → Database）
- **端到端测试覆盖率**：≥50%（关键用户路径）

### 8.5.3 独立 Test Spec
- **是否需要独立 Test Spec**：是
- **原因**：版本管理涉及多模块协作、Flowable 集成、权限控制，测试场景复杂
- **Test Spec 文件位置**：`code-copilot/changes/flow-model-version-management/test-spec.md`

### 8.5.4 关键测试场景

**场景 1：版本回退后正在运行实例的完整性**
- **前置条件**：流程模型已发布 v2，有 10 个实例正在运行（其中 5 个在节点 2 等待审批）
- **测试步骤**：管理员回退到 v1（节点数减少），新发起 5 个实例
- **预期结果**：正在运行的 10 个实例继续按 v2 执行，新发起的 5 个实例按 v1 执行

**场景 2：重要流程权限控制**
- **前置条件**：流程模型 importance_level = 2（重要流程），当前用户为普通管理员
- **测试步骤**：普通管理员尝试版本回退
- **预期结果**：返回错误"权限不足，请联系超级管理员"

**场景 3：版本对比准确性**
- **前置条件**：v1 有 2 个节点，v2 有 3 个节点（新增"财务审批"）
- **测试步骤**：用户对比 v1 和 v2
- **预期结果**：差异结果显示新增节点"财务审批"、新增连线

**场景 4：版本删除限制**
- **前置条件**：版本标记为"release"（正式发布）
- **测试步骤**：管理员尝试删除该版本
- **预期结果**：返回错误"已发布版本不可删除"

**场景 5：连续回退版本号递增**
- **前置条件**：模型当前版本 v3，历史版本有 v1、v2
- **测试步骤**：依次回退 v1 → 回退 v2 → 回退 v1
- **预期结果**：版本号依次递增 v4、v5、v6，版本名称分别为"回退自 v1"、"回退自 v2"、"回退自 v1"

## 9. 已确认的技术决策

> ✅ 所有技术决策已确认，可进入 `/apply` 执行阶段

### 9.1 版本对比的图形化展示方式
- **确认方案**：Side-by-side 并排对比
- **实现方式**：
  - 左侧展示 v1 版本的 BPMN 流程图（使用 bpmn-js 渲染）
  - 右侧展示 v2 版本的 BPMN 流程图（使用 bpmn-js 渲染）
  - 高亮差异节点（新增节点：绿色，修改节点：黄色，删除节点：红色）
  - 差异列表以文本形式展示在流程图下方
- **技术要点**：
  - 使用 bpmn-js 的 NavigatedViewer 组件渲染 BPMN XML
  - 通过 Canvas API 高亮差异节点
  - 前端实时渲染，不存储流程图 PNG

### 9.2 版本清理功能的自动化程度
- **确认方案**：纯手动删除
- **实现方式**：
  - 管理员在版本历史列表中选择版本并点击"删除"按钮
  - 仅允许删除草稿（draft）和测试（test）版本的版本
  - 已发布（release）版本不可删除（防止历史丢失）
  - 系统提示确认弹窗："确定删除该版本？删除后无法恢复"
- **不采用定时清理的原因**：
  - 避免误删重要版本（如测试版本可能包含有价值的流程设计）
  - 用户自主决定哪些版本值得保留，灵活性更强

### 9.3 版本详情的流程图预览技术方案
- **确认方案**：前端实时渲染 BPMN XML
- **实现方式**：
  - 版本详情弹窗中嵌入 bpmn-js Viewer 组件
  - 从后端 API 获取该版本的 BPMN XML（GET /version/{versionId}）
  - 前端调用 bpmn-js.importXML() 渲染流程图
  - 支持缩放、拖拽、节点点击交互
- **不存储流程图 PNG 的原因**：
  - 减少存储成本（sys_flow_model_version 表无需存储图片）
  - bpmn-js 渲染性能可控（BPMN XML 解析时间 < 500ms）
  - 交互性强（用户可点击节点查看详情）

### 9.4 重要流程的判断标准
- **确认方案**：管理员手动标记
- **实现方式**：
  - FlowModel 实体新增 importanceLevel 字段（Integer，默认值 1）
  - 模型配置页面新增"重要性等级"下拉框（字典：flow_importance_level）
  - 管理员创建/编辑模型时可手动设置重要性等级
  - 系统无自动判断逻辑（灵活性强，适应不同业务场景）
- **字典数据**：
  - 值 1：普通流程（模型管理员可回退）
  - 值 2：重要流程（需超级管理员权限回退）

### 9.5 版本标记的状态流转规则
- **确认方案**：状态流转有约束
- **状态流转约束**：
  - **允许流转**：
    - draft → test（草稿提交测试）
    - test → release（测试完成发布）
    - release → deprecated（发布版本废弃）
    - draft → release（草稿直接发布）
  - **禁止流转**：
    - deprecated → release（已废弃版本不可恢复为发布状态）
    - deprecated → test（已废弃版本不可恢复为测试状态）
    - deprecated → draft（已废弃版本不可恢复为草稿状态）
    - test → draft（测试版本不可退回草稿）
    - release → test（发布版本不可退回测试）
    - release → draft（发布版本不可退回草稿）
- **删除约束**：
  - draft 和 test 版本可删除
  - release 和 deprecated 版本不可删除
- **状态变更历史记录**：
  - 不单独记录状态变更历史（简化实现）
  - 变更说明字段可记录状态变更原因

### 9.6 版本回退后的变更说明填写方式
- **确认方案**：强制填写
- **实现方式**：
  - 版本回退弹窗中包含"变更说明"输入框（必填）
  - 输入框最小长度：10 字符（防止用户填写过短说明）
  - 输入框最大长度：500 字符
  - 不填写或长度不足时，返回错误提示："变更说明长度不足，至少 10 字符"
  - 系统自动在变更说明前缀添加"回退自 v{targetVersion}: "（如"回退自 v1: 恢复稳定版本"）
- **强制填写的原因**：
  - 版本回退属于高风险操作，变更说明有助于追溯原因
  - 强制填写提高用户对回退操作的重视程度
  - 变更说明记录在 sys_flow_model_version 表的 change_description 字段

## 10. 技术决策

### 10.1 版本对比算法选择
- **决策**：采用后端 XML 解析 + 节点差异提取，前端 Side-by-side 图形化展示
- **理由**：
  - BPMN XML 解析在后端处理更稳定（Java DOM 解析）
  - 前端 bpmn-js 渲染性能可控，支持高亮和交互
  - Side-by-side 对比视觉直观，符合流程设计器风格

### 10.2 版本存储策略选择
- **决策**：全量存储 BPMN XML，不采用增量存储
- **理由**：
  - 增量存储技术复杂，维护成本高
  - BPMN XML 大小可控（平均 50KB~500KB），存储成本可接受
  - 全量存储便于版本对比、版本下载、版本恢复

### 10.3 权限分级实现方式
- **决策**：在 Service 层实现权限校验，不依赖注解硬编码
- **理由**：
  - 权限分级需根据模型 importance_level 动态判断，注解无法实现
  - Service 层可查询模型配置并校验用户权限，灵活性强
  - 便于后续扩展权限等级（如新增等级 3）

### 10.4 Flowable 部署隔离策略
- **决策**：每次部署生成独立 ProcessDefinition，不使用 Flowable 的"覆盖部署"
- **理由**：
  - Flowable 默认支持多版本并发运行（版本隔离）
  - 覆盖部署可能导致正在运行实例异常
  - 版本隔离便于追踪历史版本和运行实例

## 11. 执行日志

| Task | 状态 | 实际改动文件 | 备注 |
|------|------|-------------|------|
| 数据库表创建 | ✅已完成 | forge/forge-admin-server/src/main/resources/sql/flow_version_init.sql | 新增 sys_flow_model_version 表 + 修改 sys_flow_model 表 + 新增字典数据 + 权限配置 SQL，提交：d96a582 + ddf186b |
| FlowModel 实体修改 | ✅已完成 | forge-plugin-flow/entity/FlowModel.java | 新增 importanceLevel 字段，提交：1c685c9 |
| FlowModelVersion 实体创建 | ✅已完成 | forge-plugin-flow/entity/FlowModelVersion.java | 新增版本历史实体（14字段），提交：3078874 |
| 字典数据新增 | ✅已完成（Task 1） | flow_version_init.sql（Task 1 已包含） | 新增 flow_version_tag + flow_importance_level 字典，无需单独提交 |
| FlowModelVersionMapper 创建 | ✅已完成 | forge-plugin-flow/mapper/FlowModelVersionMapper.java + XML | 新增版本 Mapper（3个查询方法），提交：dbc41ec |
| 版本相关 DTO/VO 创建 | ✅已完成 | dto/（3个DTO）+ vo/（3个VO） | 新增 6 个 DTO/VO 类，提交：4f17be4 |
| FlowModelVersionService 创建 | ✅已完成 | forge-plugin-flow/service/FlowModelVersionService.java + impl | 新增版本 Service（7个核心方法），提交：a26859a |
| FlowModelServiceImpl 修改 | ✅已完成 | forge-plugin-flow/service/FlowModelService.java + impl | deployModel 新增 changeDescription 参数并插入版本历史，提交：d183d06 |
| FlowModelVersionController 创建 | ✅已完成 | forge-plugin-flow/controller/FlowModelVersionController.java | 新增版本 Controller（7个REST API），提交：072bde6 |
| 前端 API 接口创建 | ✅已完成 | forge-admin-ui/src/api/version.js | 新增版本 API 接口（7个接口），提交：6652264 |
| 前端版本历史组件创建 | ✅已完成 | forge-admin-ui/src/views/flow/version.vue | 新增版本历史列表组件，提交：2b58162 |
| 前端版本对比组件创建 | ✅已完成 | forge-admin-ui/src/views/flow/versionCompare.vue | 新增版本对比组件，提交：2b58162 |
| 前端 model.vue 修改 | ✅已完成 | forge-admin-ui/src/views/flow/model.vue | 新增"版本历史"按钮，提交：2b58162 |
| 权限配置和菜单新增 | ✅已完成 | flow_version_init.sql（Task 1 文件更新） | 新增权限标识（flow:model:revert + flow:model:version:delete），提交：ddf186b |

**总任务数**：14
**完成数**：14
**Spec 状态**：confirmed → apply → review（待审查）

**Git 提交汇总**（共10个提交）：
- d96a582: Task 1 数据库表创建
- 1c685c9: Task 2 FlowModel实体修改
- 3078874: Task 3 FlowModelVersion实体创建
- dbc41ec: Task 5 FlowModelVersionMapper创建
- 4f17be4: Task 6 DTO/VO创建
- a26859a: Task 7 FlowModelVersionService创建
- d183d06: Task 8 FlowModelServiceImpl修改
- 072bde6: Task 9 FlowModelVersionController创建
- 6652264: Task 10 前端API接口创建
- 2b58162: Task 11-13 前端组件创建和修改
- ddf186b: Task 14 权限配置SQL

**核心功能已实现**：
✅ 数据库层：版本历史表创建、字典数据新增
✅ 实体层：FlowModel 和 FlowModelVersion 实体创建
✅ Mapper层：版本查询方法（分页、详情、最大版本号）
✅ Service层：核心业务逻辑（版本查询、对比、回退、删除）
✅ Controller层：REST API（7个接口）
✅ 前端层：API接口、版本历史组件、版本对比组件
✅ 集成层：发布时自动插入版本历史
✅ 权限层：版本回退和删除权限标识定义
| FlowModelVersionMapper 创建 | 待执行 | forge-plugin-flow/mapper/FlowModelVersionMapper.java + XML | 新增版本 Mapper |
| FlowModelVersionService 创建 | 待执行 | forge-plugin-flow/service/FlowModelVersionService.java + impl | 新增版本 Service |
| FlowModelVersionController 创建 | 待执行 | forge-plugin-flow/controller/FlowModelVersionController.java | 新增版本 Controller |
| FlowModelServiceImpl 修改 | 待执行 | forge-plugin-flow/service/impl/FlowModelServiceImpl.java | deployModel 方法新增版本插入 |
| 前端 API 接口创建 | 待执行 | forge-admin-ui/src/api/flow/version.ts | 新增版本 API 接口 |
| 前端版本历史组件创建 | 待执行 | forge-admin-ui/src/views/flow/version.vue | 新增版本历史页面 |
| 前端版本对比组件创建 | 待执行 | forge-admin-ui/src/views/flow/versionCompare.vue | 新增版本对比页面 |
| 前端 model.vue 修改 | 待执行 | forge-admin-ui/src/views/flow/model.vue | 新增"版本历史"按钮 |
| 字典数据新增 | 待执行 | sys_dict_type + sys_dict_data 表 | 新增版本标记和重要性等级字典 |
| 菜单权限配置 | 待执行 | sys_resource 表 | 新增版本管理菜单和权限标识 |

## 12. 审查结论

（待代码实现完成后填写）

## 13. 确认记录（HARD-GATE）

- **确认时间**：2026-05-08 15:30
- **确认人**：项目负责人（用户）
- **确认内容**：
  1. 完整 Spec 已阅读并确认
  2. 所有待澄清问题已全部解决（共 6 个）
  3. 技术决策已确认并记录在第 9 节
  4. 任务拆分已确认（tasks.md，共 14 个任务，预估 22h）
  5. 批准进入 `/apply` 执行阶段
- **确认方案摘要**：
  - 版本对比：Side-by-side 并排对比（bpmn-js 实时渲染）
  - 版本清理：纯手动删除（仅允许删除草稿/测试版本）
  - 流程图预览：前端实时渲染 BPMN XML
  - 重要性判断：管理员手动标记（importanceLevel 字段）
  - 状态流转：有约束（禁止 deprecated 恢复、禁止 test/release 退回）
  - 变更说明：强制填写（最小 10 字符）
- **确认状态**：✅ 已批准进入 `/apply` 执行阶段

## 14. 归档记录

- **归档时间**：2026-05-09
- **归档人**：code-copilot
- **归档路径**：code-copilot/changes/archive/2026-05-09-flow-model-version-management/
- **知识沉淀**：
  - tech-flow-version-db-design.md（流程版本数据库设计模板）
  - tech-flowable-multi-version.md（Flowable 多版本并发运行机制）
  - tech-version-state-machine.md（版本标记状态流转约束规则）
- **变更总结**：
  - 完成流程模型版本管理功能（14个任务，22h工作量）
  - 新增 sys_flow_model_version 表和 FlowModelVersion 实体
  - 实现版本历史查询、版本对比、版本回退、版本标记管理功能
  - 前端新增版本历史和版本对比组件
  - 集成 Flowable 多版本并发运行机制
  - 权限分级控制（普通流程/重要流程）