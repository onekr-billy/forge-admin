package com.mdframe.forge.plugin.generator.businessprocess.validation;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessEdge;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessValidationVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessProcessGraphValidatorTest {

    private final BusinessProcessGraphValidator validator = new BusinessProcessGraphValidator();

    @Test
    void validatesDagAndDelegatesEachRegisteredNodeConfig() {
        BusinessProcessSchema schema = schema(
                List.of(
                        node("start", "START_EVENT", "NEXT"),
                        node("action", "ACTION", "NEXT"),
                        node("end", "END")
                ),
                List.of(
                        edge("e1", "start", "action", "NEXT"),
                        edge("e2", "action", "end", "NEXT")
                ),
                "EVENT_RECORD"
        );
        BusinessProcessValidationVO result = new BusinessProcessValidationVO();
        AtomicInteger configValidations = new AtomicInteger();

        validator.validate(schema, result, (node, path) -> configValidations.incrementAndGet());

        assertTrue(result.finish().isValid());
        assertEquals(3, configValidations.get());
    }

    @Test
    void rejectsCyclesDanglingEdgesAndMissingEndPaths() {
        BusinessProcessSchema schema = schema(
                List.of(
                        node("start", "START_EVENT", "NEXT"),
                        node("action", "ACTION", "NEXT"),
                        node("end", "END")
                ),
                List.of(
                        edge("e1", "start", "action", "NEXT"),
                        edge("e2", "action", "start", "NEXT"),
                        edge("e3", "missing", "end", "NEXT")
                ),
                "EVENT_RECORD"
        );
        BusinessProcessValidationVO result = new BusinessProcessValidationVO();

        validator.validate(schema, result, (node, path) -> { });
        result.finish();

        assertFalse(result.isValid());
        assertTrue(result.hasError("GRAPH_CYCLE"));
        assertTrue(result.hasError("EDGE_SOURCE_MISSING"));
        assertTrue(result.hasError("END_PATH_MISSING"));
    }

    @Test
    void rejectsStartRecordSourceAndDeclaredPortMismatch() {
        BusinessProcessSchema schema = schema(
                List.of(
                        node("start", "START_MANUAL", "CUSTOM"),
                        node("end", "END")
                ),
                List.of(edge("e1", "start", "end", "CUSTOM")),
                "EVENT_RECORD"
        );
        BusinessProcessValidationVO result = new BusinessProcessValidationVO();

        validator.validate(schema, result, (node, path) -> { });
        result.finish();

        assertTrue(result.hasError("RECORD_ID_SOURCE_MISMATCH"));
        assertTrue(result.hasError("NODE_PORTS_INVALID"));
        assertTrue(result.hasError("EDGE_PORT_INVALID"));
    }

    private BusinessProcessSchema schema(List<BusinessProcessNode> nodes,
                                         List<BusinessProcessEdge> edges,
                                         String recordIdSource) {
        BusinessProcessSchema schema = new BusinessProcessSchema();
        schema.setNodes(new ArrayList<>(nodes));
        schema.setEdges(new ArrayList<>(edges));
        BusinessProcessSchema.Subject subject = new BusinessProcessSchema.Subject();
        subject.setRecordIdSource(recordIdSource);
        schema.setSubject(subject);
        return schema;
    }

    private BusinessProcessNode node(String id, String type, String... ports) {
        BusinessProcessNode node = new BusinessProcessNode();
        node.setId(id);
        node.setType(type);
        node.setName(id);
        node.setPorts(List.of(ports));
        node.setConfig(new LinkedHashMap<>());
        return node;
    }

    private BusinessProcessEdge edge(String id, String source, String target, String sourcePort) {
        BusinessProcessEdge edge = new BusinessProcessEdge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        edge.setSourcePort(sourcePort);
        edge.setCondition(new LinkedHashMap<>());
        return edge;
    }
}
