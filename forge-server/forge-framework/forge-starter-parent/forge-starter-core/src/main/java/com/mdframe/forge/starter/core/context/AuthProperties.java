package com.mdframe.forge.starter.core.context;

import com.mdframe.forge.starter.core.annotation.config.RefreshScope;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证授权配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.auth")
@RefreshScope
public class AuthProperties {

    /**
     * 是否启用API接口权限校验
     * 默认启用
     */
    private Boolean enableApiPermission = true;

    /**
     * API权限校验排除路径（支持通配符）
     */
    private String[] apiPermissionExcludePaths = new String[]{
            "/auth/**"
    };

    /**
     * 是否在应用启动完成前盘点 Controller 路由与 API 权限资源覆盖关系。
     * API 资源采用显式配置策略，默认不要求全部 Controller 都配置资源。
     */
    private Boolean apiPermissionCoverageEnabled = false;

    /**
     * 权限资源缺失或查询失败时是否阻止应用完成启动。
     * 默认关闭；仅在要求全部 Controller 都配置 API 资源的部署中显式开启。
     * 请求期只有明确配置的 API 资源才执行资源权限校验，不受此开关影响。
     */
    private Boolean apiPermissionCoverageFailOnMissing = false;

    /**
     * 启动日志中最多输出的缺失路由数量，避免错误配置导致日志洪泛。
     */
    private Integer apiPermissionCoverageReportLimit = 100;

    /**
     * 是否启用登录失败锁定功能
     * 默认启用
     */
    private Boolean enableLoginLock = true;

    /**
     * 最大登录失败尝试次数
     * 默认4次，可通过 sys_config 配置覆盖
     */
    private Integer maxLoginAttempts = 4;

    /**
     * 账号锁定时长（分钟）
     * 默认30分钟，可通过 sys_config 配置覆盖
     */
    private Long lockDuration = 30L;

    /**
     * 登录失败记录保留时长（分钟）
     * 在此时间内的失败次数会累计
     * 默认15分钟
     */
    private Long failRecordExpire = 15L;

    /**
     * 同一账号登录策略
     * - allow_concurrent: 允许并发登录
     * - replace_old: 新登录踢出旧登录(默认)
     * - reject_new: 拒绝新登录
     */
    private String sameAccountLoginStrategy = "replace_old";

    /**
     * 是否启用在线用户管理
     * 默认启用
     */
    private Boolean enableOnlineUserManagement = true;

    /**
     * 客户端验证兼容配置。
     *
     * @deprecated 客户端身份验证属于强制安全边界，关闭此配置也不会跳过验证。
     */
    @Deprecated
    private Boolean enableClientValidation = true;

    /**
     * 是否允许读取历史明文客户端密钥。
     * 完成存量盘点和机会式升级后应关闭。
     */
    private Boolean enableLegacyClientSecretRead = true;

    /**
     * 用户主动修改密码后是否保留当前会话。
     * 默认关闭；找回密码和管理员重置始终吊销全部旧会话。
     */
    private Boolean keepCurrentSessionAfterPasswordChange = false;
}
