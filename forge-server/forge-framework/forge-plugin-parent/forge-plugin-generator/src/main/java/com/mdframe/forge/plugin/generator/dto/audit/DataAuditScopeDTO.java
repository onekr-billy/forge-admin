package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataAuditScopeDTO {

    private List<Long> objectIds = new ArrayList<>();
}
