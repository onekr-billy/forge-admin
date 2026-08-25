package com.mdframe.forge.plugin.capability.identity.oauth;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientActorMode;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityRequiredCondition;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;

@RestController
@RequestMapping("/ai/capability/oauth")
@RequiredArgsConstructor
@Conditional(CapabilityIdentityRequiredCondition.class)
public class CapabilityAuthorizationController {

    private final AiCapabilityClientMapper clientMapper;
    private final OAuthRequestValidator requestValidator;
    private final DelegationAuthorizationCodeStore authorizationCodeStore;
    private final CapabilityIdentityProperties properties;
    private final CapabilityAccessTokenService accessTokenService;

    @GetMapping("/authorization-request")
    @OperationLog(
            module = "MCP用户授权",
            type = OperationType.QUERY,
            desc = "校验MCP授权请求",
            saveRequestParams = false,
            saveResponseResult = false)
    public RespInfo<AuthorizationRequestView> authorizationRequest(
            @RequestParam("client_id") String clientId,
            @RequestParam("response_type") String responseType,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam String resource,
            @RequestParam String scope,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam("code_challenge_method") String codeChallengeMethod,
            @RequestParam(required = false) String state) {
        LoginUser user = requireCurrentUser();
        AiCapabilityClient client = requireClient(user.getTenantId(), clientId);
        ValidatedAuthorizationRequest request = requestValidator.validateAuthorizationRequest(
                client, responseType, redirectUri, resource, scope,
                codeChallenge, codeChallengeMethod, state);
        requireCompatibleActiveOrganization(user, client);
        return RespInfo.success(new AuthorizationRequestView(
                client.getId().toString(), client.getClientName(), request.scopes(),
                user.getTenantId(), user.getTenantName(), user.getActiveOrgId(),
                user.getActiveOrgName(), properties.validatedAccessTokenTtl().toSeconds()));
    }

    @PostMapping("/authorize")
    @OperationLog(
            module = "MCP用户授权",
            type = OperationType.OTHER,
            desc = "确认MCP用户委托授权",
            saveRequestParams = false,
            saveResponseResult = false)
    public RespInfo<AuthorizationRedirectResponse> authorize(
            @Valid @RequestBody AuthorizationDecisionRequest decision) {
        LoginUser user = requireCurrentUser();
        AiCapabilityClient client = requireClient(user.getTenantId(), decision.clientId());
        ValidatedAuthorizationRequest request = requestValidator.validateAuthorizationRequest(
                client, decision.responseType(), decision.redirectUri(), decision.resource(),
                decision.scope(), decision.codeChallenge(), decision.codeChallengeMethod(),
                decision.state());
        requireCompatibleActiveOrganization(user, client);

        if (!decision.approved()) {
            return RespInfo.success(new AuthorizationRedirectResponse(buildRedirect(
                    request.redirectUri(), "error", "access_denied", request.state())));
        }

        String code = authorizationCodeStore.issue(new DelegationAuthorizationCode(
                client.getId(), client.getClientCode(), client.getCredentialVersion(),
                user.getUserId(), serviceUserId(client), user.getTenantId(),
                user.getActiveOrgId(), request.redirectUri(), request.resource(),
                request.scopes(), request.codeChallenge()));
        return RespInfo.success(new AuthorizationRedirectResponse(buildRedirect(
                request.redirectUri(), "code", code, request.state())));
    }

    @PostMapping("/token/revoke")
    @OperationLog(
            module = "MCP用户授权",
            type = OperationType.OTHER,
            desc = "撤销当前用户MCP委托令牌",
            saveRequestParams = false,
            saveResponseResult = false)
    public RespInfo<Void> revokeUserToken(@Valid @RequestBody UserTokenRevokeRequest request) {
        LoginUser user = requireCurrentUser();
        requestValidator.requireLength(
                request.clientId(), OAuthRequestValidator.MAX_CLIENT_ID_LENGTH, "client_id");
        requestValidator.requireLength(
                request.token(), OAuthRequestValidator.MAX_TOKEN_VALUE_LENGTH, "token");
        Long clientId;
        try {
            clientId = Long.valueOf(request.clientId());
        }
        catch (NumberFormatException exception) {
            throw new BusinessException(400, "invalid_client");
        }
        accessTokenService.revokeUserToken(
                request.token(), user.getUserId(), user.getTenantId(), clientId);
        return RespInfo.success();
    }

    private LoginUser requireCurrentUser() {
        LoginUser user = SessionHelper.getLoginUser();
        if (user == null
                || user.getUserId() == null || user.getUserId() <= 0
                || user.getTenantId() == null || user.getTenantId() <= 0
                || user.getActiveOrgId() == null || user.getActiveOrgId() <= 0
                || !EnableStatus.ENABLED.matches(user.getUserStatus())
                || Boolean.TRUE.equals(user.getForcePasswordChange())) {
            throw new BusinessException(401, "当前用户登录态不满足 MCP 授权要求");
        }
        return user;
    }

    private AiCapabilityClient requireClient(Long tenantId, String clientId) {
        requestValidator.requireLength(
                clientId, OAuthRequestValidator.MAX_CLIENT_ID_LENGTH, "client_id");
        Long id;
        try {
            id = Long.valueOf(clientId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "invalid_client");
        }
        AiCapabilityClient client = clientMapper.selectTenantById(tenantId, id);
        if (client == null) {
            throw new BusinessException(400, "invalid_client");
        }
        return client;
    }

    private void requireCompatibleActiveOrganization(LoginUser user, AiCapabilityClient client) {
        CapabilityClientActorMode actorMode = actorMode(client);
        if (actorMode == CapabilityClientActorMode.HYBRID
                && !Objects.equals(user.getActiveOrgId(), client.getActiveOrgId())) {
            throw new BusinessException(403, "当前组织与 MCP 客户端绑定组织不一致");
        }
    }

    private Long serviceUserId(AiCapabilityClient client) {
        return actorMode(client) == CapabilityClientActorMode.USER_DELEGATION
                ? null : client.getServiceUserId();
    }

    private CapabilityClientActorMode actorMode(AiCapabilityClient client) {
        try {
            return client.getActorMode() == null || client.getActorMode().isBlank()
                    ? CapabilityClientActorMode.HYBRID
                    : CapabilityClientActorMode.valueOf(client.getActorMode());
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "invalid_client");
        }
    }

    private String buildRedirect(
            String registeredRedirectUri,
            String resultName,
            String resultValue,
            String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(registeredRedirectUri)
                .queryParam(resultName, resultValue);
        if (state != null) {
            builder.queryParam("state", state);
        }
        return builder.build().encode().toUriString();
    }
}
