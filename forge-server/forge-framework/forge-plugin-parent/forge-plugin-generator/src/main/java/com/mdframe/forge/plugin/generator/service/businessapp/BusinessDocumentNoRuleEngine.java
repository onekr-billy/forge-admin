package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessDocumentNoRulePreviewDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentNoRulePreviewVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentNoRuleTokenVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.id.service.ISequenceService;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单据编号规则引擎。
 *
 * <p>使用 Interpreter Strategy 依次解释日期、序列、上下文和业务字段变量；
 * 预览和运行时生成共享同一解释链，避免两套规则产生漂移。</p>
 */
final class BusinessDocumentNoRuleEngine {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern LEGACY_TOKEN_PATTERN = Pattern.compile("(?<!\\$)\\{([^{}]+)}");
    private static final Pattern LEGACY_SEQ_TOKEN_PATTERN = Pattern.compile("seq(\\d+)");

    private final ISequenceService sequenceService;
    private final Clock clock;
    private final List<TokenInterpreter> interpreters;

    BusinessDocumentNoRuleEngine(ISequenceService sequenceService) {
        this(sequenceService, Clock.systemDefaultZone());
    }

    BusinessDocumentNoRuleEngine(ISequenceService sequenceService, Clock clock) {
        this.sequenceService = sequenceService;
        this.clock = clock;
        this.interpreters = List.of(
                this::interpretDateToken,
                this::interpretSequenceToken,
                this::interpretContextToken,
                this::interpretFieldToken
        );
    }

    List<BusinessDocumentNoRuleTokenVO> listTokens() {
        List<BusinessDocumentNoRuleTokenVO> tokens = new ArrayList<>();
        tokens.add(token("${yyyy}", "年份", "日期时间", "当前年份，四位数字", "2026", "2026"));
        tokens.add(token("${yyyyMM}", "年月", "日期时间", "当前年月，六位数字", "202606", "202606"));
        tokens.add(token("${yyyyMMdd}", "年月日", "日期时间", "当前日期，八位数字", "20260602", "20260602"));
        tokens.add(token("${HHmmss}", "时分秒", "日期时间", "当前时间，六位数字", "203405", "203405"));
        tokens.add(token("${seq}", "流水号", "序列", "预览使用样例序号，不占用真实序列", "1", "1"));
        tokens.add(token("${seq:4}", "四位流水号", "序列", "流水号左侧补零到指定长度", "0001", "0001"));
        tokens.add(token("${suiteCode}", "套件编码", "上下文", "当前业务套件编码", "CRM", "CRM"));
        tokens.add(token("${objectCode}", "对象编码", "上下文", "当前业务对象编码", "OPPORTUNITY", "OPPORTUNITY"));
        tokens.add(token("${starter}", "发起人", "上下文", "当前发起人用户名或用户编码", "zhangsan", "zhangsan"));
        tokens.add(token("${deptCode}", "部门编码", "上下文", "当前发起人部门编码", "SALES", "SALES"));
        tokens.add(token("${field:<fieldCode>}", "单据字段", "业务字段", "从样例数据读取业务字段值，例如 ${field:customerName}", "ACME", "ACME"));
        return tokens;
    }

    BusinessDocumentNoRulePreviewVO preview(BusinessDocumentNoRulePreviewDTO dto) {
        BusinessDocumentNoRulePreviewDTO source = dto == null ? new BusinessDocumentNoRulePreviewDTO() : dto;
        String template = StringUtils.defaultIfBlank(normalizeTemplate(source.getTemplate()), "DOC-${yyyyMMdd}-${seq:4}");
        return render(
                template,
                StringUtils.defaultIfBlank(source.getSuiteCode(), "SUITE"),
                StringUtils.defaultIfBlank(source.getObjectCode(), "OBJECT"),
                StringUtils.defaultIfBlank(source.getStarter(), "starter"),
                StringUtils.defaultIfBlank(source.getDeptCode(), "DEPT"),
                source.getSampleData(),
                source.getSequence() == null ? 1L : source.getSequence().longValue()
        );
    }

    String generate(AiBusinessDocumentConfig config, Map<String, Object> recordData) {
        if (config == null || !EnableStatus.ENABLED.matches(config.getDocumentEnabled())) {
            return null;
        }
        String template = normalizeTemplate(config.getDocumentNoRule());
        if (StringUtils.isBlank(template)) {
            return null;
        }
        long sequence = sequenceService.nextId(buildSequenceKey(config, template));
        LoginUser loginUser = safeLoginUser();
        String starter = loginUser == null
                ? "starter"
                : StringUtils.firstNonBlank(loginUser.getUsername(), loginUser.getRealName(),
                        loginUser.getUserId() == null ? null : String.valueOf(loginUser.getUserId()), "starter");
        String deptCode = loginUser == null
                ? "DEPT"
                : StringUtils.firstNonBlank(
                        loginUser.getMainOrgId() == null ? null : String.valueOf(loginUser.getMainOrgId()),
                        loginUser.getDeptName(),
                        "DEPT");
        BusinessDocumentNoRulePreviewVO rendered = render(
                template,
                StringUtils.defaultIfBlank(config.getSuiteCode(), "SUITE"),
                StringUtils.defaultIfBlank(config.getObjectCode(), "OBJECT"),
                starter,
                deptCode,
                recordData,
                sequence);
        throwIfInvalid(rendered);
        return rendered.getPreviewNo();
    }

