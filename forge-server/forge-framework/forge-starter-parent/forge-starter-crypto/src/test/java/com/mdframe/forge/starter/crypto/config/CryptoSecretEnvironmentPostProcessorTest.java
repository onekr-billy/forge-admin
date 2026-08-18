package com.mdframe.forge.starter.crypto.config;

import com.mdframe.forge.starter.core.context.CryptoProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoSecretEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateAndReuseStableSecrets() {
        Path secretFile = tempDir.resolve("secrets/crypto.properties");
        StandardEnvironment first = environment(secretFile, Map.of());

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                first, new SpringApplication(Object.class));

        String firstTransportKey = first.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY);
        String firstPersistenceKey = first.getProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY);
        String firstClientPepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY);
        String firstTokenPepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY);
        String firstAuthorizationCodePepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY);
        assertThat(Base64.getDecoder().decode(firstTransportKey)).hasSize(16);
        assertThat(Base64.getDecoder().decode(firstPersistenceKey)).hasSize(16);
        assertThat(Base64.getUrlDecoder().decode(firstClientPepper)).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(firstTokenPepper)).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(firstAuthorizationCodePepper)).hasSize(32);
        assertThat(List.of(firstClientPepper, firstTokenPepper, firstAuthorizationCodePepper))
                .doesNotHaveDuplicates();
        assertThat(firstPersistenceKey).isNotEqualTo(firstTransportKey);
        assertThat(first.getProperty(CryptoSecretEnvironmentPostProcessor.WRITE_VERSIONED_PROPERTY)).isEqualTo("true");
        assertThat(first.getProperty(CryptoSecretEnvironmentPostProcessor.LEGACY_READ_ENABLED_PROPERTY)).isEqualTo("false");
        assertThat(secretFile).isRegularFile();
        CryptoProperties boundProperties = Binder.get(first)
                .bind("forge.crypto", Bindable.of(CryptoProperties.class))
                .orElseThrow(() -> new AssertionError("forge.crypto 配置未绑定"));
        new CryptoConfigurationValidator().validate(boundProperties);

        StandardEnvironment second = environment(secretFile, Map.of());
        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                second, new SpringApplication(Object.class));

        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY))
                .isEqualTo(firstTransportKey);
        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY))
                .isEqualTo(firstPersistenceKey);
        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY))
                .isEqualTo(firstClientPepper);
        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY))
                .isEqualTo(firstTokenPepper);
        assertThat(second.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY))
                .isEqualTo(firstAuthorizationCodePepper);
    }

    @Test
    void shouldGenerateAes256KeysForNewBootstrapFile() {
        Path secretFile = tempDir.resolve("aes/secrets/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.ALGORITHM_PROPERTY, "AES_GCM"));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(Base64.getDecoder().decode(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY))).hasSize(32);
        assertThat(Base64.getDecoder().decode(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY))).hasSize(32);
    }

    @Test
    void shouldKeepAcceptingLegacyAes128BootstrapKeys() throws IOException {
        Path secretFile = tempDir.resolve("aes-legacy/crypto.properties");
        writeLegacySecretFile(secretFile);
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.ALGORITHM_PROPERTY, "AES"));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(Base64.getDecoder().decode(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY))).hasSize(16);
        assertThat(Base64.getDecoder().decode(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY))).hasSize(16);
    }

    @Test
    void shouldNotCreateFileWhenTransportAndCapabilitySecretsAreExplicitlyConfigured() {
        Path secretFile = tempDir.resolve("explicit/crypto.properties");
        String explicitKey = Base64.getEncoder().encodeToString(new byte[16]);
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY, explicitKey,
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY,
                "explicit-client-pepper-1234567890",
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY,
                "explicit-token-pepper-12345678901234567890",
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY,
                "explicit-code-pepper-123456789012345678901"));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY))
                .isEqualTo(explicitKey);
        assertThat(secretFile).doesNotExist();
    }

    @Test
    void shouldGenerateCapabilityPeppersWhenOnlyTransportKeyIsExplicitlyConfigured() {
        Path secretFile = tempDir.resolve("explicit-transport-only/crypto.properties");
        String explicitKey = Base64.getEncoder().encodeToString(new byte[16]);
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY, explicitKey));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY))
                .isEqualTo(explicitKey);
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY)).isNotBlank();
        assertThat(secretFile).isRegularFile();
    }

    @Test
    void shouldLetExplicitCapabilityPepperOverridesWinIndependently() {
        Path secretFile = tempDir.resolve("pepper-overrides/crypto.properties");
        String explicitClientPepper = "external-client-pepper-1234567890";
        String explicitTokenPepper = "external-token-pepper-12345678901234567890";
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY,
                explicitClientPepper,
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY,
                explicitTokenPepper));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY))
                .isEqualTo(explicitClientPepper);
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY))
                .isEqualTo(explicitTokenPepper);
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY))
                .isNotBlank()
                .isNotIn(explicitClientPepper, explicitTokenPepper);
        assertThat(secretFile).isRegularFile();
    }

    @Test
    void shouldReplaceBlankExternalValuesWithPersistedValues() {
        Path secretFile = tempDir.resolve("blank/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY, " ",
                CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY, ""));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY)).isNotBlank();
    }

    @Test
    void shouldIgnorePlaceholderValuesExposedByAttachedConfigurationSource() {
        Path secretFile = tempDir.resolve("attached/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of());
        // 模拟 application.yml 中的占位符默认值（config data 源）
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.yml]' via location 'optional:classpath:/'",
                Map.of(
                        CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY,
                        "${FORGE_CRYPTO_SECRET_KEY:}",
                        CryptoSecretEnvironmentPostProcessor.WRITE_VERSIONED_PROPERTY,
                        "${FORGE_CRYPTO_PERSISTENCE_WRITE_VERSIONED:false}",
                        CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY,
                        "${FORGE_CRYPTO_PERSISTENCE_ACTIVE_KEY:}",
                        CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY,
                        "${FORGE_CAPABILITY_CLIENT_PEPPER:}",
                        CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY,
                        "${FORGE_CAPABILITY_TOKEN_PEPPER:}",
                        CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY,
                        "${FORGE_CAPABILITY_AUTH_CODE_PEPPER:}")));
        // 模拟 SpringApplication.prepareEnvironment 在 EPP 之前挂载的聚合源
        ConfigurationPropertySources.attach(environment);

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        String transportKey = environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY);
        String activeKey = environment.getProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY);
        assertThat(Base64.getDecoder().decode(transportKey)).hasSize(16);
        assertThat(Base64.getDecoder().decode(activeKey)).hasSize(16);
        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.WRITE_VERSIONED_PROPERTY))
                .isEqualTo("true");
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY)).isNotBlank();
    }

    @Test
    void shouldBootstrapEvenWhenTransportCryptoDisabled() {
        Path secretFile = tempDir.resolve("disabled/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of("forge.crypto.enabled", "false"));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY)).isNotBlank();
        assertThat(environment.getProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY)).isNotBlank();
        assertThat(secretFile).isRegularFile();
    }

    @Test
    void shouldKeepFailClosedBehaviorWhenBootstrapIsExplicitlyDisabled() {
        Path secretFile = tempDir.resolve("bootstrap-disabled/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of(
                CryptoSecretEnvironmentPostProcessor.BOOTSTRAP_ENABLED_PROPERTY, "false"));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY)).isNull();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY)).isNull();
        assertThat(environment.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY)).isNull();
        assertThat(secretFile).doesNotExist();
    }

    @Test
    void shouldUpgradeLegacyCryptoFileWithStableCapabilityPeppers() throws IOException {
        Path secretFile = tempDir.resolve("legacy/crypto.properties");
        writeLegacySecretFile(secretFile);
        StandardEnvironment first = environment(secretFile, Map.of());

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                first, new SpringApplication(Object.class));

        String clientPepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY);
        String tokenPepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY);
        String authorizationCodePepper = first.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY);
        assertThat(List.of(clientPepper, tokenPepper, authorizationCodePepper)).doesNotHaveDuplicates();

        Properties persisted = new Properties();
        try (InputStream input = Files.newInputStream(secretFile)) {
            persisted.load(input);
        }
        assertThat(persisted.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY)).isEqualTo(clientPepper);
        assertThat(persisted.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY)).isEqualTo(tokenPepper);
        assertThat(persisted.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY))
                .isEqualTo(authorizationCodePepper);

        StandardEnvironment second = environment(secretFile, Map.of());
        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                second, new SpringApplication(Object.class));
        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY))
                .isEqualTo(clientPepper);
        assertThat(second.getProperty(CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY))
                .isEqualTo(tokenPepper);
        assertThat(second.getProperty(
                CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY))
                .isEqualTo(authorizationCodePepper);
    }

    @Test
    void shouldFailClosedForCorruptedSecretFile() throws IOException {
        Path secretFile = tempDir.resolve("corrupt/crypto.properties");
        Files.createDirectories(secretFile.getParent());
        Files.writeString(secretFile, "forge.crypto.secret-key=not-base64\n");
        StandardEnvironment environment = environment(secretFile, Map.of());

        assertThatThrownBy(() -> new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须是合法 Base64 编码");
    }

    @Test
    void shouldSerializeConcurrentFirstStart() throws Exception {
        Path secretFile = tempDir.resolve("concurrent/crypto.properties");
        Callable<String> bootstrap = () -> {
            StandardEnvironment environment = environment(secretFile, Map.of());
            new CryptoSecretEnvironmentPostProcessor(new SecureRandom()).postProcessEnvironment(
                    environment, new SpringApplication(Object.class));
            return environment.getProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(java.util.List.of(bootstrap, bootstrap));
            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReadCompleteSecretFileWhileAnotherJvmHoldsSharedLock() throws Exception {
        Path secretFile = tempDir.resolve("shared-read/crypto.properties");
        StandardEnvironment initial = environment(secretFile, Map.of());
        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                initial, new SpringApplication(Object.class));
        byte[] originalContent = Files.readAllBytes(secretFile);

        Path lockFile = secretFile.resolveSibling(secretFile.getFileName() + ".lock");
        Path childOutput = tempDir.resolve("shared-read-child.log");
        Process child = null;
        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock sharedLock = channel.lock(0L, Long.MAX_VALUE, true)) {
            assertThat(sharedLock.isShared()).isTrue();
            child = startBootstrapProcess(secretFile, childOutput);

            boolean completed = child.waitFor(5, TimeUnit.SECONDS);
            assertThat(completed)
                    .as("子 JVM 读取完整密钥文件不应等待另一个 JVM 的共享锁")
                    .isTrue();
            assertThat(child.exitValue())
                    .as(Files.readString(childOutput))
                    .isZero();
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
            }
        }
        assertThat(Files.readAllBytes(secretFile)).isEqualTo(originalContent);
    }

    @Test
    void shouldRestrictSecretFilePermissionsOnPosixFileSystems() throws IOException {
        Path secretFile = tempDir.resolve("permissions/crypto.properties");
        StandardEnvironment environment = environment(secretFile, Map.of());
        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));

        if (Files.getFileAttributeView(secretFile, PosixFileAttributeView.class) != null) {
            assertThat(Files.getPosixFilePermissions(secretFile)).isEqualTo(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            assertThat(Files.getPosixFilePermissions(secretFile.getParent())).isEqualTo(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    void shouldRegisterBootstrapBeforeApplicationConfigurationBinding() throws IOException {
        boolean registered = false;
        var resources = CryptoSecretEnvironmentPostProcessor.class.getClassLoader()
                .getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            Properties factories = new Properties();
            try (InputStream input = resources.nextElement().openStream()) {
                factories.load(input);
            }
            String processors = factories.getProperty("org.springframework.boot.env.EnvironmentPostProcessor");
            if (processors != null && processors.contains(CryptoSecretEnvironmentPostProcessor.class.getName())) {
                registered = true;
                break;
            }
        }
        assertThat(registered).isTrue();
    }

    @Test
    void springApplicationShouldInjectGeneratedSecretsWithoutManualExport() {
        Path secretFile = tempDir.resolve("spring-application/crypto.properties");
        SpringApplication application = new SpringApplication(BootstrapTestConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF",
                "--forge.crypto.bootstrap.file=" + secretFile)) {
            assertThat(context.getEnvironment().getProperty(
                    CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY)).isNotBlank();
            assertThat(context.getEnvironment().getProperty(
                    CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY)).isNotBlank();
            assertThat(context.getEnvironment().getProperty(
                    CryptoSecretEnvironmentPostProcessor.CAPABILITY_CLIENT_PEPPER_PROPERTY)).isNotBlank();
            assertThat(context.getEnvironment().getProperty(
                    CryptoSecretEnvironmentPostProcessor.CAPABILITY_TOKEN_PEPPER_PROPERTY)).isNotBlank();
            assertThat(context.getEnvironment().getProperty(
                    CryptoSecretEnvironmentPostProcessor.CAPABILITY_AUTHORIZATION_CODE_PEPPER_PROPERTY)).isNotBlank();
            assertThat(secretFile).isRegularFile();
        }
    }

    private void writeLegacySecretFile(Path secretFile) throws IOException {
        Files.createDirectories(secretFile.getParent());
        Properties properties = new Properties();
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.SECRET_KEY_PROPERTY,
                Base64.getEncoder().encodeToString(new byte[16]));
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.PERSISTENCE_ENABLED_PROPERTY, "true");
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.WRITE_VERSIONED_PROPERTY, "true");
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.LEGACY_READ_ENABLED_PROPERTY, "false");
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_ID_PROPERTY, "legacy-key");
        byte[] activeKey = new byte[16];
        activeKey[0] = 1;
        properties.setProperty(CryptoSecretEnvironmentPostProcessor.ACTIVE_KEY_PROPERTY,
                Base64.getEncoder().encodeToString(activeKey));
        try (OutputStream output = Files.newOutputStream(secretFile)) {
            properties.store(output, "legacy Forge crypto bootstrap");
        }
    }

    private StandardEnvironment environment(Path secretFile, Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new java.util.LinkedHashMap<>(overrides);
        properties.put(CryptoSecretEnvironmentPostProcessor.BOOTSTRAP_FILE_PROPERTY, secretFile.toString());
        environment.getPropertySources().addFirst(new MapPropertySource("testOverrides", properties));
        return environment;
    }

    private Process startBootstrapProcess(Path secretFile, Path outputFile) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                CryptoSecretBootstrapProcess.class.getName(),
                secretFile.toString())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
    }

    @Configuration(proxyBeanMethods = false)
    static class BootstrapTestConfiguration {
    }
}
