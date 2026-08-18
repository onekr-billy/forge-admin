package com.mdframe.forge.plugin.generator.vo.businessapp;

import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 应用方案初始化结果。 */
@Data
public class BusinessApplicationAiInitializeResultVO {

    private Long applicationId;

    private Long primaryObjectId;

    private String primaryObjectCode;

    private List<BusinessApplicationObjectVO> objects = new ArrayList<>();

    private List<BusinessProcessVO> processes = new ArrayList<>();
}
