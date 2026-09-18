package com.mdframe.forge.starter.core.constant;

/**
 * 流程委托会话相关常量。
 * <p>
 * 委托会话由 {@code SaTokenFlowTokenProvider} 在服务侧调用流程服务前签发，
 * 设备标识以 {@link #FLOW_DELEGATION_DEVICE_PREFIX} 开头，
 * 供登录/在线日志监听器识别并归类，避免把临时会话计入用户登录行为。
 *
 * @author forge
 */
public final class FlowDelegationConstants {

    /**
     * 流程委托临时会话的设备标识前缀（完整格式：mcp-flow:{clientId}:{orgId}:{sessionId}）。
     * 委托会话生命周期极短且不写入在线用户表，日志监听器不应将其计入用户登录/在线行为。
     */
    public static final String FLOW_DELEGATION_DEVICE_PREFIX = "mcp-flow:";

    private FlowDelegationConstants() {
    }
}
