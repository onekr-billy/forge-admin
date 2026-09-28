package com.mdframe.forge.plugin.external.adapter.impl;

import com.mdframe.forge.plugin.external.adapter.DataAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

@Component
public class ScriptAdapter implements DataAdapter {

    private final ScriptEngineManager scriptEngineManager;

    public ScriptAdapter() {
        this(new ScriptEngineManager());
    }

    ScriptAdapter(ScriptEngineManager scriptEngineManager) {
        this.scriptEngineManager = scriptEngineManager;
    }

    @Override
    public String getAdapterType() {
        return "Script";
    }

    @Override
    public Object transform(Object originalData, String adapterConfig) {
        if (adapterConfig == null || adapterConfig.isBlank()) {
            throw new BusinessException("响应转换脚本不能为空");
        }
        ScriptEngine engine = requireScriptEngine();

        try {
            engine.put("response", originalData);
            engine.eval(adapterConfig);
            return engine.get("result");
        } catch (ScriptException e) {
            throw new BusinessException("响应转换脚本执行失败，请检查脚本语法和返回值");
        }
    }

    @Override
    public boolean validateConfig(String adapterConfig) {
        if (adapterConfig == null || adapterConfig.isEmpty()) {
            return false;
        }
        ScriptEngine engine = scriptEngineManager.getEngineByName("javascript");
        if (engine == null) {
            return false;
        }
        try {
            engine.eval(adapterConfig);
            return true;
        } catch (ScriptException e) {
            return false;
        }
    }

    private ScriptEngine requireScriptEngine() {
        ScriptEngine engine = scriptEngineManager.getEngineByName("javascript");
        if (engine == null) {
            throw new BusinessException("当前运行环境未启用安全脚本引擎，无法执行响应转换脚本");
        }
        return engine;
    }
}
