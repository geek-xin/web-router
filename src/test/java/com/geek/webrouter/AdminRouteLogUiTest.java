package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRouteLogUiTest {

    @Test
    void routeLogTablesUseOperationHeaderAndHideDiagnosticParamsColumn() throws Exception {
        String source = Files.readString(Path.of("frontend/src/features/logs/RouteLogDialog.tsx"));

        assertThat(source).contains("<TableHead>操作</TableHead>");
        assertThat(source).contains("诊断分析");
        assertThat(source).doesNotContain("<TableHead>参数</TableHead>");
        assertThat(source).contains("拷贝分析");
    }

    @Test
    void singleDurationTopUsesBackendStartupScopeList() throws Exception {
        String dialog = Files.readString(Path.of("frontend/src/features/logs/RouteLogDialog.tsx"));
        String utils = Files.readString(Path.of("frontend/src/features/logs/log-utils.ts"));

        assertThat(dialog).contains("state.durationTopLogs.length > 0 ? state.durationTopLogs : buildDurationTopLogs(state.recentLogs)");
        assertThat(utils).contains("durationTopLogs: (snapshot.durationTopLogs || buildDurationTopLogs(recentLogs)).slice(0, ROUTE_LOG_MAX_RECENT)");
        assertThat(utils).contains("durationTopLogs: updateDurationTopLogs(state.durationTopLogs, entry)");
    }

    @Test
    void routeLogTimeAndAccessColumnsFavorCompactTimeAndAccessAddress() throws Exception {
        String source = Files.readString(Path.of("frontend/src/features/logs/RouteLogDialog.tsx"));

        assertThat(source).contains("formatTime(entry.time)");
        assertThat(source).contains("entry.accessAddress || '-'");
        assertThat(source).contains("实际访问: ");
    }
}
