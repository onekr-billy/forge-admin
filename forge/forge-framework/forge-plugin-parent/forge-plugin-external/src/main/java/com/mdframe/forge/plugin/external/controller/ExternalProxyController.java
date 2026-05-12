package com.mdframe.forge.plugin.external.controller;

import com.mdframe.forge.plugin.external.service.ExternalProxyService;
import com.mdframe.forge.plugin.external.vo.ExternalApiDebugResult;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/external/proxy")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
@Slf4j
public class ExternalProxyController {

    private final ExternalProxyService proxyService;

    @PostMapping("/{apiId}")
    public RespInfo<Object> proxyPost(
            @PathVariable Long apiId,
            @RequestBody(required = false) Map<String, Object> params) {
        log.info("请求三方代理接口:{},请求参数:{}",apiId, params);
        Object result = proxyService.proxyRequest(apiId, params);
        return RespInfo.success(result);
    }

    @GetMapping("/{apiId}")
    public RespInfo<Object> proxyGet(
            @PathVariable Long apiId,
            @RequestParam(required = false) Map<String, Object> params) {
        Object result = proxyService.proxyRequest(apiId, params);
        return RespInfo.success(result);
    }

    @PostMapping("/debug/{apiId}")
    public RespInfo<ExternalApiDebugResult> debug(
            @PathVariable Long apiId,
            @RequestBody(required = false) Map<String, Object> params) {
        return RespInfo.success(proxyService.debugRequest(apiId, params));
    }
}