    String normalizeTemplate(String template) {
        String value = StringUtils.trimToNull(template);
        if (value == null) {
            return null;
        }
        Matcher matcher = LEGACY_TOKEN_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("${" + normalizeToken(matcher.group(1)) + "}"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    void validateTemplate(String template) {
        BusinessDocumentNoRulePreviewVO preview = render(normalizeTemplate(template), "SUITE", "OBJECT", "starter", "DEPT",
                Map.of("fieldCode", "SAMPLE"), 1L);
        throwIfInvalid(preview);
    }

    private BusinessDocumentNoRulePreviewVO render(String template,
                                                   String suiteCode,
                                                   String objectCode,
                                                   String starter,
                                                   String deptCode,
                                                   Map<String, Object> sampleData,
                                                   Long sequence) {
        BusinessDocumentNoRulePreviewVO preview = new BusinessDocumentNoRulePreviewVO();
        preview.setTemplate(template);
        StringBuilder result = new StringBuilder();
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        int lastIndex = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        while (matcher.find()) {
            result.append(template, lastIndex, matcher.start());
            String token = matcher.group(1);
            preview.getUsedTokens().add("${" + token + "}");
            TokenContext context = new TokenContext(token, suiteCode, objectCode, starter, deptCode,
                    sampleData, sequence, now, preview);
            result.append(interpret(context));
            lastIndex = matcher.end();
        }
        result.append(template.substring(lastIndex));
        String previewNo = result.toString();
        preview.setPreviewNo(previewNo);
        if (!previewNo.matches("[A-Za-z0-9_\\-./]+")) {
            preview.getWarnings().add(issue(null, "编号包含空格或特殊字符，可能不适合作为对外单据号", "建议只使用字母、数字、短横线、下划线、点和斜线"));
        }
        if (previewNo.length() > 64) {
            preview.getWarnings().add(issue(null, "编号长度超过 64 个字符", "建议缩短固定前缀或字段变量内容"));
        }
        preview.setValid(preview.getErrors().isEmpty());
        return preview;
    }

    private String interpret(TokenContext context) {
        for (TokenInterpreter interpreter : interpreters) {
            String value = interpreter.interpret(context);
            if (value != null) {
                return value;
            }
        }
        context.preview().getErrors().add(issue("${" + context.token() + "}",
                "未知编号变量: ${" + context.token() + "}",
                "从内置变量列表选择，或使用 ${field:<fieldCode>} 引用业务字段"));
        return "";
    }

    private String interpretDateToken(TokenContext context) {
        return switch (context.token()) {
            case "yyyy" -> context.now().format(DateTimeFormatter.ofPattern("yyyy"));
            case "yyyyMM" -> context.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "yyyyMMdd" -> context.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "HHmmss" -> context.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            default -> null;
        };
    }

    private String interpretSequenceToken(TokenContext context) {
        if ("seq".equals(context.token())) {
            return String.valueOf(Math.max(context.sequence() == null ? 1L : context.sequence(), 1L));
        }
        if (!context.token().startsWith("seq:")) {
            return null;
        }
        String lengthText = context.token().substring("seq:".length()).trim();
        int length;
        try {
            length = Integer.parseInt(lengthText);
        } catch (Exception e) {
            context.preview().getErrors().add(issue("${" + context.token() + "}", "流水号长度必须是数字", "例如 ${seq:4}"));
            return "";
        }
        if (length <= 0 || length > 12) {
            context.preview().getErrors().add(issue("${" + context.token() + "}", "流水号长度建议在 1-12 之间", "例如 ${seq:4}"));
            return "";
        }
        String value = String.valueOf(Math.max(context.sequence() == null ? 1L : context.sequence(), 1L));
        return StringUtils.leftPad(value, length, '0');
    }

    private String interpretContextToken(TokenContext context) {
        return switch (context.token()) {
            case "suiteCode" -> context.suiteCode();
            case "objectCode" -> context.objectCode();
            case "starter" -> context.starter();
            case "deptCode" -> context.deptCode();
            default -> null;
        };
    }

    private String interpretFieldToken(TokenContext context) {
        if (!context.token().startsWith("field:")) {
            return null;
        }
        String fieldCode = context.token().substring("field:".length()).trim();
        if (StringUtils.isBlank(fieldCode)) {
            context.preview().getErrors().add(issue("${" + context.token() + "}", "字段变量缺少字段编码", "改为 ${field:字段编码}"));
            return "";
        }
        Object value = readSampleValue(context.sampleData(), fieldCode);
        if (value == null) {
            context.preview().getWarnings().add(issue("${" + context.token() + "}", "样例数据中没有字段 " + fieldCode,
                    "预览时可填入 sampleData，保存后运行态会读取真实字段"));
            return fieldCode;
        }
        return String.valueOf(value);
    }

    private String buildSequenceKey(AiBusinessDocumentConfig config, String template) {
        String tenantId = config.getTenantId() == null ? String.valueOf(resolveTenantId()) : String.valueOf(config.getTenantId());
        String suiteCode = StringUtils.defaultIfBlank(config.getSuiteCode(), "SUITE");
        String objectCode = StringUtils.defaultIfBlank(config.getObjectCode(), "OBJECT");
        String period = resolveSequencePeriod(template);
        String templateHash = Integer.toHexString(StringUtils.defaultString(template).hashCode());
        return "lowcode:document-no:" + tenantId + ":" + safeSequencePart(suiteCode) + ":"
                + safeSequencePart(objectCode) + ":" + period + ":" + templateHash;
    }

    private String resolveSequencePeriod(String template) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (StringUtils.contains(template, "yyyyMMdd")) {
            return now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        if (StringUtils.contains(template, "yyyyMM")) {
            return now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        }
        if (StringUtils.contains(template, "yyyy")) {
            return now.format(DateTimeFormatter.ofPattern("yyyy"));
        }
        return "all";
    }

    private String safeSequencePart(String value) {
        String result = StringUtils.defaultIfBlank(value, "NA").replaceAll("[^A-Za-z0-9_\\-]", "_");
        return StringUtils.defaultIfBlank(result, "NA");
    }

    private LoginUser safeLoginUser() {
        try {
            return SessionHelper.getLoginUser();
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveTenantId() {
        try {
            Long tenantId = SessionHelper.getTenantId();
            return tenantId != null ? tenantId : 1L;
        } catch (Exception e) {
            return 1L;
        }
    }

    private Object readSampleValue(Map<String, Object> sampleData, String fieldCode) {
        if (sampleData == null || StringUtils.isBlank(fieldCode)) {
            return null;
        }
        if (sampleData.containsKey(fieldCode)) {
            return sampleData.get(fieldCode);
        }
        String camel = snakeToCamel(fieldCode);
        if (sampleData.containsKey(camel)) {
            return sampleData.get(camel);
        }
        return sampleData.get(camelToSnake(fieldCode));
    }

    private BusinessDocumentNoRuleTokenVO token(String insertText, String label, String groupName,
                                                String description, String example, String sampleValue) {
        BusinessDocumentNoRuleTokenVO token = new BusinessDocumentNoRuleTokenVO();
        token.setToken(insertText);
        token.setInsertText(insertText);
        token.setLabel(label);
        token.setGroupName(groupName);
        token.setDescription(description);
        token.setExample(example);
        token.setSampleValue(sampleValue);
        return token;
    }

    private BusinessDocumentNoRulePreviewVO.PreviewIssueVO issue(String token, String message, String suggestion) {
        BusinessDocumentNoRulePreviewVO.PreviewIssueVO issue = new BusinessDocumentNoRulePreviewVO.PreviewIssueVO();
        issue.setToken(token);
        issue.setMessage(message);
        issue.setSuggestion(suggestion);
        return issue;
    }

    private void throwIfInvalid(BusinessDocumentNoRulePreviewVO preview) {
        if (preview.getErrors().isEmpty()) {
            return;
        }
        String message = preview.getErrors().stream()
                .map(BusinessDocumentNoRulePreviewVO.PreviewIssueVO::getMessage)
                .findFirst()
                .orElse("单据编号规则不正确");
        throw new BusinessException(message);
    }

    private String normalizeToken(String token) {
        String value = StringUtils.defaultString(token).trim();
        Matcher seqMatcher = LEGACY_SEQ_TOKEN_PATTERN.matcher(value);
        return seqMatcher.matches() ? "seq:" + seqMatcher.group(1) : value;
    }

    private String snakeToCamel(String value) {
        if (StringUtils.isBlank(value) || !value.contains("_")) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }

    private String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                result.append('_').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    @FunctionalInterface
    private interface TokenInterpreter {
        String interpret(TokenContext context);
    }

    private record TokenContext(String token,
                                String suiteCode,
                                String objectCode,
                                String starter,
                                String deptCode,
                                Map<String, Object> sampleData,
                                Long sequence,
                                LocalDateTime now,
                                BusinessDocumentNoRulePreviewVO preview) {
    }
}
