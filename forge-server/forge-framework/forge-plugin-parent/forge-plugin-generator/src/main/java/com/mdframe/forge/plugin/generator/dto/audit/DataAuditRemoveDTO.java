package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已启用审计对象的逻辑删除入口。
 */
@Data
public class DataAuditRemoveDTO {

    private List<String> ids = new ArrayList<>();

    private List<Long> expectedRevisions = new ArrayList<>();

    private String reason;
}
