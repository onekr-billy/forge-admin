package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessExtension;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessExtensionLockVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基于条件更新的扩展编辑锁。
 */
@Service
public class BusinessExtensionLockService {

    private static final long LOCK_MINUTES = 10L;

    private final BusinessExtensionMapper extensionMapper;

    public BusinessExtensionLockService(BusinessExtensionMapper extensionMapper) {
        this.extensionMapper = extensionMapper;
    }

    public BusinessExtensionLockVO acquire(Long extensionId) {
        requireId(extensionId);
        IdentitySnapshot identity = requireIdentity();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(LOCK_MINUTES);
        String token = UUID.randomUUID().toString().replace("-", "");
        int affected = extensionMapper.tryAcquireLock(
                identity.tenantId(), extensionId, identity.userId(), identity.username(),
                hashToken(token), now, expireTime);
        if (affected == 0) {
            AiBusinessExtension current = extensionMapper.selectEntityById(identity.tenantId(), extensionId);
            if (current == null) {
                throw new BusinessException("业务扩展不存在");
            }
            throw new BusinessException("扩展正由 "
                    + StringUtils.defaultIfBlank(current.getLockUsername(), "其他用户")
                    + " 编辑，锁将在 " + current.getLockExpireTime() + " 后释放");
        }
        return lockVO(extensionId, identity.userId(), identity.username(), token, expireTime);
    }

    public BusinessExtensionLockVO renew(Long extensionId, String lockToken) {
        requireId(extensionId);
        requireToken(lockToken);
        IdentitySnapshot identity = requireIdentity();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(LOCK_MINUTES);
        if (extensionMapper.renewLock(identity.tenantId(), extensionId, identity.userId(),
                hashToken(lockToken), now, expireTime) == 0) {
            throw new BusinessException("编辑锁已失效，请重新获取");
        }
        return lockVO(extensionId, identity.userId(), identity.username(), lockToken, expireTime);
    }

    public void release(Long extensionId, String lockToken) {
        requireId(extensionId);
        requireToken(lockToken);
        IdentitySnapshot identity = requireIdentity();
        if (extensionMapper.releaseLock(identity.tenantId(), extensionId,
                identity.userId(), hashToken(lockToken)) == 0) {
            throw new BusinessException("不能释放其他用户或其他租户的编辑锁");
        }
    }

    public void assertOwned(Long extensionId, String lockToken) {
        requireId(extensionId);
        requireToken(lockToken);
        IdentitySnapshot identity = requireIdentity();
        Long count = extensionMapper.countOwnedLock(
                identity.tenantId(), extensionId, identity.userId(),
                hashToken(lockToken), LocalDateTime.now());
        if (count == null || count == 0L) {
            throw new BusinessException("编辑锁不存在或已过期，请重新打开编辑器");
        }
    }

    private BusinessExtensionLockVO lockVO(Long extensionId, Long userId, String username,
                                            String lockToken, LocalDateTime expireTime) {
        BusinessExtensionLockVO vo = new BusinessExtensionLockVO();
        vo.setExtensionId(extensionId);
        vo.setHolderUserId(userId);
        vo.setHolderUsername(username);
        vo.setLockToken(lockToken);
        vo.setExpireTime(expireTime);
        return vo;
    }

    private void requireId(Long extensionId) {
        if (extensionId == null || extensionId <= 0) {
            throw new BusinessException("业务扩展ID不能为空");
        }
    }

    private void requireToken(String lockToken) {
        if (StringUtils.isBlank(lockToken) || lockToken.length() > 64) {
            throw new BusinessException("编辑锁令牌不正确");
        }
    }

    private String hashToken(String lockToken) {
        return DigestUtils.sha256Hex(lockToken);
    }

    private IdentitySnapshot requireIdentity() {
        Long tenantId;
        Long userId;
        String username;
        try {
            tenantId = SessionHelper.getTenantId();
            userId = SessionHelper.getUserId();
            username = SessionHelper.getUsername();
        } catch (Exception e) {
            tenantId = null;
            userId = null;
            username = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("扩展编辑锁缺少可信租户上下文");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("扩展编辑锁缺少可信用户");
        }
        return new IdentitySnapshot(
                tenantId, userId, StringUtils.defaultIfBlank(username, "用户"));
    }

    private record IdentitySnapshot(Long tenantId, Long userId, String username) {
    }
}
