package com.mdframe.forge.starter.crypto.config;

import com.mdframe.forge.starter.crypto.crypto.CryptoAlgorithm;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Injects stable generated crypto secrets before configuration properties are bound.
 */
public final class CryptoSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "forgeCryptoBootstrap";
    static final String BOOTSTRAP_ENABLED_PROPERTY = "forge.crypto.bootstrap.enabled";
    static final String BOOTSTRAP_FILE_PROPERTY = "forge.crypto.bootstrap.file";
    static final String ALGORITHM_PROPERTY = "forge.crypto.algorithm";
    static final String SECRET_KEY_PROPERTY = "forge.crypto.secret-key";
    static final String PERSISTENCE_ENABLED_PROPERTY = "forge.crypto.persistence.enabled";
    static final String WRITE_VERSIONED_PROPERTY = "forge.crypto.persistence.write-versioned";
    static final String LEGACY_READ_ENABLED_PROPERTY = "forge.crypto.persistence.legacy-read-enabled";
    static final String LEGACY_KEY_PROPERTY = "forge.crypto.persistence.legacy-key";
    static final String ACTIVE_KEY_ID_PROPERTY = "forge.crypto.persistence.active-key-id";
    static final String ACTIVE_KEY_PROPERTY = "forge.crypto.persistence.active-key";
    static final String HISTORICAL_KEY_PREFIX = "forge.crypto.persistence.keys.";
    static final String CAPABILITY_CLIENT_PEPPER_PROPERTY = "forge.capability.client-pepper";
    static final String CAPABILITY_TOKEN_PEPPER_PROPERTY = "forge.capability.identity.token-pepper";
    static final String CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY =
            "forge.capability.identity.authorization-code-pepper";

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int CAPABILITY_CLIENT_PEPPER_MIN_LENGTH = 16;
    private static final int CAPABILITY_IDENTITY_PEPPER_MIN_LENGTH = 32;
    private static final int SM4_KEY_LENGTH = 16;
    private static final int AES_256_KEY_LENGTH = 32;
    private static final Set<String> FIXED_PROPERTIES = Set.of(
            SECRET_KEY_PROPERTY,
            PERSISTENCE_ENABLED_PROPERTY,
            WRITE_VERSIONED_PROPERTY,
            LEGACY_READ_ENABLED_PROPERTY,
            LEGACY_KEY_PROPERTY,
            ACTIVE_KEY_ID_PROPERTY,
            ACTIVE_KEY_PROPERTY,
            CAPABILITY_CLIENT_PEPPER_PROPERTY,
            CAPABILITY_TOKEN_PEPPER_PROPERTY,
            CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY
    );
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );
    private static final Map<Path, Object> JVM_FILE_LOCKS = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom;

    public CryptoSecretEnvironmentPostProcessor() {
        this(new SecureRandom());
    }

    CryptoSecretEnvironmentPostProcessor(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 持久化密钥自举不依赖 forge.crypto.enabled（传输加密开关），仅受 bootstrap.enabled 控制
        if (!getBoolean(environment, BOOTSTRAP_ENABLED_PROPERTY, true)) {
            return;
        }
        CryptoAlgorithm algorithm = resolveAlgorithm(environment);
        if (areAllBootstrapSecretsExplicitlyConfigured(environment)) {
            validateExplicitSecrets(environment, algorithm);
            return;
        }

        Path secretFile = resolveSecretFile(environment);
        Map<String, Object> fileProperties = loadOrCreate(secretFile, algorithm);
        Map<String, Object> effectiveProperties = applyExternalOverrides(environment, fileProperties);
        validateCryptoKeys(effectiveProperties, algorithm);
        validateCapabilityPeppers(effectiveProperties);

        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, effectiveProperties));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private boolean getBoolean(ConfigurableEnvironment environment, String key, boolean defaultValue) {
        String value = environment.getProperty(key);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private boolean areAllBootstrapSecretsExplicitlyConfigured(ConfigurableEnvironment environment) {
        return hasExternalOverride(environment, SECRET_KEY_PROPERTY)
                && hasExternalOverride(environment, CAPABILITY_CLIENT_PEPPER_PROPERTY)
                && hasExternalOverride(environment, CAPABILITY_TOKEN_PEPPER_PROPERTY)
                && hasExternalOverride(environment, CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY);
    }

    private boolean hasExternalOverride(ConfigurableEnvironment environment, String key) {
        return StringUtils.hasText(findExternalOverride(environment, key));
    }

    private void validateExplicitSecrets(ConfigurableEnvironment environment, CryptoAlgorithm algorithm) {
        validateBase64Key(SECRET_KEY_PROPERTY, findExternalOverride(environment, SECRET_KEY_PROPERTY), algorithm);
        Map<String, Object> explicitPeppers = new LinkedHashMap<>();
        explicitPeppers.put(CAPABILITY_CLIENT_PEPPER_PROPERTY,
                findExternalOverride(environment, CAPABILITY_CLIENT_PEPPER_PROPERTY));
        explicitPeppers.put(CAPABILITY_TOKEN_PEPPER_PROPERTY,
                findExternalOverride(environment, CAPABILITY_TOKEN_PEPPER_PROPERTY));
        explicitPeppers.put(CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY,
                findExternalOverride(environment, CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY));
        validateCapabilityPeppers(explicitPeppers);
    }

    private CryptoAlgorithm resolveAlgorithm(ConfigurableEnvironment environment) {
        String configured = environment.getProperty(ALGORITHM_PROPERTY, CryptoAlgorithm.SM4.getCode());
        try {
            return CryptoAlgorithm.fromCode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ALGORITHM_PROPERTY + " 配置不受支持: " + configured, e);
        }
    }

    private Path resolveSecretFile(ConfigurableEnvironment environment) {
        String configured = environment.getProperty(BOOTSTRAP_FILE_PROPERTY);
        Path path = StringUtils.hasText(configured)
                ? Path.of(configured.trim())
                : Path.of(System.getProperty("user.home"), ".forge", "secrets", "crypto.properties");
        return path.toAbsolutePath().normalize();
    }

    private Map<String, Object> loadOrCreate(Path secretFile, CryptoAlgorithm algorithm) {
        Object jvmLock = JVM_FILE_LOCKS.computeIfAbsent(secretFile, ignored -> new Object());
        synchronized (jvmLock) {
            try {
                Path directory = secretFile.getParent();
                if (directory == null) {
                    throw new IllegalStateException("自动密钥文件必须具有父目录: " + secretFile);
                }
                Files.createDirectories(directory);
                tightenPermissions(directory, DIRECTORY_PERMISSIONS);
                rejectSymbolicLink(secretFile);

                Path lockFile = directory.resolve(secretFile.getFileName() + ".lock");
                rejectSymbolicLink(lockFile);
                try (FileChannel lockChannel = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE)) {
                    tightenPermissions(lockFile, FILE_PERMISSIONS);
                    Map<String, Object> existing = readCompleteProperties(
                            lockChannel, secretFile, algorithm);
                    if (existing != null) {
                        return existing;
                    }
                    try (var ignored = lockChannel.lock()) {
                        return loadOrCreateWithExclusiveLock(secretFile, algorithm);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("无法初始化自动密钥文件: " + secretFile, e);
            }
        }
    }

    private Map<String, Object> readCompleteProperties(FileChannel lockChannel,
                                                       Path secretFile,
                                                       CryptoAlgorithm algorithm) throws IOException {
        try (var ignored = lockChannel.lock(0L, Long.MAX_VALUE, true)) {
            if (!Files.exists(secretFile, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Map<String, Object> existing = readProperties(secretFile);
            if (requiresCapabilityPepperUpgrade(existing)) {
                return null;
            }
            validateProperties(existing, algorithm);
            return existing;
        }
    }

    private Map<String, Object> loadOrCreateWithExclusiveLock(Path secretFile,
                                                               CryptoAlgorithm algorithm) throws IOException {
        if (Files.exists(secretFile, LinkOption.NOFOLLOW_LINKS)) {
            Map<String, Object> existing = readProperties(secretFile);
            boolean changed = addMissingCapabilityPeppers(existing);
            validateProperties(existing, algorithm);
            if (changed) {
                writeAtomically(secretFile, existing);
            }
            return existing;
        }
        Map<String, Object> generated = generateProperties(algorithm);
        validateProperties(generated, algorithm);
        writeAtomically(secretFile, generated);
        return generated;
    }

    private boolean requiresCapabilityPepperUpgrade(Map<String, Object> properties) {
        return !properties.containsKey(CAPABILITY_CLIENT_PEPPER_PROPERTY)
                || !properties.containsKey(CAPABILITY_TOKEN_PEPPER_PROPERTY)
                || !properties.containsKey(CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY);
    }

    private Map<String, Object> generateProperties(CryptoAlgorithm algorithm) {
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put(SECRET_KEY_PROPERTY, generateBase64Key(algorithm));
        generated.put(PERSISTENCE_ENABLED_PROPERTY, "true");
        generated.put(WRITE_VERSIONED_PROPERTY, "true");
        generated.put(LEGACY_READ_ENABLED_PROPERTY, "false");
        generated.put(ACTIVE_KEY_ID_PROPERTY, generateKeyId());
        generated.put(ACTIVE_KEY_PROPERTY, generateBase64Key(algorithm));
        addMissingCapabilityPeppers(generated);
        return generated;
    }

    private boolean addMissingCapabilityPeppers(Map<String, Object> properties) {
        boolean changed = false;
        Set<String> existingValues = new HashSet<>();
        addExistingValue(properties, CAPABILITY_CLIENT_PEPPER_PROPERTY, existingValues);
        addExistingValue(properties, CAPABILITY_TOKEN_PEPPER_PROPERTY, existingValues);
        addExistingValue(properties, CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY, existingValues);

        changed |= addCapabilityPepperIfMissing(
                properties, CAPABILITY_CLIENT_PEPPER_PROPERTY, existingValues);
        changed |= addCapabilityPepperIfMissing(
                properties, CAPABILITY_TOKEN_PEPPER_PROPERTY, existingValues);
        changed |= addCapabilityPepperIfMissing(
                properties, CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY, existingValues);
        return changed;
    }

    private void addExistingValue(Map<String, Object> properties, String key, Set<String> existingValues) {
        Object value = properties.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            existingValues.add(String.valueOf(value).trim());
        }
    }

    private boolean addCapabilityPepperIfMissing(Map<String, Object> properties,
                                                 String key,
                                                 Set<String> existingValues) {
        if (properties.containsKey(key)) {
            return false;
        }
        String pepper = generateCapabilityPepper(existingValues);
        properties.put(key, pepper);
        existingValues.add(pepper);
        return true;
    }

    private String generateCapabilityPepper(Set<String> existingValues) {
        String value;
        do {
            byte[] secret = new byte[32];
            secureRandom.nextBytes(secret);
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        } while (existingValues.contains(value));
        return value;
    }

    private String generateKeyId() {
        byte[] suffix = new byte[8];
        secureRandom.nextBytes(suffix);
        return "bootstrap-" + Base64.getUrlEncoder().withoutPadding().encodeToString(suffix);
    }

    private String generateBase64Key(CryptoAlgorithm algorithm) {
        int keyLength = algorithm == CryptoAlgorithm.SM4 ? SM4_KEY_LENGTH : AES_256_KEY_LENGTH;
        byte[] key = new byte[keyLength];
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private Map<String, Object> readProperties(Path secretFile) throws IOException {
        if (!Files.isRegularFile(secretFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("自动密钥路径不是普通文件: " + secretFile);
        }
        tightenPermissions(secretFile, FILE_PERMISSIONS);

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(secretFile)) {
            properties.load(input);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!FIXED_PROPERTIES.contains(key) && !key.startsWith(HISTORICAL_KEY_PREFIX)) {
                throw new IllegalStateException("自动密钥文件包含不允许的配置键: " + key);
            }
            result.put(key, properties.getProperty(key));
        }
        return result;
    }

    private void validateProperties(Map<String, Object> properties, CryptoAlgorithm algorithm) {
        validateCryptoKeys(properties, algorithm);
        validateCapabilityPeppers(properties);
    }

    private void validateCryptoKeys(Map<String, Object> properties, CryptoAlgorithm algorithm) {
        String transportKey = required(properties, SECRET_KEY_PROPERTY);
        validateBase64Key(SECRET_KEY_PROPERTY, transportKey, algorithm);
        boolean persistenceEnabled = requiredBoolean(properties, PERSISTENCE_ENABLED_PROPERTY);
        boolean writeVersioned = requiredBoolean(properties, WRITE_VERSIONED_PROPERTY);
        boolean legacyReadEnabled = requiredBoolean(properties, LEGACY_READ_ENABLED_PROPERTY);

        if (persistenceEnabled && writeVersioned) {
            String activeKeyId = required(properties, ACTIVE_KEY_ID_PROPERTY);
            if (!KEY_ID_PATTERN.matcher(activeKeyId).matches()) {
                throw new IllegalStateException(ACTIVE_KEY_ID_PROPERTY + " 必须匹配 [A-Za-z0-9_-]{1,32}");
            }
            validateBase64Key(ACTIVE_KEY_PROPERTY, required(properties, ACTIVE_KEY_PROPERTY), algorithm);
        }
        if (properties.containsKey(LEGACY_KEY_PROPERTY)) {
            validateBase64Key(LEGACY_KEY_PROPERTY, required(properties, LEGACY_KEY_PROPERTY), algorithm);
        } else if (persistenceEnabled && (legacyReadEnabled || !writeVersioned)) {
            validateBase64Key(SECRET_KEY_PROPERTY, transportKey, algorithm);
        }
        properties.forEach((key, value) -> {
            if (key.startsWith(HISTORICAL_KEY_PREFIX)) {
                String keyId = key.substring(HISTORICAL_KEY_PREFIX.length());
                if (!KEY_ID_PATTERN.matcher(keyId).matches()) {
                    throw new IllegalStateException("历史密钥 keyId 非法: " + keyId);
                }
                validateBase64Key(key, String.valueOf(value), algorithm);
            }
        });
    }

    private void validateCapabilityPeppers(Map<String, Object> properties) {
        String clientPepper = validateCapabilityPepper(
                properties, CAPABILITY_CLIENT_PEPPER_PROPERTY, CAPABILITY_CLIENT_PEPPER_MIN_LENGTH);
        String tokenPepper = validateCapabilityPepper(
                properties, CAPABILITY_TOKEN_PEPPER_PROPERTY, CAPABILITY_IDENTITY_PEPPER_MIN_LENGTH);
        String authorizationCodePepper = validateCapabilityPepper(
                properties,
                CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY,
                CAPABILITY_IDENTITY_PEPPER_MIN_LENGTH);
        Set<String> peppers = new HashSet<>();
        peppers.add(clientPepper);
        peppers.add(tokenPepper);
        peppers.add(authorizationCodePepper);
        if (peppers.size() != 3) {
            throw new IllegalStateException("Forge Capability 三个 Pepper 必须互不相同");
        }
    }

    private String validateCapabilityPepper(Map<String, Object> properties, String key, int minLength) {
        String value = required(properties, key);
        if (value.length() < minLength) {
            throw new IllegalStateException(key + " 长度不能少于 " + minLength + " 位");
        }
        return value;
    }

    private boolean requiredBoolean(Map<String, Object> properties, String key) {
        String value = required(properties, key);
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException(key + " 必须是 true 或 false");
        }
        return Boolean.parseBoolean(value);
    }

    private String required(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalStateException("自动密钥文件缺少配置: " + key);
        }
        return String.valueOf(value).trim();
    }

    private void validateBase64Key(String keyName, String value, CryptoAlgorithm algorithm) {
        try {
            int length = Base64.getDecoder().decode(value.trim()).length;
            boolean valid = algorithm == CryptoAlgorithm.SM4
                    ? length == SM4_KEY_LENGTH
                    : length == 16 || length == 24 || length == AES_256_KEY_LENGTH;
            if (!valid) {
                String expected = algorithm == CryptoAlgorithm.SM4 ? "16" : "16、24 或 32";
                throw new IllegalStateException(keyName + " 必须是 Base64 编码的 " + expected + " 字节密钥");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(keyName + " 必须是合法 Base64 编码", e);
        }
    }

    private Map<String, Object> applyExternalOverrides(ConfigurableEnvironment environment,
                                                       Map<String, Object> fileProperties) {
        Map<String, Object> effective = new LinkedHashMap<>(fileProperties);
        for (String key : fileProperties.keySet()) {
            String externalValue = findExternalOverride(environment, key);
            if (StringUtils.hasText(externalValue)) {
                effective.put(key, externalValue.trim());
            }
        }
        return effective;
    }

    private String findExternalOverride(ConfigurableEnvironment environment, String key) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            String name = propertySource.getName();
            if (PROPERTY_SOURCE_NAME.equals(name) || isConfigDataSource(name) || isAggregatedSource(name)) {
                continue;
            }
            Object value = propertySource.getProperty(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))
                    && !isUnresolvedPlaceholder(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean isConfigDataSource(String name) {
        return name.startsWith("Config resource '") || name.startsWith("applicationConfig:");
    }

    /**
     * ConfigurationPropertySources.attach 注入的 configurationProperties 聚合源会透传全部底层源
     * （含 yml 配置的原始占位符值），必须跳过，否则占位符字符串会覆盖自举文件中的真实密钥。
     */
    private boolean isAggregatedSource(String name) {
        return "configurationProperties".equals(name);
    }

    private boolean isUnresolvedPlaceholder(String value) {
        return value.contains("${");
    }

    private void writeAtomically(Path secretFile, Map<String, Object> values) throws IOException {
        Path directory = secretFile.getParent();
        Path temporary = Files.createTempFile(directory, ".crypto-", ".tmp");
        try {
            tightenPermissions(temporary, FILE_PERMISSIONS);
            Properties properties = new Properties();
            values.forEach((key, value) -> properties.setProperty(key, String.valueOf(value)));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            properties.store(output, "Generated by Forge crypto bootstrap. Do not commit this file.");
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(output.toByteArray()));
                channel.force(true);
            }
            try {
                Files.move(temporary, secretFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, secretFile, StandardCopyOption.REPLACE_EXISTING);
            }
            tightenPermissions(secretFile, FILE_PERMISSIONS);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("自动密钥路径禁止使用符号链接: " + path);
        }
    }

    private void tightenPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }
}
