package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishTaskReplayDTO;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodePublishTaskReplayControllerContractTest {

    @Test
    void replayUsesDedicatedPermissionTypedDtoAndRedactedAuditLog() throws Exception {
        Method replay = LowcodeAppController.class.getMethod(
                "replayPublishTask", Long.class, LowcodePublishTaskReplayDTO.class);

        PostMapping mapping = replay.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains(
                "/publish-tasks/{taskId}/replay"));

        SaCheckPermission permission = replay.getAnnotation(SaCheckPermission.class);
        assertNotNull(permission);
        assertTrue(Arrays.asList(permission.value()).contains(
                "ai:lowcode:publish-task:replay"));

        OperationLog operationLog = replay.getAnnotation(OperationLog.class);
        assertNotNull(operationLog);
        assertFalse(operationLog.saveRequestParams());
        assertFalse(operationLog.saveResponseResult());

        Annotation[] dtoAnnotations = replay.getParameterAnnotations()[1];
        assertTrue(hasAnnotation(dtoAnnotations, Valid.class));
        assertTrue(hasAnnotation(dtoAnnotations, RequestBody.class));
    }

    @Test
    void replayReasonIsRequiredAndBounded() throws Exception {
        Field reason = LowcodePublishTaskReplayDTO.class.getDeclaredField("reason");

        assertNotNull(reason.getAnnotation(NotBlank.class));
        Size size = reason.getAnnotation(Size.class);
        assertNotNull(size);
        assertEquals(500, size.max());
    }

    private boolean hasAnnotation(Annotation[] annotations,
                                  Class<? extends Annotation> annotationType) {
        return Arrays.stream(annotations)
                .anyMatch(annotation -> annotation.annotationType().equals(annotationType));
    }
}
