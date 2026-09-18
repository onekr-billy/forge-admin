package com.mdframe.forge.starter.collaboration.model;

import java.time.Instant;

/**
 * 服务端验证后的外部身份。
 * <p>
 * 只能由 LoginConnector 通过平台官方接口换取产生，禁止信任前端自报的 uuid/tenantId。
 *
 * @param tenantId       租户 ID
 * @param connectionId   连接 ID
 * @param connectionCode 连接编码
 * @param platform       平台编码
 * @param externalUserId 平台侧用户唯一标识（企微 userid / 开放平台 unionId）
 * @param nickname       昵称（可为空）
 * @param avatar         头像（可为空）
 * @param email          邮箱（可为空）
 * @param phone          手机号（可为空，仅平台授权 snsapi_privateinfo 后经官方接口换取）
 * @param verifiedAt     服务端验证时间
 */
public record VerifiedSocialIdentity(
        Long tenantId,
        Long connectionId,
        String connectionCode,
        String platform,
        String externalUserId,
        String nickname,
        String avatar,
        String email,
        String phone,
        Instant verifiedAt
) {

    public VerifiedSocialIdentity withTenantId(Long newTenantId) {
        return new VerifiedSocialIdentity(
                newTenantId,
                connectionId,
                connectionCode,
                platform,
                externalUserId,
                nickname,
                avatar,
                email,
                phone,
                verifiedAt);
    }
}
