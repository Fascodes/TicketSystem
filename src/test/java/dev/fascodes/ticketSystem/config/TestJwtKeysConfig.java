package dev.fascodes.ticketSystem.config;

import dev.fascodes.ticketSystem.common.TestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class TestJwtKeysConfig {

    private static final Path KEYS_DIR = TestKeys.writeKeysDir();

    @Bean
    DynamicPropertyRegistrar jwtKeysDir() {
        return registry -> registry.add("app.jwt.keys-dir", KEYS_DIR::toString);
    }
}
