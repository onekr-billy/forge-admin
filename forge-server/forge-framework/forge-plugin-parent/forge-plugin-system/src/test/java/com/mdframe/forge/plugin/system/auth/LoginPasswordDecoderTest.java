package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.keyexchange.RsaKeyPairHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginPasswordDecoderTest {

    @Test
    void shouldRejectPasswordWhenRsaDecryptionFails() {
        LoginPasswordEncryptionPolicy policy = mock(LoginPasswordEncryptionPolicy.class);
        RsaKeyPairHolder keyPairHolder = mock(RsaKeyPairHolder.class);
        ObjectProvider<RsaKeyPairHolder> keyPairHolderProvider = mock(ObjectProvider.class);
        when(policy.isEnabled()).thenReturn(true);
        when(keyPairHolderProvider.getIfAvailable()).thenReturn(keyPairHolder);
        when(keyPairHolder.decryptByPrivateKey("invalid-ciphertext"))
                .thenThrow(new IllegalArgumentException("invalid ciphertext"));

        LoginPasswordDecoder decoder = new LoginPasswordDecoder(policy, keyPairHolderProvider);

        BusinessException exception = assertThrows(BusinessException.class, () -> decoder.decode("invalid-ciphertext"));
        assertEquals(400, exception.getCode());
        assertEquals("密码加密校验失败，请刷新后重试", exception.getMessage());
    }

    @Test
    void shouldAllowPlainPasswordOnlyWhenLoginPasswordEncryptionIsDisabled() {
        LoginPasswordEncryptionPolicy policy = mock(LoginPasswordEncryptionPolicy.class);
        ObjectProvider<RsaKeyPairHolder> keyPairHolderProvider = mock(ObjectProvider.class);
        when(policy.isEnabled()).thenReturn(false);

        LoginPasswordDecoder decoder = new LoginPasswordDecoder(policy, keyPairHolderProvider);

        assertEquals("plain-password", decoder.decode("plain-password"));
        verifyNoInteractions(keyPairHolderProvider);
    }
}
