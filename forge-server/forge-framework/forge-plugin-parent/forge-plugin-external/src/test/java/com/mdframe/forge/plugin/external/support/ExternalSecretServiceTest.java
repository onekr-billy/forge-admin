package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalSecretServiceTest {

    private final PersistentCryptoService cryptoService = mock(PersistentCryptoService.class);
    private final ExternalSecretService secretService = new ExternalSecretService(cryptoService);

    @Test
    void shouldEncryptNewSecretAndMaskManagementResponse() {
        when(cryptoService.encrypt("plain-token", null)).thenReturn("FPC1:SM4:key:cipher");
        ExternalSystem incoming = new ExternalSystem();
        incoming.setAuthType("token");
        incoming.setTokenValue("plain-token");

        ExternalSystem stored = secretService.prepareForPersistence(incoming, null);
        ExternalSystem managed = secretService.forManagement(stored);

        assertEquals("FPC1:SM4:key:cipher", stored.getTokenValue());
        assertEquals(ExternalSensitiveDataMasker.MASK, managed.getTokenValue());
        assertNotSame(stored, managed);
    }

    @Test
    void shouldKeepExistingSecretForBlankOrMaskAndDecryptRuntimeCopy() {
        when(cryptoService.decrypt("FPC1:SM4:key:cipher", null)).thenReturn("plain-token");
        ExternalSystem existing = new ExternalSystem();
        existing.setId(10L);
        existing.setAuthType("token");
        existing.setTokenValue("FPC1:SM4:key:cipher");
        ExternalSystem incoming = new ExternalSystem();
        incoming.setId(10L);
        incoming.setAuthType("token");
        incoming.setTokenValue(ExternalSensitiveDataMasker.MASK);

        ExternalSystem stored = secretService.prepareForPersistence(incoming, existing);
        ExternalSystem runtime = secretService.forRuntime(stored);

        assertEquals("FPC1:SM4:key:cipher", stored.getTokenValue());
        assertEquals("plain-token", runtime.getTokenValue());
        assertEquals("FPC1:SM4:key:cipher", stored.getTokenValue());
    }

    @Test
    void shouldRejectAuthTypeSwitchWithoutRequiredSecret() {
        ExternalSystem existing = new ExternalSystem();
        existing.setId(10L);
        existing.setAuthType("none");
        ExternalSystem incoming = new ExternalSystem();
        incoming.setId(10L);
        incoming.setAuthType("token");

        assertThrows(com.mdframe.forge.starter.core.exception.BusinessException.class,
                () -> secretService.prepareForPersistence(incoming, existing));
    }
}
