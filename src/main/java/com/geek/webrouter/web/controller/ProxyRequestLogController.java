package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.result.Result;
import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.model.dto.ProxyRequestLogSnapshot;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;

@Controller
@RequestMapping("/admin/api/proxy-logs")
@RequiredArgsConstructor
public class ProxyRequestLogController {

    private final ProxyRequestLogService logService;

    @GetMapping
    @ResponseBody
    public Result<ProxyRequestLogSnapshot> snapshot() {
        return Result.success(logService.snapshot());
    }

    @GetMapping("/routes/{routeId}")
    @ResponseBody
    public Result<ProxyRequestLogSnapshot> routeSnapshot(@PathVariable String routeId) {
        return Result.success(logService.snapshot(routeId));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<ProxyRequestLogEntry>> stream() {
        return logService.stream()
                .map(entry -> ServerSentEvent.builder(entry).event("proxy-request").build());
    }

    @GetMapping(path = "/routes/{routeId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<ProxyRequestLogEntry>> routeStream(@PathVariable String routeId) {
        return logService.stream(routeId)
                .map(entry -> ServerSentEvent.builder(entry).event("proxy-request").build());
    }
}
