package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    @Test
    void serverDefaultsToLoopbackAddressForAdminSafety() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("address: 127.0.0.1");
    }

    @Test
    void logbackRollsOutAndErrLogsBySizeAndDayForSevenDays() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(logback).contains("name=\"OUT_FILE\"");
        assertThat(logback).contains("name=\"ERR_FILE\"");
        assertThat(logback).contains("${LOG_PATH}/web-router.out.%d{yyyy-MM-dd}.%i.log");
        assertThat(logback).contains("${LOG_PATH}/web-router.err.%d{yyyy-MM-dd}.%i.log");
        assertThat(logback).contains("<maxFileSize>10MB</maxFileSize>");
        assertThat(logback).contains("<maxHistory>7</maxHistory>");
        assertThat(logback).contains("<level>ERROR</level>");
    }
}
