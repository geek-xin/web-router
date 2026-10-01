package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateScriptGeneratorTest {

    private static final String HOME = "/opt/wrouter";
    private static final String UPDATES = "/opt/wrouter/updates";

    @Test
    void jarShellScriptWaitsBacksUpUnzipsRestartsAndRollsBack() {
        String script = UpdateScriptGenerator.generate("jar", UpdatePlatform.LINUX, "1.4.0", "1.3.0",
                "wrouter-1.4.0-jar.zip", UPDATES + "/wrouter-1.4.0-jar.zip", HOME, UPDATES);

        assertThat(script).startsWith("#!/bin/sh");
        assertThat(script).contains("HOME_DIR='/opt/wrouter'");
        assertThat(script).contains("LOG_FILE=\"$UPDATES_DIR/update.log\"");
        assertThat(script).contains("wait_for_exit");
        assertThat(script).contains("kill -0");
        assertThat(script).contains("BACKUP_DIR='/opt/wrouter/updates/backup-1.3.0'");
        assertThat(script).contains("backup_entry 'wrouter-1.3.0.jar'");
        assertThat(script).contains("restore_entry 'wrouter-1.3.0.jar'");
        assertThat(script).contains("unzip -oq");
        assertThat(script).contains("ditto -x -k");
        assertThat(script).contains("run.sh");
        assertThat(script).contains("java -jar");
        // 必须优先启动目标版本的 jar，避免回退到旧版本
        assertThat(script).contains("JAR_FILE=\"$HOME_DIR/wrouter-$TARGET_VERSION.jar\"");
        assertThat(script).contains("ls -t \"$HOME_DIR\"/wrouter-*.jar");
        assertThat(script).contains("TMP_DIR='/opt/wrouter/updates/unpack-1.4.0'");
    }

    @Test
    void linuxAppImageScriptUnpacksTarGzAndStartsTheAppBinary() {
        String script = UpdateScriptGenerator.generate("app-image", UpdatePlatform.LINUX, "1.4.0", "1.3.0",
                "wrouter-1.4.0-linux-x64-app.tar.gz", UPDATES + "/wrouter-1.4.0-linux-x64-app.tar.gz",
                HOME, UPDATES);

        assertThat(script).contains("ASSET_FORMAT='tar.gz'");
        assertThat(script).contains("tar -xzf");
        assertThat(script).contains("backup_entry 'wrouter'");
        assertThat(script).contains("/bin/wrouter");
        assertThat(script).doesNotContain("java -jar");
    }

    @Test
    void macosAppImageScriptTargetsTheAppBundle() {
        String script = UpdateScriptGenerator.generate("app-image", UpdatePlatform.MACOS, "1.4.0", "1.3.0",
                "wrouter-1.4.0-macos-arm64-app.zip", UPDATES + "/wrouter-1.4.0-macos-arm64-app.zip",
                HOME, UPDATES);

        assertThat(script).contains("backup_entry 'wrouter.app'");
        assertThat(script).contains("Contents/MacOS/wrouter");
    }

    @Test
    void windowsScriptUsesBatchSyntaxAndPowerShellExpansion() {
        String script = UpdateScriptGenerator.generate("jar", UpdatePlatform.WINDOWS, "1.4.0", "1.3.0",
                "wrouter-1.4.0-jar.zip", "C:/wrouter/updates/wrouter-1.4.0-jar.zip",
                "C:/wrouter", "C:/wrouter/updates");

        assertThat(script).startsWith("@echo off");
        assertThat(script).contains("Expand-Archive");
        assertThat(script).contains(":rollback");
        assertThat(script).contains("run.bat");
        assertThat(script).contains("backup-1.3.0");
        assertThat(script).contains("wrouter-%TARGET_VERSION%.jar");
        assertThat(script).contains("\r\n");
    }

    @Test
    void scriptFileNamesFollowThePlatformConvention() {
        assertThat(UpdateScriptGenerator.scriptFileName(UpdatePlatform.LINUX)).isEqualTo("apply-update.sh");
        assertThat(UpdateScriptGenerator.scriptFileName(UpdatePlatform.MACOS)).isEqualTo("apply-update.sh");
        assertThat(UpdateScriptGenerator.scriptFileName(UpdatePlatform.WINDOWS)).isEqualTo("apply-update.bat");
    }
}
