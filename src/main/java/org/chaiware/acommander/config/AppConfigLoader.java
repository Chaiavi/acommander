package org.chaiware.acommander.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppConfigLoader {
    private final ObjectMapper mapper;

    public AppConfigLoader() {
        mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AppConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Missing config file: " + configPath);
        }
        return mapper.readValue(configPath.toFile(), AppConfig.class);
    }
}
