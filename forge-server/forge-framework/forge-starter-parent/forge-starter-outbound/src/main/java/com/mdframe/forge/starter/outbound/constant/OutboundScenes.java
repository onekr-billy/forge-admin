package com.mdframe.forge.starter.outbound.constant;

import java.util.Set;

public final class OutboundScenes {

    public static final String JOB_WEBHOOK = "JOB_WEBHOOK";
    public static final String JOB_RPC = "JOB_RPC";
    public static final String FLOW_API = "FLOW_API";
    public static final String COLLABORATION_PROVIDER = "COLLABORATION_PROVIDER";
    public static final String EXTERNAL_CONNECTOR = "EXTERNAL_CONNECTOR";
    public static final Set<String> SUPPORTED = Set.of(
            JOB_WEBHOOK, JOB_RPC, FLOW_API, COLLABORATION_PROVIDER, EXTERNAL_CONNECTOR);

    private OutboundScenes() {
    }
}
