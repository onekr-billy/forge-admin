package com.mdframe.forge.flow.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNotifyOutboxControllerContractTest {

    @Test
    void deadLetterEndpointsMustUseExplicitPermissionsTypedDtoAndSanitizedVo() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/flow/controller/FlowNotifyOutboxController.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/flow/dto/FlowNotifyOutboxReplayDTO.java"));
        String vo = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/flow/vo/FlowNotifyDeadLetterVO.java"));

        assertThat(controller)
                .contains("@SaCheckPermission(\"flow:monitor:view\")",
                        "@SaCheckPermission(\"flow:monitor:manage\")",
                        "@Valid @RequestBody FlowNotifyOutboxReplayDTO dto",
                        "@RequestParam(defaultValue = \"1\") Integer pageNum")
                .doesNotContain("@RequestBody Map", "@IgnoreTenant");
        assertThat(dto).contains("@NotBlank", "@Size(max = 500");
        assertThat(vo)
                .contains("private String eventId", "private String aggregateId")
                .doesNotContain("payload", "payloadHash");
    }
}
