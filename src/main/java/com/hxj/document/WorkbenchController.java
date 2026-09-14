package com.hxj.document;

import com.hxj.common.ApiResponse;
import com.hxj.repository.FormTemplateRepository;
import com.hxj.repository.WorkbenchEntryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台事项快捷入口查询：事项（员工视角的申请事由）到承接表单模板的映射清单。
 *
 * <p>事项→模板映射是业务配置数据，由后端管理（种子见 V1 基线 workbench_entry 表），
 * 前端只消费渲染——事项的增删改不依赖前端发版。
 */
@Tag(name = "工作台", description = "工作台事项快捷入口")
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    /** 事项入口视图：承接模板的 id 与名称一并下发，前端无需再按名称反查。 */
    public record EntryView(String zone, String label, String hint, Long templateId, String templateName) {}

    private final WorkbenchEntryRepository entryRepository;
    private final FormTemplateRepository templateRepository;

    public WorkbenchController(WorkbenchEntryRepository entryRepository,
                               FormTemplateRepository templateRepository) {
        this.entryRepository = entryRepository;
        this.templateRepository = templateRepository;
    }

    @Operation(summary = "工作台事项入口清单", description = "按 DAILY/BUSINESS/SEAL 分区次序返回全部事项及其承接模板")
    @GetMapping("/entries")
    @Transactional(readOnly = true)
    public ApiResponse<List<EntryView>> entries() {
        Map<Long, com.hxj.entity.FormTemplate> templates = templateRepository
                .findAllById(entryRepository.findAllGroupedByZone().stream()
                        .map(com.hxj.entity.WorkbenchEntry::getTemplateId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(com.hxj.entity.FormTemplate::getId, Function.identity()));
        return ApiResponse.success(entryRepository.findAllGroupedByZone().stream()
                .map(entry -> {
                    com.hxj.entity.FormTemplate tpl = templates.get(entry.getTemplateId());
                    // 自动事项的文案实时跟随模板名（模板改名入口即变，无需二次维护）
                    boolean auto = Boolean.TRUE.equals(entry.getAutoCreated());
                    return new EntryView(
                            entry.getZone(),
                            auto && tpl != null ? tpl.getName() : entry.getLabel(),
                            entry.getHint(),
                            entry.getTemplateId(),
                            tpl == null ? null : tpl.getName());
                })
                .toList());
    }
}
