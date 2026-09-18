package com.mdframe.forge.starter.social.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.community.GiteeCommunityLoginSupport;
import com.mdframe.forge.starter.social.domain.dto.LoginClientContext;
import com.mdframe.forge.starter.social.domain.dto.SocialOAuthIntent;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * OAuth state 与登录票据服务。
 * <p>
 * state 与票据均为服务端签发的一次性随机凭据，短有效期，消费即删除；
 * 删除失败视为重放，失败关闭。登录身份只能通过票据取回，禁止前端自报。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialOAuthStateService {

    private static final String STATE_KEY_PREFIX = "social:oauth:state:";
    private static final String TICKET_KEY_PREFIX = "social:oauth:ticket:";

    /**
     * state 有效期（秒）：覆盖用户在三方授权页的停留时间
     */
    public static final long STATE_TTL_SECONDS = 300;

    /**
     * 登录票据有效期（秒）：回调换票后立即用于 /auth/login
     */
    public static final long TICKET_TTL_SECONDS = 120;

    private final ICacheService cacheService;
    private final GiteeCommunityLoginSupport giteeCommunityLoginSupport;

    /**
     * 签发 state 并保存授权意图。
     * <p>
     * state 保持 [bind_]platform_random 格式，兼容前端 callback 页对平台的解析。
     */
    public String issueState(SocialOAuthIntent intent) {
        if (intent == null || StrUtil.isBlank(intent.getPlatform()) || intent.getConnectionId() == null) {
            throw new BusinessException("授权意图不完整");
        }
        if (StrUtil.isBlank(intent.getAction())) {
            intent.setAction(SocialOAuthIntent.ACTION_LOGIN);
        }
        intent.setIssuedAt(System.currentTimeMillis());
        String prefix = SocialOAuthIntent.ACTION_BIND.equals(intent.getAction()) ? "bind_" : "";
        String state = prefix + intent.getPlatform() + "_" + IdUtil.fastSimpleUUID();
        cacheService.set(STATE_KEY_PREFIX + state, JSON.toJSONString(intent), STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return state;
    }

    /**
     * 消费 state：取回授权意图并原子删除；不存在或已被消费均失败关闭
     */
    public SocialOAuthIntent consumeState(String state) {
        if (StrUtil.isBlank(state)) {
            throw new BusinessException("state 不能为空");
        }
        String key = STATE_KEY_PREFIX + state;
        Object cached = cacheService.get(key);
        if (cached == null) {
            throw new BusinessException("授权已过期或 state 无效，请重新发起登录");
        }
        if (!cacheService.delete(key)) {
            log.warn("OAuth state 重放被拒绝: state={}", state);
            throw new BusinessException("授权凭据已被使用，请重新发起登录");
        }
        SocialOAuthIntent intent = JSON.parseObject(String.valueOf(cached), SocialOAuthIntent.class);
        if (intent == null || intent.getConnectionId() == null) {
            throw new BusinessException("授权意图解析失败");
        }
        return intent;
    }

    /**
     * 签发一次性登录票据，绑定已验证身份与发起客户端
     */
    public String issueLoginTicket(VerifiedSocialIdentity identity, LoginClientContext client) {
        if (identity == null || identity.tenantId() == null || identity.connectionId() == null
                || StrUtil.isBlank(identity.externalUserId())) {
            throw new BusinessException("外部身份不完整，禁止签发登录票据");
        }
        TicketPayload payload = new TicketPayload();
        payload.setTenantId(identity.tenantId());
        payload.setConnectionId(identity.connectionId());
        payload.setConnectionCode(identity.connectionCode());
        payload.setPlatform(identity.platform());
        payload.setExternalUserId(identity.externalUserId());
        payload.setNickname(identity.nickname());
        payload.setAvatar(identity.avatar());
        payload.setEmail(identity.email());
        payload.setPhone(identity.phone());
        payload.setVerifiedAt(identity.verifiedAt() != null ? identity.verifiedAt().toEpochMilli() : System.currentTimeMillis());
        payload.setUserClient(client != null ? client.userClient() : null);

        String ticket = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        cacheService.set(TICKET_KEY_PREFIX + ticket, JSON.toJSONString(payload), TICKET_TTL_SECONDS, TimeUnit.SECONDS);
        return ticket;
    }

    /**
     * 消费登录票据：一次性删除，并校验客户端与租户一致性；任何不一致失败关闭
     */
    public VerifiedSocialIdentity consumeLoginTicket(String ticket, LoginClientContext client) {
        if (StrUtil.isBlank(ticket)) {
            throw new BusinessException("登录票据不能为空");
        }
        String key = TICKET_KEY_PREFIX + ticket;
        Object cached = cacheService.get(key);
        if (cached == null) {
            throw new BusinessException("登录票据已过期或无效，请重新登录");
        }
        if (!cacheService.delete(key)) {
            log.warn("三方登录票据重放被拒绝");
            throw new BusinessException("登录票据已被使用，请重新登录");
        }
        TicketPayload payload = JSON.parseObject(String.valueOf(cached), TicketPayload.class);
        if (payload == null || payload.getTenantId() == null || payload.getConnectionId() == null) {
            throw new BusinessException("登录票据解析失败");
        }
        if (client != null) {
            // 客户端类型必须与票据签发时一致，防止票据跨端挪用
            if (StrUtil.isNotBlank(payload.getUserClient())
                    && StrUtil.isNotBlank(client.userClient())
                    && !payload.getUserClient().equals(client.userClient())) {
                throw new BusinessException("登录客户端与授权客户端不一致");
            }
            // 登录请求声明租户时必须与票据租户一致。Gitee 社区体验登录会把身份改写到隔离租户。
            if (client.tenantId() != null && !client.tenantId().equals(payload.getTenantId())) {
                boolean communityTicket = giteeCommunityLoginSupport != null
                        && giteeCommunityLoginSupport.isEnabled()
                        && "GITEE".equalsIgnoreCase(payload.getPlatform())
                        && payload.getTenantId().equals(giteeCommunityLoginSupport.communityTenantId());
                if (!communityTicket) {
                    throw new BusinessException("登录租户与授权租户不一致");
                }
            }
        }
        return new VerifiedSocialIdentity(
                payload.getTenantId(),
                payload.getConnectionId(),
                payload.getConnectionCode(),
                payload.getPlatform(),
                payload.getExternalUserId(),
                payload.getNickname(),
                payload.getAvatar(),
                payload.getEmail(),
                payload.getPhone(),
                Instant.ofEpochMilli(payload.getVerifiedAt()));
    }

    /**
     * 票据缓存载荷（JSON 序列化，不含任何 Token/Secret）
     */
    @Data
    public static class TicketPayload {
        private Long tenantId;
        private Long connectionId;
        private String connectionCode;
        private String platform;
        private String externalUserId;
        private String nickname;
        private String avatar;
        private String email;
        private String phone;
        private long verifiedAt;
        private String userClient;
    }
}
