package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Flow 通知进入低代码业务状态机前的可靠消费记录。 */
@Data
@TableName("ai_business_flow_callback_inbox")
public class AiBusinessFlowCallbackInbox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String eventId;
    private Integer eventVersion;
    private Long eventSequence;
    private String aggregateKey;
    private String eventType;
    private String processInstanceId;
    private String businessKey;
    private String taskId;
    private String eventDigest;
    private String eventPayload;
    private String consumeStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockTime;
    private String errorType;
    private LocalDateTime completedTime;
    private Long createBy;
    private LocalDateTime createTime;
    private Long createDept;
    private Long updateBy;
    private LocalDateTime updateTime;
}
