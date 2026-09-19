package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * 流程实例关联（ai_business_flow_instance_link）上的运行态标记。
 * <p>
 * 发起人修改节点的待办信息由流程回调写入、由单据运行态读取，两边共用本类解析
 * {@code variables_snapshot} 中的保留键，避免各自重复实现 JSON 解析导致结构漂移。
 */
final class BusinessFlowLinkRuntimeState {

    /** 保留键，与流程变量同层存放，命名上明确区分平台自用数据。 */
    private static final String SNAPSHOT_KEY = "forgeFlowRuntime";
    private static final String KEY_MODIFY_TASK = "modifyTask";

    private BusinessFlowLinkRuntimeState() {
    }

    /**
     * 读取发起人修改节点待办。没有待办时返回 {@code null}。
     */
    static ModifyTask readModifyTask(String variablesSnapshot) {
        JSONObject runtime = readRuntime(variablesSnapshot);
        JSONObject task = runtime.getJSONObject(KEY_MODIFY_TASK);
        if (task == null || StringUtils.isBlank(task.getString("taskId"))) {
            return null;
        }
        return new ModifyTask(
                task.getString("taskId"),
                task.getString("taskDefKey"),
                task.getString("taskName"),
                task.getString("assigneeId"));
    }

    /**
     * 写入或清除发起人修改节点待办，返回新的快照 JSON。
     * <p>
     * 必须基于原快照合并，{@code flowBusinessKey}、{@code statusField} 等发起变量不能丢。
     *
     * @param task 传 {@code null} 表示清除
     */
    static String writeModifyTask(String variablesSnapshot, ModifyTask task) {
        JSONObject snapshot = readJson(variablesSnapshot);
        JSONObject runtime = readRuntime(variablesSnapshot);
        if (task == null) {
            runtime.remove(KEY_MODIFY_TASK);
        } else {
            JSONObject value = new JSONObject();
            value.put("taskId", task.taskId());
            value.put("taskDefKey", task.taskDefKey());
            value.put("taskName", task.taskName());
            value.put("assigneeId", task.assigneeId());
            runtime.put(KEY_MODIFY_TASK, value);
        }
        if (runtime.isEmpty()) {
            snapshot.remove(SNAPSHOT_KEY);
        } else {
            snapshot.put(SNAPSHOT_KEY, runtime);
        }
        return JSON.toJSONString(snapshot);
    }

    private static JSONObject readRuntime(String variablesSnapshot) {
        JSONObject runtime = readJson(variablesSnapshot).getJSONObject(SNAPSHOT_KEY);
        return runtime == null ? new JSONObject() : runtime;
    }

    private static JSONObject readJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = JSON.parseObject(json);
            return parsed == null ? new JSONObject() : parsed;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 发起人修改节点上的待办快照。 */
    record ModifyTask(String taskId, String taskDefKey, String taskName, String assigneeId) {
    }
}
