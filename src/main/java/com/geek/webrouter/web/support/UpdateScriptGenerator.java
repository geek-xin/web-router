package com.geek.webrouter.web.support;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新脚本生成器 — 根据安装形态与当前平台生成可重复执行的更新脚本。
 *
 * <p>脚本执行流程：等待当前进程退出 → 备份旧文件到 updates/backup-旧版本/ →
 * 解压并替换 → 重启应用；任一步失败都会回滚，并把过程写入 updates/update.log。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class UpdateScriptGenerator {

    /** Linux/macOS 更新脚本文件名。 */
    public static final String SHELL_SCRIPT_NAME = "apply-update.sh";

    /** Windows 更新脚本文件名。 */
    public static final String BATCH_SCRIPT_NAME = "apply-update.bat";

    /** 应用目录名（app-image）。 */
    private static final String APP_DIR_NAME = "wrouter";

    /** macOS 应用包目录名。 */
    private static final String MACOS_BUNDLE_NAME = "wrouter.app";

    /** 解压临时目录前缀。 */
    private static final String UNPACK_DIR_PREFIX = "unpack-";

    /** 等待旧进程退出的最长秒数。 */
    private static final int WAIT_SECONDS = 30;

    private UpdateScriptGenerator() {
    }

    /** 生成脚本文件名。 */
    public static String scriptFileName(UpdatePlatform platform) {
        return platform.isWindows() ? BATCH_SCRIPT_NAME : SHELL_SCRIPT_NAME;
    }

    /**
     * 生成更新脚本内容。
     *
     * @param installMode    安装形态：jar | app-image
     * @param platform       目标平台
     * @param version        目标版本
     * @param currentVersion 当前版本（用于备份目录命名）
     * @param assetName      更新包文件名
     * @param archivePath    更新包绝对路径
     * @param homeDir        应用根目录
     * @param updatesDir     更新工作目录
     * @return 脚本文本
     */
    public static String generate(String installMode, UpdatePlatform platform, String version,
                                  String currentVersion, String assetName, String archivePath,
                                  String homeDir, String updatesDir) {
        boolean appImage = "app-image".equalsIgnoreCase(installMode);
        String format = assetName != null && assetName.toLowerCase(java.util.Locale.ROOT).endsWith(".tar.gz")
                ? "tar.gz" : "zip";
        String backupDir = join(updatesDir, "backup-" + currentVersion);
        String unpackDir = join(updatesDir, UNPACK_DIR_PREFIX + version);
        if (platform.isWindows()) {
            return windowsScript(appImage, format, version, currentVersion, assetName, archivePath,
                    homeDir, updatesDir, backupDir, unpackDir);
        }
        return shellScript(appImage, platform, format, version, currentVersion, assetName, archivePath,
                homeDir, updatesDir, backupDir, unpackDir);
    }

    private static String shellScript(boolean appImage, UpdatePlatform platform, String format, String version,
                                      String currentVersion, String assetName, String archivePath, String homeDir,
                                      String updatesDir, String backupDir, String unpackDir) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/sh\n");
        script.append("# wrouter 自动更新脚本（由应用生成，可重复执行）\n");
        script.append("# 目标版本: ").append(version).append(" / 当前版本: ").append(currentVersion).append('\n');
        script.append("# 安装形态: ").append(appImage ? "app-image" : "jar")
                .append(" / 平台: ").append(platform.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        script.append("\n");
        script.append("HOME_DIR=").append(quote(homeDir)).append('\n');
        script.append("UPDATES_DIR=").append(quote(updatesDir)).append('\n');
        script.append("LOG_FILE=\"$UPDATES_DIR/update.log\"\n");
        script.append("ARCHIVE=").append(quote(archivePath)).append('\n');
        script.append("BACKUP_DIR=").append(quote(backupDir)).append('\n');
        script.append("TMP_DIR=").append(quote(unpackDir)).append('\n');
        script.append("TARGET_VERSION=").append(quote(version)).append('\n');
        script.append("ASSET_NAME=").append(quote(assetName)).append('\n');
        script.append("ASSET_FORMAT=").append(quote(format)).append('\n');
        script.append("INSTALL_MODE=").append(quote(appImage ? "app-image" : "jar")).append("\n\n");
        script.append("mkdir -p \"$UPDATES_DIR\"\n\n");
        script.append("log() {\n    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] $1\" >> \"$LOG_FILE\"\n}\n\n");
        script.append("wait_for_exit() {\n")
                .append("    pid=\"$1\"\n")
                .append("    [ -z \"$pid\" ] && return 0\n")
                .append("    i=0\n")
                .append("    while [ \"$i\" -lt ").append(WAIT_SECONDS).append(" ]; do\n")
                .append("        kill -0 \"$pid\" 2>/dev/null || return 0\n")
                .append("        sleep 1\n")
                .append("        i=$((i + 1))\n")
                .append("    done\n")
                .append("    return 0\n}\n\n");
        script.append("backup_entry() {\n")
                .append("    src=\"$HOME_DIR/$1\"\n")
                .append("    [ -e \"$src\" ] || return 0\n")
                .append("    mkdir -p \"$BACKUP_DIR/$(dirname \"$1\")\"\n")
                .append("    cp -R \"$src\" \"$BACKUP_DIR/$1\" 2>/dev/null\n")
                .append("    log \"已备份: $1\"\n}\n\n");
        script.append("restore_entry() {\n")
                .append("    src=\"$BACKUP_DIR/$1\"\n")
                .append("    [ -e \"$src\" ] || return 0\n")
                .append("    rm -rf \"$HOME_DIR/$1\"\n")
                .append("    mkdir -p \"$HOME_DIR/$(dirname \"$1\")\"\n")
                .append("    cp -R \"$src\" \"$HOME_DIR/$1\" 2>/dev/null\n")
                .append("    log \"已回滚: $1\"\n}\n\n");
        script.append("rollback() {\n");
        for (String entry : backupEntries(appImage, platform, currentVersion)) {
            script.append("    restore_entry ").append(quote(entry)).append('\n');
        }
        script.append("}\n\n");
        script.append("restart() {\n");
        script.append(shellRestart(appImage, platform));
        script.append("}\n\n");
        script.append("log \"开始更新: $TARGET_VERSION (asset=$ASSET_NAME, mode=$INSTALL_MODE)\"\n");
        script.append("if [ ! -f \"$ARCHIVE\" ]; then\n")
                .append("    log \"错误: 更新包不存在: $ARCHIVE\"\n")
                .append("    exit 1\n")
                .append("fi\n\n");
        script.append("if [ \"$#\" -ge 1 ]; then\n")
                .append("    wait_for_exit \"$1\"\n")
                .append("fi\n\n");
        script.append("rm -rf \"$BACKUP_DIR\"\n")
                .append("mkdir -p \"$BACKUP_DIR\"\n");
        for (String entry : backupEntries(appImage, platform, currentVersion)) {
            script.append("backup_entry ").append(quote(entry)).append('\n');
        }
        script.append('\n');
        script.append("rm -rf \"$TMP_DIR\"\n")
                .append("mkdir -p \"$TMP_DIR\"\n")
                .append("if [ \"$ASSET_FORMAT\" = \"tar.gz\" ]; then\n")
                .append("    tar -xzf \"$ARCHIVE\" -C \"$TMP_DIR\"\n")
                .append("else\n")
                .append("    if command -v unzip >/dev/null 2>&1; then\n")
                .append("        unzip -oq \"$ARCHIVE\" -d \"$TMP_DIR\"\n")
                .append("    elif command -v ditto >/dev/null 2>&1; then\n")
                .append("        ditto -x -k \"$ARCHIVE\" \"$TMP_DIR\"\n")
                .append("    else\n")
                .append("        log \"错误: 未找到 unzip 或 ditto 命令\"\n")
                .append("        rollback\n")
                .append("        exit 1\n")
                .append("    fi\n")
                .append("fi\n")
                .append("if [ ! -d \"$TMP_DIR\" ] || [ -z \"$(ls -A \"$TMP_DIR\" 2>/dev/null)\" ]; then\n")
                .append("    log \"错误: 更新包解压失败或内容为空\"\n")
                .append("    rollback\n")
                .append("    exit 1\n")
                .append("fi\n\n");
        script.append("PAYLOAD_DIR=\"$TMP_DIR\"\n")
                .append("ENTRY_COUNT=$(ls -A \"$TMP_DIR\" | wc -l | tr -d ' ')\n")
                .append("if [ \"$ENTRY_COUNT\" = \"1\" ]; then\n")
                .append("    ONLY_ENTRY=\"$TMP_DIR/$(ls -A \"$TMP_DIR\" | head -n 1)\"\n")
                .append("    [ -d \"$ONLY_ENTRY\" ] && PAYLOAD_DIR=\"$ONLY_ENTRY\"\n")
                .append("fi\n\n");
        script.append(shellInstall(appImage, platform));
        script.append('\n');
        script.append("if restart; then\n")
                .append("    log \"更新完成: $TARGET_VERSION\"\n")
                .append("    rm -rf \"$TMP_DIR\"\n")
                .append("    exit 0\n")
                .append("fi\n")
                .append("log \"错误: 重启应用失败，开始回滚\"\n")
                .append("rollback\n")
                .append("exit 1\n");
        return script.toString();
    }

    private static String shellInstall(boolean appImage, UpdatePlatform platform) {
        StringBuilder install = new StringBuilder();
        if (!appImage) {
            install.append("cp -R \"$PAYLOAD_DIR\"/. \"$HOME_DIR\"/ 2>/dev/null\n");
            install.append("if ! ls \"$HOME_DIR\"/wrouter-*.jar >/dev/null 2>&1; then\n")
                    .append("    log \"警告: 应用目录下没有 wrouter-*.jar，请确认更新包结构\"\n")
                    .append("fi\n");
            return install.toString();
        }
        if (platform == UpdatePlatform.MACOS) {
            install.append("APP_DIR=\"$HOME_DIR/").append(MACOS_BUNDLE_NAME).append("\"\n");
            install.append("if [ -d \"$PAYLOAD_DIR/").append(MACOS_BUNDLE_NAME).append("\" ]; then\n")
                    .append("    PAYLOAD_DIR=\"$PAYLOAD_DIR/").append(MACOS_BUNDLE_NAME).append("\"\n")
                    .append("fi\n");
            install.append("rm -rf \"$APP_DIR\"\n")
                    .append("mkdir -p \"$APP_DIR\"\n")
                    .append("cp -R \"$PAYLOAD_DIR\"/. \"$APP_DIR\"/ 2>/dev/null\n")
                    .append("chmod +x \"$APP_DIR/Contents/MacOS/wrouter\" 2>/dev/null\n");
            return install.toString();
        }
        install.append("APP_DIR=\"$HOME_DIR/").append(APP_DIR_NAME).append("\"\n");
        install.append("if [ -d \"$PAYLOAD_DIR/").append(APP_DIR_NAME).append("\" ]; then\n")
                .append("    PAYLOAD_DIR=\"$PAYLOAD_DIR/").append(APP_DIR_NAME).append("\"\n")
                .append("fi\n");
        install.append("rm -rf \"$APP_DIR\"\n")
                .append("mkdir -p \"$APP_DIR\"\n")
                .append("cp -R \"$PAYLOAD_DIR\"/. \"$APP_DIR\"/ 2>/dev/null\n")
                .append("chmod +x \"$APP_DIR/bin/wrouter\" \"$APP_DIR/wrouter\" 2>/dev/null\n");
        return install.toString();
    }

    private static String shellRestart(boolean appImage, UpdatePlatform platform) {
        if (!appImage) {
            return "    if [ -f \"$HOME_DIR/run.sh\" ]; then\n"
                    + "        (cd \"$HOME_DIR\" && nohup sh ./run.sh >> \"$LOG_FILE\" 2>&1 &)\n"
                    + "        log \"已通过 run.sh 触发重启\"\n"
                    + "        return 0\n"
                    + "    fi\n"
                    + "    JAR_FILE=\"$HOME_DIR/wrouter-$TARGET_VERSION.jar\"\n"
                    + "    if [ ! -f \"$JAR_FILE\" ]; then\n"
                    + "        JAR_FILE=$(ls -t \"$HOME_DIR\"/wrouter-*.jar 2>/dev/null | head -n 1)\n"
                    + "    fi\n"
                    + "    if [ -n \"$JAR_FILE\" ]; then\n"
                    + "        (cd \"$HOME_DIR\" && nohup java -jar \"$JAR_FILE\" >> \"$LOG_FILE\" 2>&1 &)\n"
                    + "        log \"已通过 java -jar 触发重启\"\n"
                    + "        return 0\n"
                    + "    fi\n"
                    + "    return 1\n";
        }
        if (platform == UpdatePlatform.MACOS) {
            return "    APP_BIN=\"$HOME_DIR/" + MACOS_BUNDLE_NAME + "/Contents/MacOS/wrouter\"\n"
                    + "    if [ -x \"$APP_BIN\" ]; then\n"
                    + "        (cd \"$HOME_DIR\" && nohup \"$APP_BIN\" >> \"$LOG_FILE\" 2>&1 &)\n"
                    + "        log \"已触发 app-image 重启\"\n"
                    + "        return 0\n"
                    + "    fi\n"
                    + "    return 1\n";
        }
        return "    for APP_BIN in \"$HOME_DIR/" + APP_DIR_NAME + "/bin/wrouter\" \"$HOME_DIR/"
                + APP_DIR_NAME + "/wrouter\"; do\n"
                + "        if [ -x \"$APP_BIN\" ]; then\n"
                + "            (cd \"$HOME_DIR\" && nohup \"$APP_BIN\" >> \"$LOG_FILE\" 2>&1 &)\n"
                + "            log \"已触发 app-image 重启\"\n"
                + "            return 0\n"
                + "        fi\n"
                + "    done\n"
                + "    return 1\n";
    }

    private static String windowsScript(boolean appImage, String format, String version, String currentVersion,
                                        String assetName, String archivePath, String homeDir, String updatesDir,
                                        String backupDir, String unpackDir) {
        StringBuilder script = new StringBuilder();
        script.append("@echo off\r\n");
        script.append("rem wrouter 自动更新脚本（由应用生成，可重复执行）\r\n");
        script.append("rem 目标版本: ").append(version).append(" / 当前版本: ").append(currentVersion).append("\r\n");
        script.append("rem 安装形态: ").append(appImage ? "app-image" : "jar").append(" / 平台: windows\r\n");
        script.append("setlocal\r\n\r\n");
        script.append("set \"HOME_DIR=").append(homeDir).append("\"\r\n");
        script.append("set \"UPDATES_DIR=").append(updatesDir).append("\"\r\n");
        script.append("set \"LOG_FILE=%UPDATES_DIR%\\update.log\"\r\n");
        script.append("set \"ARCHIVE=").append(archivePath).append("\"\r\n");
        script.append("set \"BACKUP_DIR=").append(backupDir).append("\"\r\n");
        script.append("set \"TMP_DIR=").append(unpackDir).append("\"\r\n");
        script.append("set \"TARGET_VERSION=").append(version).append("\"\r\n");
        script.append("set \"ASSET_FORMAT=").append(format).append("\"\r\n\r\n");
        script.append("if not exist \"%UPDATES_DIR%\" mkdir \"%UPDATES_DIR%\"\r\n");
        script.append("call :log \"开始更新: %TARGET_VERSION% (asset=").append(assetName).append(")\"\r\n");
        script.append("if not exist \"%ARCHIVE%\" (\r\n")
                .append("    call :log \"错误: 更新包不存在: %ARCHIVE%\"\r\n")
                .append("    exit /b 1\r\n")
                .append(")\r\n\r\n");
        script.append("call :wait_for_exit \"%~1\"\r\n\r\n");
        script.append("if exist \"%BACKUP_DIR%\" rmdir /S /Q \"%BACKUP_DIR%\"\r\n");
        script.append("mkdir \"%BACKUP_DIR%\"\r\n");
        for (String entry : backupEntries(appImage, UpdatePlatform.WINDOWS, currentVersion)) {
            script.append("call :backup \"").append(entry).append("\"\r\n");
        }
        script.append("\r\n");
        script.append("if exist \"%TMP_DIR%\" rmdir /S /Q \"%TMP_DIR%\"\r\n");
        script.append("mkdir \"%TMP_DIR%\"\r\n");
        script.append("if \"%ASSET_FORMAT%\"==\"tar.gz\" (\r\n")
                .append("    tar -xzf \"%ARCHIVE%\" -C \"%TMP_DIR%\"\r\n")
                .append(") else (\r\n")
                .append("    powershell -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%TMP_DIR%' -Force\"\r\n")
                .append(")\r\n");
        script.append("if errorlevel 1 (\r\n")
                .append("    call :log \"错误: 更新包解压失败\"\r\n")
                .append("    call :rollback\r\n")
                .append("    exit /b 1\r\n")
                .append(")\r\n\r\n");
        script.append("set \"PAYLOAD_DIR=%TMP_DIR%\"\r\n");
        script.append("for /d %%D in (\"%TMP_DIR%\\*\") do set \"PAYLOAD_DIR=%%~fD\"\r\n\r\n");
        if (appImage) {
            script.append("set \"APP_DIR=%HOME_DIR%\\").append(APP_DIR_NAME).append("\"\r\n");
            script.append("if exist \"%PAYLOAD_DIR%\\").append(APP_DIR_NAME).append("\" set \"PAYLOAD_DIR=%PAYLOAD_DIR%\\")
                    .append(APP_DIR_NAME).append("\"\r\n");
            script.append("if exist \"%APP_DIR%\" rmdir /S /Q \"%APP_DIR%\"\r\n");
            script.append("mkdir \"%APP_DIR%\"\r\n");
            script.append("xcopy /E /I /Q /Y \"%PAYLOAD_DIR%\\*\" \"%APP_DIR%\\\" >NUL\r\n\r\n");
        } else {
            script.append("xcopy /E /I /Q /Y \"%PAYLOAD_DIR%\\*\" \"%HOME_DIR%\\\" >NUL\r\n\r\n");
        }
        script.append("call :restart\r\n");
        script.append("if errorlevel 1 (\r\n")
                .append("    call :log \"错误: 重启应用失败，开始回滚\"\r\n")
                .append("    call :rollback\r\n")
                .append("    exit /b 1\r\n")
                .append(")\r\n");
        script.append("call :log \"更新完成: %TARGET_VERSION%\"\r\n");
        script.append("if exist \"%TMP_DIR%\" rmdir /S /Q \"%TMP_DIR%\"\r\n");
        script.append("exit /b 0\r\n\r\n");
        script.append(":restart\r\n");
        if (appImage) {
            script.append("if not exist \"%HOME_DIR%\\").append(APP_DIR_NAME)
                    .append("\\wrouter.exe\" exit /b 1\r\n");
            script.append("start \"wrouter\" /D \"%HOME_DIR%\" \"%HOME_DIR%\\")
                    .append(APP_DIR_NAME).append("\\wrouter.exe\"\r\n");
        } else {
            script.append("if exist \"%HOME_DIR%\\run.bat\" (\r\n")
                    .append("    start \"wrouter\" /D \"%HOME_DIR%\" /B cmd /c run.bat\r\n")
                    .append("    exit /b 0\r\n")
                    .append(")\r\n");
            script.append("if exist \"%HOME_DIR%\\wrouter-%TARGET_VERSION%.jar\" (\r\n")
                    .append("    start \"wrouter\" /D \"%HOME_DIR%\" /B java -jar \"%HOME_DIR%\\wrouter-%TARGET_VERSION%.jar\"\r\n")
                    .append("    exit /b 0\r\n")
                    .append(")\r\n");
            script.append("for %%J in (\"%HOME_DIR%\\wrouter-*.jar\") do (\r\n")
                    .append("    start \"wrouter\" /D \"%HOME_DIR%\" /B java -jar \"%%~fJ\"\r\n")
                    .append("    exit /b 0\r\n")
                    .append(")\r\n");
            script.append("exit /b 1\r\n");
        }
        script.append("call :log \"已触发应用重启\"\r\n");
        script.append("exit /b 0\r\n\r\n");
        script.append(":backup\r\n");
        script.append("if not exist \"%HOME_DIR%\\%~1\" exit /b 0\r\n");
        script.append("if exist \"%HOME_DIR%\\%~1\\\" (\r\n");
        script.append("    xcopy /E /I /Q /Y \"%HOME_DIR%\\%~1\" \"%BACKUP_DIR%\\%~1\\\" >NUL\r\n");
        script.append(") else (\r\n");
        script.append("    copy /Y \"%HOME_DIR%\\%~1\" \"%BACKUP_DIR%\\%~1\" >NUL\r\n");
        script.append(")\r\n");
        script.append("call :log \"已备份: %~1\"\r\n");
        script.append("exit /b 0\r\n\r\n");
        script.append(":rollback\r\n");
        for (String entry : backupEntries(appImage, UpdatePlatform.WINDOWS, currentVersion)) {
            script.append("call :restore \"").append(entry).append("\"\r\n");
        }
        script.append("call :log \"回滚完成\"\r\n");
        script.append("exit /b 0\r\n\r\n");
        script.append(":restore\r\n");
        script.append("if not exist \"%BACKUP_DIR%\\%~1\" exit /b 0\r\n");
        script.append("if exist \"%BACKUP_DIR%\\%~1\\\" (\r\n");
        script.append("    if exist \"%HOME_DIR%\\%~1\" rmdir /S /Q \"%HOME_DIR%\\%~1\"\r\n");
        script.append("    xcopy /E /I /Q /Y \"%BACKUP_DIR%\\%~1\" \"%HOME_DIR%\\%~1\\\" >NUL\r\n");
        script.append(") else (\r\n");
        script.append("    copy /Y \"%BACKUP_DIR%\\%~1\" \"%HOME_DIR%\\%~1\" >NUL\r\n");
        script.append(")\r\n");
        script.append("call :log \"已回滚: %~1\"\r\n");
        script.append("exit /b 0\r\n\r\n");
        script.append(":wait_for_exit\r\n");
        script.append("set \"WAIT_PID=%~1\"\r\n");
        script.append("if \"%WAIT_PID%\"==\"\" exit /b 0\r\n");
        script.append("set /a WAIT_COUNT=0\r\n");
        script.append(":wait_loop\r\n");
        script.append("tasklist /FI \"PID eq %WAIT_PID%\" 2>NUL | find \"%WAIT_PID%\" >NUL\r\n");
        script.append("if errorlevel 1 exit /b 0\r\n");
        script.append("set /a WAIT_COUNT+=1\r\n");
        script.append("if %WAIT_COUNT% GEQ ").append(WAIT_SECONDS).append(" exit /b 0\r\n");
        script.append("timeout /t 1 /nobreak >NUL 2>&1\r\n");
        script.append("goto :wait_loop\r\n\r\n");
        script.append(":log\r\n");
        script.append(">>\"%LOG_FILE%\" echo [%DATE% %TIME%] %~1\r\n");
        script.append("exit /b 0\r\n");
        return script.toString();
    }

    private static List<String> backupEntries(boolean appImage, UpdatePlatform platform, String currentVersion) {
        if (!appImage) {
            List<String> entries = new ArrayList<>();
            if (currentVersion != null && !currentVersion.isBlank()
                    && !"unknown".equalsIgnoreCase(currentVersion)) {
                entries.add("wrouter-" + currentVersion + ".jar");
            }
            entries.add("version.properties");
            entries.add(platform.isWindows() ? "run.bat" : "run.sh");
            return entries;
        }
        return switch (platform) {
            case MACOS -> List.of(MACOS_BUNDLE_NAME);
            case WINDOWS, LINUX -> List.of(APP_DIR_NAME);
        };
    }

    private static String quote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }

    private static String join(String parent, String child) {
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
    }

    /** 便于测试与调用方：把 Path 转成脚本可用的绝对路径字符串。 */
    public static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
