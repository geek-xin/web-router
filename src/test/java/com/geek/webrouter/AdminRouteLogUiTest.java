package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRouteLogUiTest {

    @Test
    void routeLogTablesUseOperationHeaderAndHideDiagnosticParamsColumn() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(template).doesNotContain("<th>详情</th>");
        assertThat(template).contains("<th>操作</th>");
        assertThat(template).doesNotContain("<th>参数</th>");
        assertThat(script).doesNotContain("tr.appendChild(cell(logDetailText(entry.requestParams), 'path-cell'));");
    }

    @Test
    void singleDurationTopUsesBackendStartupScopeList() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(script).contains("durationTopLogs: (snapshot.durationTopLogs || buildDurationTopLogs(logs)).slice(0, ROUTE_LOG_MAX_RECENT)");
        assertThat(script).contains("renderSlowLogs(routeLogState.durationTopLogs || [])");
        assertThat(script).contains("routeLogState.durationTopLogs = updateDurationTopLogs(routeLogState.durationTopLogs, entry)");
    }

    @Test
    void routeLogTimeAndAccessColumnsFavorCompactTimeAndWiderAccessAddress() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));

        assertThat(css).contains("width: 146px;\n    white-space: nowrap;");
        assertThat(css).contains("width: 190px;\n    white-space: nowrap;");
    }
}
