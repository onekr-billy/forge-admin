# 版本标记状态流转约束规则

> 来源：变更 flow-model-version-management  
> 时间：2026-05-09

## 问题描述
流程版本需要标记不同状态（待部署、已部署、已发布、已归档），并严格控制状态流转规则，避免误操作导致流程混乱。

## 解决方案

### 状态定义
```
0 = 待部署（草稿）
1 = 已部署（已提交到 Flowable）
2 = 已发布（正式使用，可发起流程）
3 = 已归档（历史版本，不可发起）
```

### 状态流转规则（单向流转）
```
待部署 (0)
  └─ 部署 → 已部署 (1)
       └─ 发布 → 已发布 (2)
            └─ 归档 → 已归档 (3)

❌ 不允许逆向流转（已发布不能退回已部署）
❌ 不允许跳过状态（待部署不能直接发布）
```

### 主版本约束
```
规则：每个模型只能有一个 is_primary=1 的版本

场景1：新版本发布
  - 当前主版本 is_primary 设为 0
  - 新版本 is_primary 设为 1

场景2：版本回退
  - 当前主版本 is_primary 设为 0
  - 目标历史版本 is_primary 设为 1
  - ⚠️ 需确认无运行实例或实例已完成

场景3：版本删除
  - 删除非主版本：直接删除
  - 删除主版本：禁止删除（必须先发布新版本）
```

### 状态校验代码
```java
public enum VersionStatus {
  DRAFT(0, "待部署"),
  DEPLOYED(1, "已部署"),
  PUBLISHED(2, "已发布"),
  ARCHIVED(3, "已归档");
  
  public boolean canTransitionTo(VersionStatus target) {
    return switch (this) {
      case DRAFT -> target == DEPLOYED;
      case DEPLOYED -> target == PUBLISHED;
      case PUBLISHED -> target == ARCHIVED;
      case ARCHIVED -> false; // 已归档，不可流转
    };
  }
}

// 发布校验
public void publishVersion(String versionId) {
  FlowModelVersion version = getById(versionId);
  if (!version.getStatus().canTransitionTo(PUBLISHED)) {
    throw new BusinessException("当前状态不允许发布");
  }
  // 设置主版本
  clearPrimaryVersion(version.getModelId());
  version.setIsPrimary(1);
  version.setStatus(PUBLISHED);
  updateById(version);
}
```

### 异常场景处理
| 异常 | 检查点 | 处理 |
|------|--------|------|
| 重复部署 | version 已存在 | 拒绝，提示"版本号已存在" |
| 主版本冲突 | 多个 is_primary=1 | 自动修复：保留最新版本 |
| 状态非法流转 | 已发布→已部署 | 拒绝，提示"不可逆向流转" |
| 删除主版本 | is_primary=1 | 拒绝，提示"必须先发布新版本" |

## 相关文件
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/entity/FlowModelVersion.java
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/FlowModelVersionService.java