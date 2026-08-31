package com.mdframe.forge.plugin.system.service;

import com.mdframe.forge.starter.core.session.LoginUser;

/**
 * 用户加载服务接口
 * 提供给认证策略使用，避免循环依赖
 */
public interface IUserLoadService {

    /**
     * 根据用户名加载用户信息（包含角色、权限、组织）
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 登录用户信息
     */
    LoginUser loadUserByUsername(String username, Long tenantId);

    /**
     * 根据用户名和首选当前组织加载用户信息。
     *
     * @param username 用户名
     * @param tenantId 租户ID
     * @param preferredActiveOrgId 首选当前组织ID
     * @return 登录用户信息
     */
    LoginUser loadUserByUsername(String username, Long tenantId, Long preferredActiveOrgId);

    /**
     * 用户名密码登录：先校验密码，再按该账号可进入的工作区消歧。
     * 多个工作区且未指定 tenantId 时抛出业务码 {@code AuthResultCodes.TENANT_SELECTION_REQUIRED}。
     */
    LoginUser authenticateByUsernamePassword(String username, String rawPassword, Long requestedTenantId);

    /**
     * 根据手机号加载用户信息
     *
     * @param phone    手机号
     * @param tenantId 租户ID
     * @return 登录用户信息
     */
    LoginUser loadUserByPhone(String phone, Long tenantId);

    /**
     * 根据手机号和首选当前组织加载用户信息。
     */
    LoginUser loadUserByPhone(String phone, Long tenantId, Long preferredActiveOrgId);

    /**
     * 根据身份提供方已验证的手机号唯一加载用户。
     *
     * <p>该方法用于外部身份首次映射；没有匹配或存在多个有效候选时均拒绝认证。</p>
     */
    LoginUser loadUniqueUserByVerifiedPhone(
            String phone, Long tenantId, Long preferredActiveOrgId);

    /**
     * 根据邮箱加载用户信息
     *
     * @param email    邮箱
     * @param tenantId 租户ID
     * @return 登录用户信息
     */
    LoginUser loadUserByEmail(String email, Long tenantId);

    /**
     * 根据邮箱和首选当前组织加载用户信息。
     */
    LoginUser loadUserByEmail(String email, Long tenantId, Long preferredActiveOrgId);

    /**
     * 根据用户ID和当前租户加载用户信息
     *
     * @param userId 用户ID
     * @param tenantId 当前租户ID
     * @return 登录用户信息
     */
    LoginUser loadUserByUserId(Long userId, Long tenantId);

    /**
     * 根据用户ID、租户和首选当前组织加载用户信息。
     */
    LoginUser loadUserByUserId(Long userId, Long tenantId, Long preferredActiveOrgId);

    /**
     * 获取用户密码
     *
     * @param userId 用户ID
     * @return 加密后的密码
     */
    String getUserPassword(Long userId);

    /**
     * 验证密码
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean matchPassword(String rawPassword, String encodedPassword);

    /**
     * 验证验证码
     *
     * @param codeKey 验证码key
     * @param code    验证码
     * @return 是否正确
     */
    boolean validateCode(String codeKey, String code);

    /**
     * 验证手机验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 是否正确
     */
    boolean validatePhoneCode(String phone, String code);
}
