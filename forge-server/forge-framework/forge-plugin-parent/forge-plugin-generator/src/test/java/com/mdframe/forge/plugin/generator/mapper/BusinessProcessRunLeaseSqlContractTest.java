package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessProcessRunLeaseSqlContractTest {

    @Test
    void runTransitionsAndNodeAttemptsAreFencedByTheActiveLease() throws Exception {
        String runMapper = resource("mapper/BusinessProcessRunMapper.xml");
        String nodeMapper = resource("mapper/BusinessProcessNodeRunMapper.xml");

        assertTrue(runMapper.contains("execution_token = execution_token + 1"));
        assertTrue(runMapper.contains("lease_expire_time &gt; NOW(3)"));
        assertTrue(runMapper.contains("execution_token = #{executionToken}"));
        assertTrue(runMapper.contains("lease_owner = #{leaseOwner}"));
        assertTrue(nodeMapper.contains("<update id=\"claimAttemptWithLease\">"));
        assertTrue(nodeMapper.contains("<update id=\"completeAttemptWithLease\">"));
        assertTrue(nodeMapper.contains("r.execution_token = #{executionToken}"));
        assertTrue(nodeMapper.contains("r.lease_expire_time &gt; NOW(3)"));
        assertTrue(!nodeMapper.contains("SET n."), "MySQL UPDATE SET 左值不应带表别名");
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
