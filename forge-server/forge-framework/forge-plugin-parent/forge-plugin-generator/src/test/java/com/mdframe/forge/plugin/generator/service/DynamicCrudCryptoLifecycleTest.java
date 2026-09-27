package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.persistence.PersistentCiphertext;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicCrudCryptoLifecycleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldEncryptSnakeColumnAndDecryptCamelField() throws Exception {
        DynamicCrudFieldValuePipeline pipeline = pipeline(fakeCrypto(false));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone_number", "13900000000");

        pipeline.applyEncrypt(data, "{\"phoneNumber\":{\"algorithm\":\"SM4\"}}");

        assertEquals("FPC1:SM4:v2:13900000000", data.get("phone_number"));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phoneNumber", "FPC1:SM4:v2:13900000000");
        rows.add(row);

        pipeline.applyDecrypt(rows, "{\"phoneNumber\":{\"algorithm\":\"SM4\"}}");

        assertEquals("13900000000", row.get("phoneNumber"));
    }

    @Test
    void shouldFailClosedWhenLowcodeDecryptFails() {
        DynamicCrudFieldValuePipeline pipeline = pipeline(fakeCrypto(true));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phoneNumber", "broken-cipher");
        rows.add(row);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> pipeline.applyDecrypt(rows, "{\"phoneNumber\":{\"algorithm\":\"SM4\"}}"));

        assertTrue(exception.getMessage().contains("低代码加密字段读取失败"));
        assertEquals("broken-cipher", row.get("phoneNumber"));
    }

    @Test
    void shouldFailClosedWhenEncryptConfigIsInvalid() {
        DynamicCrudFieldValuePipeline pipeline = pipeline(fakeCrypto(false));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone_number", "13900000000");

        assertThrows(BusinessException.class, () -> pipeline.applyEncrypt(data, "[]"));
    }

    private DynamicCrudFieldValuePipeline pipeline(PersistentCryptoService cryptoService) {
        return new DynamicCrudFieldValuePipeline(
                objectMapper, null, null, cryptoService, new LowcodeEncryptConfigParser(objectMapper), null);
    }

    private PersistentCryptoService fakeCrypto(boolean failDecrypt) {
        return new PersistentCryptoService() {
            @Override
            public String encrypt(String plaintext, String algorithm) {
                return "FPC1:" + algorithm + ":v2:" + plaintext;
            }

            @Override
            public String decrypt(String ciphertext, String legacyAlgorithm) {
                if (failDecrypt) {
                    throw new IllegalStateException("bad key");
                }
                return ciphertext.substring(ciphertext.lastIndexOf(':') + 1);
            }

            @Override
            public PersistentCiphertext inspect(String ciphertext, String legacyAlgorithm) {
                return null;
            }

            @Override
            public String reencrypt(String ciphertext, String legacyAlgorithm) {
                return ciphertext;
            }
        };
    }
}
