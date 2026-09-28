package com.mdframe.forge.starter.flow.service;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.entity.FlowTaskCandidate;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import com.mdframe.forge.starter.flow.enums.FlowTaskCandidateStatus;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskCandidateMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** 将投影快照幂等写入任务、候选人、业务状态和表单状态镜像。 */
@Component
@RequiredArgsConstructor
public class FlowProjectionHandler {

    private final FlowTaskMapper taskMapper;
    private final FlowTaskCandidateMapper candidateMapper;
    private final FlowBusinessMapper businessMapper;
    private final FlowFormInstanceMapper formInstanceMapper;

    public void apply(FlowProjectionOutbox outbox, FlowProjectionOutboxPayload payload) {
        validate(outbox, payload);
        FlowProjectionType type = payload.getType();
        switch (type) {
            case TASK_CREATED -> applyTask(outbox, payload.getTask(), true);
            case TASK_COMPLETED, TASK_ASSIGNED, TASK_CANCELED ->
                    applyTask(outbox, payload.getTask(), false);
            case PROCESS_COMPLETED, PROCESS_REJECTED, PROCESS_CANCELED ->
                    applyProcess(outbox, payload.getBusiness(), payload.getFormStatus());
            default -> throw new IllegalArgumentException("FLOW_PROJECTION_TYPE_UNSUPPORTED");
        }
    }

    private void applyTask(FlowProjectionOutbox outbox, FlowTask task, boolean syncCandidates) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            throw new IllegalArgumentException("FLOW_PROJECTION_TASK_REQUIRED");
        }
        Long tenantId = outbox.getTenantId();
        task.setTenantId(tenantId);
        int updated = TenantContextHolder.executeIgnore(() -> taskMapper.applyProjection(
                tenantId, task.getTaskId(), task, outbox.getEventId(), outbox.getId()));
        if (updated == 0) {
            FlowTask existing = TenantContextHolder.executeIgnore(
                    () -> taskMapper.selectByTaskIdAndTenant(task.getTaskId(), tenantId));
            if (existing == null) {
                if (task.getId() == null || task.getId().isBlank()) {
                    task.setId(UUID.randomUUID().toString().replace("-", ""));
                }
                task.setProjectionEventId(outbox.getEventId());
                task.setProjectionSequence(outbox.getId());
                TenantContextHolder.executeIgnore(() -> taskMapper.insert(task));
            } else if (sequence(existing.getProjectionSequence()) < outbox.getId()) {
                throw new IllegalStateException("FLOW_TASK_PROJECTION_NOT_APPLIED");
            }
        }
        if (syncCandidates) {
            syncCandidateValues(outbox, task, task.getCandidateUsers(), FlowTaskCandidate.TYPE_USER);
            syncCandidateValues(outbox, task, task.getCandidateGroups(), FlowTaskCandidate.TYPE_GROUP);
        }
    }

    private void syncCandidateValues(FlowProjectionOutbox outbox, FlowTask task,
                                     String values, String candidateType) {
        if (values == null || values.isBlank()) {
            return;
        }
        for (String raw : values.split("[,;，；、]")) {
            String value = raw == null ? null : raw.trim();
            if (value == null || value.isEmpty()) {
                continue;
            }
            FlowTaskCandidate candidate = new FlowTaskCandidate();
            candidate.setTenantId(outbox.getTenantId());
            candidate.setTaskId(task.getTaskId());
            candidate.setProcessInstanceId(task.getProcessInstanceId());
            candidate.setCandidateType(candidateType);
            candidate.setCandidateValue(value);
            candidate.setSource(FlowTaskCandidate.SOURCE_FLOWABLE);
            candidate.setStatus(FlowTaskCandidateStatus.ACTIVE.getCode());
            candidate.setCreateTime(task.getCreateTime());
            candidate.setUpdateTime(LocalDateTime.now());
            TenantContextHolder.executeIgnore(() -> candidateMapper.upsertProjection(
                    candidate, outbox.getEventId(), outbox.getId()));
        }
    }

    private void applyProcess(FlowProjectionOutbox outbox, FlowBusiness business, String formStatus) {
        if (business == null || business.getProcessInstanceId() == null
                || business.getProcessInstanceId().isBlank() || business.getStatus() == null) {
            throw new IllegalArgumentException("FLOW_PROJECTION_BUSINESS_REQUIRED");
        }
        Long tenantId = outbox.getTenantId();
        String processInstanceId = business.getProcessInstanceId();
        int updated = TenantContextHolder.executeIgnore(() -> businessMapper.applyProjection(
                tenantId, processInstanceId, business.getStatus(), business.getEndTime(),
                business.getDuration(), outbox.getEventId(), outbox.getId()));
        if (updated == 0) {
            FlowBusiness existing = TenantContextHolder.executeIgnore(
                    () -> businessMapper.selectByProcessInstanceIdAndTenantId(processInstanceId, tenantId));
            if (existing == null) {
                throw new IllegalStateException("FLOW_BUSINESS_PROJECTION_TARGET_MISSING");
            }
            if (sequence(existing.getProjectionSequence()) < outbox.getId()) {
                throw new IllegalStateException("FLOW_BUSINESS_PROJECTION_NOT_APPLIED");
            }
        }
        if (formStatus != null && !formStatus.isBlank()) {
            TenantContextHolder.executeIgnore(() -> formInstanceMapper.applyProjectionStatus(
                    processInstanceId, formStatus, tenantId, outbox.getEventId(), outbox.getId()));
        }
    }

    private void validate(FlowProjectionOutbox outbox, FlowProjectionOutboxPayload payload) {
        if (outbox == null || outbox.getId() == null || outbox.getTenantId() == null
                || outbox.getTenantId() <= 0 || outbox.getEventId() == null
                || outbox.getEventId().isBlank() || payload == null || payload.getType() == null) {
            throw new IllegalArgumentException("FLOW_PROJECTION_OUTBOX_INVALID");
        }
    }

    private long sequence(Long value) {
        return value == null ? 0L : value;
    }
}
