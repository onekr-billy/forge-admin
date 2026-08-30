# 知识索引
> 领域知识的轻量索引。每条用一句话说清核心逻辑。
> 格式：- **触发关键词**: 一句话核心逻辑 → `包名.类名.方法名`（可选）
## 业务知识
（随实践积累补充）
## 技术约定
- **Spring Boot自动配置**: 通过META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件注册自动配置类 → `tech-spring-boot-autoconfig.md`
- **Maven多模块**: 新增starter模块需要在父pom.xml的modules标签中添加模块声明 → `tech-maven-multimodule.md`
- **SpEL表达式**: 使用Spring Expression Language动态解析幂等键，需要异常处理 → `tech-spel-expression.md`
- **AOP参数名**: 使用DefaultParameterNameDiscoverer获取方法参数名，更加健壮 → `tech-aop-parameter-names.md`
- **全局异常处理**: 自定义业务异常继承BusinessException，直接被全局异常处理器捕获 → `tech-global-exception-handler.md`
- **Redisson版本兼容**: redisson-spring-data 适配按 Spring Data Redis 主版本分模块，版本必须对齐，否则登录 StackOverflowError → `tech-redisson-spring-data-redis-compat.md`
- **异步导出数据权限**: 异步导出后台线程脱离会话，须捕获/恢复租户与数据权限上下文，否则退化为全量越权 → `tech-async-export-data-permission-context.md`
- **公式引擎**: 字段级公式用 Aviator + 配置 JSON 落字段定义，发布期 DAG 拓扑排序检测循环依赖，金额用 long(分) → `tech-formula-engine-aviator-dag.md`
- **多租户隔离加固**: 区分租户业务表与平台定义表，数据权限失败关闭（恒假条件），回填脏数据前先去重防撞唯一键 → `tech-multi-tenant-isolation-hardening.md`
- **敏感数据脱敏**: 脱敏占位回写防覆盖、认证字段@JsonIgnore、敏感配置键/缓存键拒绝明文、强制改密闭环、上传扩展名黑名单 → `tech-sensitive-data-masking-strategy.md`
- **表单设计器Schema转换**: form-create 临时 ref_ id 需归一化为稳定 cmp_ id，表单配置优先同步字段资产 → `tech-form-create-forge-schema-normalization.md`
- **组织上下文角色权限**: 数据中心和当前组织分层，用户角色授权落 `sys_user_org_role`，数据权限只使用 `activeOrgId` → `tech-org-scoped-role-permission.md`
## 踩坑记录
真实故障按主题写在 `code-copilot/memory/pitfalls/`，先看 `code-copilot/memory/pitfalls.md` 索引。
