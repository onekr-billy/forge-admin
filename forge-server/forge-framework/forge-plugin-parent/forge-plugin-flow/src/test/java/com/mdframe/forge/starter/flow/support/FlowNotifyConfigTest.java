package com.mdframe.forge.starter.flow.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowNotifyConfigTest {

    @Test
    void parsesEventChannelsAndTemplateOverride() {
        FlowNotifyConfig config = FlowNotifyConfig.parse("""
                {
                  "todo": {"channels": ["WEB", "COLLABORATION"], "templateCode": "FLOW_TODO_CUSTOM"},
                  "result": {"channels": ["WEB", "EMAIL"]}
                }
                """);

        assertEquals(2, config.channelConfigOf(FlowNotifyConfig.EVENT_TODO).getChannels().size());
        assertEquals("FLOW_TODO_CUSTOM",
                config.channelConfigOf(FlowNotifyConfig.EVENT_TODO).getTemplateCode());
        assertEquals(2, config.channelConfigOf(FlowNotifyConfig.EVENT_RESULT).getChannels().size());
        assertNull(config.channelConfigOf(FlowNotifyConfig.EVENT_CC));
    }

    @Test
    void invalidOrEmptyJsonFallsBackToLegacyBehavior() {
        assertNull(FlowNotifyConfig.parse(null));
        assertNull(FlowNotifyConfig.parse("   "));
        assertNull(FlowNotifyConfig.parse("not-json"));
        assertNull(FlowNotifyConfig.parse("{}"));
        FlowNotifyConfig emptyTodo = FlowNotifyConfig.parse("{\"todo\":{\"channels\":[]}}");
        assertTrue(emptyTodo != null);
        assertEquals(java.util.List.of("WEB"),
                emptyTodo.channelConfigOf(FlowNotifyConfig.EVENT_TODO).getChannels());
    }

    @Test
    void normalizesMandatoryAndUnsupportedChannels() {
        FlowNotifyConfig config = FlowNotifyConfig.parse("""
                {"todo":{"channels":["email"]},"cc":{"channels":["WEB","SMS"]}}
                """);

        assertEquals(java.util.List.of("WEB", "EMAIL"),
                config.channelConfigOf(FlowNotifyConfig.EVENT_TODO).getChannels());
        assertEquals(java.util.List.of("WEB"),
                config.channelConfigOf(FlowNotifyConfig.EVENT_CC).getChannels());
    }
}
