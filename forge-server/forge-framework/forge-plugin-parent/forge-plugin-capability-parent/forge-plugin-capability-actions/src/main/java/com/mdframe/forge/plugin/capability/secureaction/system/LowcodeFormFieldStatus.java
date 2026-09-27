package com.mdframe.forge.plugin.capability.secureaction.system;

/** 发布模型中影响外部表单填写的字段状态，不修改模型原有存储协议。 */
enum LowcodeFormFieldStatus {
    ENABLED("ENABLED"), HIDDEN("HIDDEN"), DISABLED("DISABLED");

    private final String code;

    LowcodeFormFieldStatus(String code) { this.code = code; }

    public String getCode() { return code; }

    public boolean matches(String value) { return code.equalsIgnoreCase(value); }
}
