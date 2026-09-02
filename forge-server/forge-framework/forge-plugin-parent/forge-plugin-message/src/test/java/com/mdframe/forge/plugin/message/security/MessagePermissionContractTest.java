package com.mdframe.forge.plugin.message.security;

import com.mdframe.forge.plugin.message.controller.MessageBizTypeController;
import com.mdframe.forge.plugin.message.controller.MessageConfigController;
import com.mdframe.forge.plugin.message.controller.MessageController;
import com.mdframe.forge.plugin.message.controller.MessageTemplateController;
import com.mdframe.forge.plugin.message.domain.dto.MessageQueryDTO;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MessagePermissionContractTest {

    @Test
    void messageAdminControllersShouldNotIgnorePermissionAtClassLevel() {
        assertThat(MessageController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(MessageConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(MessageTemplateController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(MessageBizTypeController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    @Test
    void inboxApisShouldRemainIgnoredAndSendShouldRequirePermission() throws NoSuchMethodException {
        assertIgnored("pageByGet", MessageQueryDTO.class, Integer.class, Integer.class);
        assertIgnored("getUnreadCount");
        assertIgnored("markAllRead");
        Method send = MessageController.class.getDeclaredMethod("send", MessageSendRequestDTO.class);
        assertThat(send.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    private void assertIgnored(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = MessageController.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(ApiPermissionIgnore.class)).isTrue();
    }
}
