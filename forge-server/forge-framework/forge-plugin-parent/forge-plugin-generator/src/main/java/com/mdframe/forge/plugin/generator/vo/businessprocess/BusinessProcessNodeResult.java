package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;

/**
 * 单节点执行结果。
 */
@Data
public class BusinessProcessNodeResult {

    public static final String COMPLETED = "COMPLETED";
    public static final String WAITING = "WAITING";
    public static final String FAILED = "FAILED";

    private String status;

    private String outputPort;

    private String correlationId;

    private String outputSummary;

    private String errorCode;

    private String errorSummary;

    public static BusinessProcessNodeResult completed(String outputPort, String outputSummary) {
        BusinessProcessNodeResult result = new BusinessProcessNodeResult();
        result.setStatus(COMPLETED);
        result.setOutputPort(outputPort);
        result.setOutputSummary(outputSummary);
        return result;
    }

    public static BusinessProcessNodeResult waiting(String correlationId, String outputSummary) {
        BusinessProcessNodeResult result = new BusinessProcessNodeResult();
        result.setStatus(WAITING);
        result.setCorrelationId(correlationId);
        result.setOutputSummary(outputSummary);
        return result;
    }

    public static BusinessProcessNodeResult failed(String errorCode, String errorSummary) {
        BusinessProcessNodeResult result = new BusinessProcessNodeResult();
        result.setStatus(FAILED);
        result.setErrorCode(errorCode);
        result.setErrorSummary(errorSummary);
        return result;
    }

    public boolean isWaiting() {
        return WAITING.equals(status);
    }

    public boolean isFailed() {
        return FAILED.equals(status);
    }
}
