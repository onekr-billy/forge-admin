package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.system.dto.SysNoticeQuery;
import org.apache.ibatis.annotations.Param;
import com.mdframe.forge.plugin.system.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知公告Mapper接口
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    Page<SysNotice> selectUserNoticePage(Page<SysNotice> page,
                                         @Param("query") SysNoticeQuery query,
                                         @Param("userId") Long userId,
                                         @Param("noticeId") Long noticeId);

    Integer countUserUnreadNotices(@Param("userId") Long userId);
}
