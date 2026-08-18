package com.mdframe.forge.starter.crypto.config;

import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Path;
import java.util.Map;

/**
 * Starts crypto bootstrap in a separate JVM for cross-process lock tests.
 */
public final class CryptoSecretBootstrapProcess {

    private CryptoSecretBootstrapProcess() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the crypto bootstrap file path");
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "processOverrides",
                Map.of(CryptoSecretEnvironmentPostProcessor.BOOTSTRAP_FILE_PROPERTY, Path.of(args[0]).toString())));

        new CryptoSecretEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication(Object.class));
    }
}
