package com.mdframe.forge.flow.vo;

import lombok.Data;

import java.util.List;

/** 流程通知死信分页响应。 */
@Data
public class FlowNotifyDeadLetterPageVO {

    private List<FlowNotifyDeadLetterVO> list;
    private long total;
    private long pageNum;
    private long pageSize;
}
