package com.mdframe.forge.plugin.ai.skill.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.skill.domain.AiAgentSkill;
import com.mdframe.forge.plugin.ai.skill.domain.AiSkill;
import com.mdframe.forge.plugin.ai.skill.domain.AiSkillFile;
import com.mdframe.forge.plugin.ai.skill.dto.AiSkillBindDTO;
import com.mdframe.forge.plugin.ai.skill.service.AiSkillService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 技能包管理控制器
 */
@RestController
@RequestMapping("/ai/skill")
@RequiredArgsConstructor
public class AiSkillController {

    private final AiSkillService skillService;

    @GetMapping("/page")
    @SaCheckPermission("ai:skill:list")
    public RespInfo<Page<AiSkill>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return RespInfo.success(skillService.selectSkillPage(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:skill:list")
    public RespInfo<AiSkill> getById(@PathVariable Long id) {
        return RespInfo.success(skillService.getById(id));
    }

    @GetMapping("/{id}/files")
    @SaCheckPermission("ai:skill:list")
    public RespInfo<List<AiSkillFile>> getSkillFiles(@PathVariable Long id) {
        return RespInfo.success(skillService.getSkillFiles(id));
    }

    @GetMapping("/agent/{agentId}")
    @SaCheckPermission("ai:skill:list")
    public RespInfo<List<AiAgentSkill>> getAgentSkills(@PathVariable Long agentId) {
        return RespInfo.success(skillService.getAgentSkills(agentId));
    }

    @PostMapping("/agent/{agentId}")
    @SaCheckPermission("ai:skill:add")
    public RespInfo<Void> bindAgentSkill(@PathVariable Long agentId, @RequestBody AiSkillBindDTO body) {
        skillService.bindAgentSkill(agentId, body.getSkillId());
        return RespInfo.success();
    }

    @DeleteMapping("/agent/{agentId}/{skillId}")
    @SaCheckPermission("ai:skill:delete")
    public RespInfo<Void> unbindAgentSkill(@PathVariable Long agentId, @PathVariable Long skillId) {
        skillService.unbindAgentSkill(agentId, skillId);
        return RespInfo.success();
    }

    @PostMapping
    @SaCheckPermission("ai:skill:add")
    public RespInfo<Void> create(@RequestBody AiSkill skill) {
        skillService.createSkill(skill);
        return RespInfo.success();
    }

    @PutMapping
    @SaCheckPermission("ai:skill:edit")
    public RespInfo<Void> update(@RequestBody AiSkill skill) {
        skillService.updateSkill(skill);
        return RespInfo.success();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:skill:delete")
    public RespInfo<Void> delete(@PathVariable Long id) {
        skillService.deleteSkill(id);
        return RespInfo.success();
    }

    /**
     * 上传 ZIP 技能包
     */
    @PostMapping("/upload-zip")
    @SaCheckPermission("ai:skill:add")
    public RespInfo<AiSkill> uploadZip(@RequestParam("file") MultipartFile file) {
        return RespInfo.success(skillService.uploadZip(file));
    }

    /**
     * AI 生成 SKILL.md
     */
    @PostMapping("/ai-generate")
    @SaCheckPermission("ai:skill:ai-generate")
    public RespInfo<String> aiGenerate(@RequestParam String description) {
        return RespInfo.success(skillService.aiGenerate(description));
    }

    /**
     * AI 优化现有 SKILL.md
     */
    @PostMapping("/{id}/ai-optimize")
    @SaCheckPermission("ai:skill:ai-generate")
    public RespInfo<String> aiOptimize(@PathVariable Long id,
                                        @RequestParam String instruction) {
        return RespInfo.success(skillService.aiOptimize(id, instruction));
    }
}
