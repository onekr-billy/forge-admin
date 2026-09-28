package com.mdframe.forge.plugin.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Password credential history persistence.
 *
 * <p>Only one-way password hashes are stored. The table is an internal security
 * control and is never exposed through user management APIs.</p>
 */
@Mapper
public interface SysUserPasswordHistoryMapper {

    List<String> selectRecentPasswordHashes(@Param("tenantId") Long tenantId,
                                             @Param("userId") Long userId,
                                             @Param("limit") int limit);

    int insertPasswordHistory(@Param("tenantId") Long tenantId,
                              @Param("userId") Long userId,
                              @Param("passwordHash") String passwordHash,
                              @Param("changedTime") LocalDateTime changedTime);

    int deleteOlderPasswordHistory(@Param("tenantId") Long tenantId,
                                   @Param("userId") Long userId,
                                   @Param("retainCount") int retainCount);

    int deleteAllPasswordHistory(@Param("tenantId") Long tenantId,
                                 @Param("userId") Long userId);
}
