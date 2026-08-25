package com.mdframe.forge.plugin.message.domain;

import lombok.Getter;

/**
 * 消息发送状态，对应 {@code sys_message.status} / {@code sys_message_send_record.status}。
 */
@Getter
public enum MessageSendStatus {

    SENDING(0, "发送中"),
    SUCCESS(1, "已发送"),
    FAILED(2, "发送失败"),
    PARTIAL(3, "部分成功");

    private final int code;
    private final String label;

    MessageSendStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }
}
