package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueProtection;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditValueViewVO;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategyFactory;
import com.mdframe.forge.starter.crypto.persistence.PersistentCiphertext;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DataAuditValueProtector")
class DataAuditValueProtectorTest {

    @Test
    @DisplayName("凭据字段只记录变更事实")
    void secretsAreChangeOnly() {
        DataAuditValueProtector protector = protector();
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setSensitiveType("PASSWORD");
        assertEquals(DataAuditValueProtection.CHANGE_ONLY, protector.resolve(field));

        DataAuditNormalizedValue value = DataAuditNormalizedValue.builder()
                .state(DataAuditValueState.VALUE)
                .type("STRING")
                .canonical("secret")
                .display("secret")
                .encodedSize(6)
                .build();
        DataAuditValueProtector.StoredValue stored = protector.store(value, DataAuditValueProtection.CHANGE_ONLY);
        assertEquals(DataAuditValueState.OMITTED.getCode(), stored.state());
        assertNull(stored.encoded());
    }

    @Test
    @DisplayName("敏感值默认投影不含原值")
    void masksEncryptedValuesByDefault() {
        DataAuditValueProtector protector = protector();
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setSensitiveType("PHONE");
        DataAuditValueViewVO view = protector.project(
                DataAuditValueState.VALUE.getCode(),
                "ENC:13800138000",
                "13800138000",
                "STRING",
                DataAuditValueProtection.ENCRYPTED,
                field,
                false);
        assertTrue(Boolean.TRUE.equals(view.getProtectedValue()));
        assertNull(view.getValue());
        assertTrue(view.getDisplay() == null || !view.getDisplay().contains("13800138000")
                || view.getDisplay().contains("*"));
    }

    private DataAuditValueProtector protector() {
        PersistentCryptoService crypto = new PersistentCryptoService() {
            @Override
            public String encrypt(String plaintext, String algorithm) {
                return "ENC:" + plaintext;
            }

            @Override
            public String decrypt(String ciphertext, String legacyAlgorithm) {
                return ciphertext.startsWith("ENC:") ? ciphertext.substring(4) : ciphertext;
            }

            @Override
            public PersistentCiphertext inspect(String ciphertext, String legacyAlgorithm) {
                return PersistentCiphertext.inspect(ciphertext, legacyAlgorithm, "k1", java.util.Set.of("k1"));
            }

            @Override
            public String reencrypt(String ciphertext, String legacyAlgorithm) {
                return encrypt(decrypt(ciphertext, legacyAlgorithm), legacyAlgorithm);
            }
        };
        return new DataAuditValueProtector(crypto, new DesensitizeStrategyFactory(),
                new DataAuditValueNormalizer(new ObjectMapper()));
    }
}
