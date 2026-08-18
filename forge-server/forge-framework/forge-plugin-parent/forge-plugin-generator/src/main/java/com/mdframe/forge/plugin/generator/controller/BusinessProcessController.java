package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessSchemaDTO;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessPublishService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessSnapshot;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessFlowModelVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessValidationVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用级业务流程定义控制面接口。
 */
@RestController
@RequestMapping("/ai/business/process")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class BusinessProcessController {

    private final BusinessProcessService businessProcessService;
    private final BusinessProcessPublishService businessProcessPublishService;

    @GetMapping("/page")
    @SaCheckPermission("ai:businessProcess:list")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "分页查询应用级业务流程")
    public RespInfo<Page<BusinessProcessVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam String applicationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String designStatus) {
        return RespInfo.success(businessProcessService.page(
                pageNum, pageSize, applicationId, keyword, status, designStatus));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:businessProcess:list")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "查询应用级业务流程详情")
    public RespInfo<BusinessProcessVO> detail(@PathVariable Long id) {
        return RespInfo.success(businessProcessService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("ai:businessProcess:add")
    @OperationLog(module = "业务流程", type = OperationType.ADD, desc = "新增应用级业务流程")
    public RespInfo<BusinessProcessVO> create(@Valid @RequestBody BusinessProcessDTO dto) {
        return RespInfo.success(businessProcessService.create(dto));
    }

    @PostMapping("/{id}/copy")
    @SaCheckPermission("ai:businessProcess:copy")
    @OperationLog(module = "业务流程", type = OperationType.ADD, desc = "复制应用级业务流程草稿")
    public RespInfo<BusinessProcessVO> copy(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BusinessProcessDTO dto) {
        return RespInfo.success(businessProcessService.copy(id, dto));
    }

    @PutMapping
    @SaCheckPermission("ai:businessProcess:edit")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "修改业务流程基础信息")
    public RespInfo<BusinessProcessVO> update(@Valid @RequestBody BusinessProcessDTO dto) {
        return RespInfo.success(businessProcessService.update(dto));
    }

    @GetMapping("/{id}/designer")
    @SaCheckPermission("ai:businessProcess:list")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "查询业务流程设计草稿")
    public RespInfo<BusinessProcessVO> designer(@PathVariable Long id) {
        return RespInfo.success(businessProcessService.getDesigner(id));
    }

    @GetMapping("/{id}/flow-models")
    @SaCheckPermission("ai:businessProcess:list")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "查询当前流程可引用审批模型")
    public RespInfo<List<BusinessProcessFlowModelVO>> availableFlowModels(@PathVariable Long id) {
        return RespInfo.success(businessProcessService.availableFlowModels(id));
    }

    @PutMapping("/{id}/schema")
    @SaCheckPermission("ai:businessProcess:edit")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "保存业务流程设计草稿")
    public RespInfo<BusinessProcessVO> saveSchema(
            @PathVariable Long id,
            @Valid @RequestBody BusinessProcessSchemaDTO dto) {
        return RespInfo.success(businessProcessService.saveSchema(id, dto));
    }

    @PostMapping("/{id}/validate")
    @SaCheckPermission("ai:businessProcess:validate")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "校验业务流程设计草稿")
    public RespInfo<BusinessProcessValidationVO> validate(@PathVariable Long id) {
        return RespInfo.success(businessProcessService.validate(id));
    }

    @PostMapping("/{id}/publish")
    @SaCheckPermission("ai:businessProcess:publish")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "独立发布业务流程版本")
    public RespInfo<BusinessProcessSnapshot> publish(@PathVariable Long id) {
        return RespInfo.success(businessProcessPublishService.publishStandalone(id));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("ai:businessProcess:status")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "启停应用级业务流程")
    public RespInfo<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        businessProcessService.updateStatus(id, status);
        return RespInfo.success();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:businessProcess:delete")
    @OperationLog(module = "业务流程", type = OperationType.DELETE, desc = "逻辑删除应用级业务流程")
    public RespInfo<Void> delete(@PathVariable Long id) {
        businessProcessService.logicalDelete(id);
        return RespInfo.success();
    }
}
