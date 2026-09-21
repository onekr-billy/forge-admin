package com.mdframe.forge.plugin.generator.enums;

/** Real external delivery currently supported by the low-code runtime. */
public enum BusinessMessageChannelType {
    COLLABORATION;

    public String getCode() { return name(); }
    public boolean matches(String value) { return name().equals(value); }
}
