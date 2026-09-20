package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueProtection;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditValueViewVO;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategy;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategyFactory;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeType;
import com.mdframe.forge.starter.crypto.persistence.PersistentCiphertext;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class DataAuditValueProtector {

    private static final Set<String> SECRET_TYPES = Set.of(
            "PASSWORD", "SECRET", "TOKEN", "API_KEY", "AK", "SK"
    );

    private final PersistentCryptoService persistentCryptoService;
    private final DesensitizeStrategyFactory desensitizeStrategyFactory;
    private final DataAuditValueNormalizer normalizer;

    public DataAuditValueProtector(PersistentCryptoService persistentCryptoService,
                                   DesensitizeStrategyFactory desensitizeStrategyFactory,
                                   DataAuditValueNormalizer normalizer) {
        this.persistentCryptoService = persistentCryptoService;
        this.desensitizeStrategyFactory = desensitizeStrategyFactory;
        this.normalizer = normalizer;
    }

    public DataAuditValueProtection resolve(LowcodeFieldSchema field) {
        if (field == null) {
            return DataAuditValueProtection.PLAIN;
        }
        String sensitive = StringUtils.defaultString(field.getSensitiveType()).toUpperCase(Locale.ROOT);
        String component = StringUtils.defaultString(field.getComponentType());
        if (SECRET_TYPES.contains(sensitive) || "password".equalsIgnoreCase(component)) {
            return DataAuditValueProtection.CHANGE_ONLY;
        }
        if (StringUtils.isNotBlank(field.getEncryptAlgorithm())
                || (!sensitive.isEmpty() && !"NONE".equals(sensitive))) {
            return DataAuditValueProtection.ENCRYPTED;
        }
        return DataAuditValueProtection.PLAIN;
    }

    public StoredValue store(DataAuditNormalizedValue value, DataAuditValueProtection protection) {
        if (value == null || value.getState() != DataAuditValueState.VALUE) {
            return new StoredValue(value == null ? DataAuditValueState.NULL.getCode() : value.getState().getCode(),
                    null, null, protection, null);
        }
        if (protection == DataAuditValueProtection.CHANGE_ONLY) {
            return new StoredValue(DataAuditValueState.OMITTED.getCode(), null, null,
                    DataAuditValueProtection.CHANGE_ONLY, null);
        }
        String encoded = normalizer.encode(value);
        if (protection == DataAuditValueProtection.ENCRYPTED) {
            try {
                String cipher = persistentCryptoService.encrypt(encoded, null);
                PersistentCiphertext inspected = persistentCryptoService.inspect(cipher, null);
                String keyVersion = inspected == null ? null : inspected.keyId();
                return new StoredValue(DataAuditValueState.VALUE.getCode(), cipher, value.getDisplay(),
                        DataAuditValueProtection.ENCRYPTED, keyVersion);
            } catch (RuntimeException ex) {
                throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("敏感值加密失败");
            }
        }
        return new StoredValue(value.getState().getCode(), encoded, value.getDisplay(),
                DataAuditValueProtection.PLAIN, null);
    }

    public DataAuditValueViewVO project(String state,
                                        String encoded,
                                        String display,
                                        String type,
                                        DataAuditValueProtection protection,
                                        LowcodeFieldSchema field,
                                        boolean reveal) {
        DataAuditValueViewVO view = new DataAuditValueViewVO();
        DataAuditValueState valueState = DataAuditValueState.fromCode(state);
        view.setState(valueState.getCode());
        view.setType(type);
        view.setOmitted(valueState == DataAuditValueState.OMITTED || protection == DataAuditValueProtection.CHANGE_ONLY);
        view.setProtectedValue(protection != DataAuditValueProtection.PLAIN);
        if (valueState != DataAuditValueState.VALUE) {
            view.setDisplay(displayForState(valueState, protection));
            return view;
        }
        if (protection == DataAuditValueProtection.CHANGE_ONLY) {
            view.setDisplay("已变更");
            view.setOmitted(true);
            return view;
        }
        Object canonical = decode(encoded, type, protection);
        if (reveal && protection == DataAuditValueProtection.ENCRYPTED) {
            view.setValue(canonical);
            view.setDisplay(display == null ? stringify(canonical) : display);
            view.setProtectedValue(false);
            return view;
        }
        if (protection == DataAuditValueProtection.ENCRYPTED) {
            view.setDisplay(mask(field, display == null ? stringify(canonical) : display));
            view.setValue(null);
            return view;
        }
        view.setValue(canonical);
        view.setDisplay(display == null ? stringify(canonical) : display);
        return view;
    }

    public Object decode(String encoded, String type, DataAuditValueProtection protection) {
        if (StringUtils.isBlank(encoded)) {
            return null;
        }
        String plain = encoded;
        if (protection == DataAuditValueProtection.ENCRYPTED) {
            try {
                plain = persistentCryptoService.decrypt(encoded, null);
            } catch (RuntimeException ex) {
                throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("历史敏感值解密失败");
            }
        }
        return normalizer.decodeCanonical(plain, type);
    }

    private String mask(LowcodeFieldSchema field, String display) {
        if (display == null) {
            return "已脱敏";
        }
        DesensitizeType type = resolveDesensitizeType(field);
        if (type == null) {
            return maskFallback(display);
        }
        DesensitizeStrategy strategy = desensitizeStrategyFactory.getStrategy(type);
        if (strategy == null) {
            return maskFallback(display);
        }
        String masked = strategy.desensitize(display);
        return StringUtils.isBlank(masked) ? maskFallback(display) : masked;
    }

    private DesensitizeType resolveDesensitizeType(LowcodeFieldSchema field) {
        if (field == null || StringUtils.isBlank(field.getSensitiveType())) {
            return DesensitizeType.CUSTOM;
        }
        try {
            return DesensitizeType.valueOf(field.getSensitiveType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DesensitizeType.CUSTOM;
        }
    }

    private String maskFallback(String display) {
        if (display.length() <= 2) {
            return "**";
        }
        return display.charAt(0) + "****" + display.charAt(display.length() - 1);
    }

    private String displayForState(DataAuditValueState state, DataAuditValueProtection protection) {
        if (protection == DataAuditValueProtection.CHANGE_ONLY) {
            return "已变更";
        }
        return switch (state) {
            case ABSENT -> "不存在";
            case NULL -> "空值";
            case OMITTED -> "已省略";
            case VALUE -> null;
        };
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record StoredValue(String state,
                              String encoded,
                              String display,
                              DataAuditValueProtection protection,
                              String keyVersion) {
    }
}
