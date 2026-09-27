package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessNodeRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunDetailVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessProcessRunViewAssemblerTest {

    @Test
    void mapsSnowflakeIdsAsStringsAndKeepsTimelineFields() {
        AiBusinessProcessRun run = new AiBusinessProcessRun();
        run.setId(9007199254740993L);
        run.setProcessVersionId(77L);
        run.setActorUserId(88L);
        run.setBusinessKey("order:1");
        run.setFlowProcessInstanceId("flow-1");
        AiBusinessProcessNodeRun attempt = new AiBusinessProcessNodeRun();
        attempt.setId(99L);
        attempt.setRunId(run.getId());
        attempt.setNodeId("approve");
        attempt.setAttemptNo(2);

        BusinessProcessRunDetailVO detail = BusinessProcessRunViewAssembler.toDetail(run);
        BusinessProcessRunDetailVO.NodeRunVO node = BusinessProcessRunViewAssembler.toNodeVo(attempt);

        assertEquals("9007199254740993", detail.getId());
        assertEquals("77", detail.getProcessVersionId());
        assertEquals("88", detail.getActorUserId());
        assertEquals("flow-1", detail.getFlowProcessInstanceId());
        assertEquals("9007199254740993", node.getRunId());
        assertEquals("approve", node.getNodeId());
        assertEquals(2, node.getAttemptNo());
    }

    @Test
    void pageResolvesEachPublishedVersionOnceAndFallsBackForUnknownNode() {
        BusinessProcessVersionMapper mapper = mock(BusinessProcessVersionMapper.class);
        BusinessProcessSchemaValidator validator = mock(BusinessProcessSchemaValidator.class);
        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setSchemaJson("snapshot");
        when(mapper.selectPublishedVersionById(1L, 77L)).thenReturn(version);
        BusinessProcessNode approve = new BusinessProcessNode();
        approve.setId("approve");
        approve.setName("经理审批");
        BusinessProcessSchema schema = new BusinessProcessSchema();
        schema.setNodes(List.of(approve));
        when(validator.normalize("snapshot")).thenReturn(schema);
        BusinessProcessRunVO first = runView("approve");
        BusinessProcessRunVO second = runView("missing");

        BusinessProcessRunViewAssembler.enrichNodeNames(
                1L, List.of(first, second), mapper, validator);

        assertEquals("经理审批", first.getCurrentNodeName());
        assertEquals("missing", second.getCurrentNodeName());
        verify(mapper, times(1)).selectPublishedVersionById(1L, 77L);
    }

    @Test
    void missingPublishedVersionReturnsEmptyNameMap() {
        BusinessProcessVersionMapper mapper = mock(BusinessProcessVersionMapper.class);

        assertTrue(BusinessProcessRunViewAssembler.loadNodeNameMap(
                1L, 77L, mapper, mock(BusinessProcessSchemaValidator.class)).isEmpty());
    }

    private BusinessProcessRunVO runView(String nodeId) {
        BusinessProcessRunVO vo = new BusinessProcessRunVO();
        vo.setProcessVersionId("77");
        vo.setCurrentNodeId(nodeId);
        return vo;
    }
}
