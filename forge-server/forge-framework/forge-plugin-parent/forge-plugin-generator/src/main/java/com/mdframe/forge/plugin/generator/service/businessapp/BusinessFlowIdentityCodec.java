package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskAccessPolicy.isSyntheticTestBusinessKey;

/** Encodes and decodes stable business identities shared by flow entry points. */
final class BusinessFlowIdentityCodec {

    private BusinessFlowIdentityCodec() {
    }

    static String buildBusinessKey(String objectCode, Long recordId) {
        return objectCode + ":" + recordId;
    }

    static BusinessKeyParts parseBusinessKey(String businessKey) {
        if (StringUtils.isBlank(businessKey) || !businessKey.contains(":")) {
            throw new BusinessException("业务Key格式错误，应为 objectCode:recordId");
        }
        String[] parts = businessKey.split(":", 2);
        if (StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
            throw new BusinessException("业务Key格式错误，应为 objectCode:recordId");
        }
        try {
            return new BusinessKeyParts(parts[0], Long.valueOf(parts[1]));
        } catch (NumberFormatException e) {
            throw new BusinessException("业务Key中的记录ID必须是数字");
        }
    }

    static String parseBusinessKeyObjectCode(String businessKey) {
        if (StringUtils.isBlank(businessKey) || !businessKey.contains(":") || isSyntheticTestBusinessKey(businessKey)) {
            return null;
        }
        return StringUtils.trimToNull(businessKey.split(":", 2)[0]);
    }

    static Long parseBusinessKeyRecordId(String businessKey) {
        if (StringUtils.isBlank(businessKey) || !businessKey.contains(":") || isSyntheticTestBusinessKey(businessKey)) {
            return null;
        }
        return parseLongValue(businessKey.split(":", 2)[1]);
    }

    static Long parseLongValue(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
