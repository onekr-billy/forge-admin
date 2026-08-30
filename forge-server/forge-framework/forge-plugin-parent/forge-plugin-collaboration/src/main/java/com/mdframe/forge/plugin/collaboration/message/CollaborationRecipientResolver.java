package com.mdframe.forge.plugin.collaboration.message;

import com.mdframe.forge.plugin.collaboration.domain.CollaborationDirectoryStatus;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.mapper.SysUserSocialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 企业协同消息接收人解析器（Task 13）。
 * <p>
 * 批量读取连接内的用户身份绑定，把 Forge 用户区分为可发送（有活动绑定且外部状态可用）、
 * 未映射、绑定停用三类；未映射与停用接收人必须明确失败/跳过，禁止静默丢弃。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationRecipientResolver {

    private final SysUserSocialMapper userSocialMapper;

    /**
     * 解析连接内接收人映射
     *
     * @param tenantId     租户ID
     * @param connectionId 企业协同连接ID
     * @param forgeUserIds Forge 用户ID集合
     * @return 解析结果（可发送映射 + 未映射 + 停用列表）
     */
    public RecipientResolution resolve(Long tenantId, Long connectionId, Collection<Long> forgeUserIds) {
        Set<Long> distinctUserIds = forgeUserIds == null ? Set.of() : new LinkedHashSet<>(forgeUserIds);
        if (distinctUserIds.isEmpty()) {
            return new RecipientResolution(Map.of(), List.of(), List.of());
        }
        List<SysUserSocial> bindings = userSocialMapper.selectBindingsByUsers(
                tenantId, connectionId, distinctUserIds);
        Map<Long, SysUserSocial> bindingByUser = new LinkedHashMap<>();
        for (SysUserSocial binding : bindings) {
            if (binding.getUserId() != null) {
                bindingByUser.putIfAbsent(binding.getUserId(), binding);
            }
        }
        Map<Long, String> sendable = new LinkedHashMap<>();
        List<Long> unmapped = new ArrayList<>();
        List<Long> disabled = new ArrayList<>();
        for (Long userId : distinctUserIds) {
            SysUserSocial binding = bindingByUser.get(userId);
            if (binding == null || !StringUtils.hasText(binding.getUuid())) {
                unmapped.add(userId);
            } else if (isInactive(binding.getExternalStatus())) {
                disabled.add(userId);
            } else {
                sendable.put(userId, binding.getUuid());
            }
        }
        if (!unmapped.isEmpty() || !disabled.isEmpty()) {
            log.info("企业协同接收人解析存在不可投递用户: connectionId={}, total={}, unmapped={}, disabled={}",
                    connectionId, distinctUserIds.size(), unmapped.size(), disabled.size());
        }
        return new RecipientResolution(sendable, unmapped, disabled);
    }

    private boolean isInactive(String externalStatus) {
        return CollaborationDirectoryStatus.DISABLED.matches(externalStatus)
                || CollaborationDirectoryStatus.DELETED.matches(externalStatus);
    }

    /**
     * 接收人解析结果
     *
     * @param sendable        可投递用户：Forge 用户ID → 平台侧用户ID
     * @param unmappedUserIds 无活动绑定的用户
     * @param disabledUserIds 绑定存在但外部账号停用/删除的用户
     */
    public record RecipientResolution(Map<Long, String> sendable,
                                      List<Long> unmappedUserIds,
                                      List<Long> disabledUserIds) {

        public RecipientResolution {
            sendable = sendable == null ? Map.of() : Map.copyOf(sendable);
            unmappedUserIds = unmappedUserIds == null ? List.of() : List.copyOf(unmappedUserIds);
            disabledUserIds = disabledUserIds == null ? List.of() : List.copyOf(disabledUserIds);
        }
    }
}
