package com.hxj.document;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 表单模板：管理端 CRUD + 提交渲染读取。 */
@Tag(name = "表单模板")
@RestController
public class FormTemplateController {

    private final FormTemplateManagementService templateService;

    public FormTemplateController(FormTemplateManagementService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "启用模板列表", description = "登录即可读取：提交表单按业务类型渲染字段")
    @GetMapping("/api/form-templates/enabled")
    public ApiResponse<List<FormTemplateManagementService.TemplateView>> enabled() {
        return ApiResponse.success(templateService.listEnabled());
    }

    @Operation(summary = "模板详情（含字段清单）", description = "登录即可读取：驳回重提/详情渲染用")
    @GetMapping("/api/form-templates/{id}")
    public ApiResponse<FormTemplateManagementService.TemplateView> get(@PathVariable Long id) {
        return ApiResponse.success(templateService.get(id));
    }

    @Operation(summary = "模板列表（管理）")
    @GetMapping("/api/admin/form-templates")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<List<FormTemplateManagementService.TemplateView>> list() {
        return ApiResponse.success(templateService.list());
    }

    @Operation(summary = "模板详情（管理）", description = "编辑回填用，含字段清单")
    @GetMapping("/api/admin/form-templates/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> getAdmin(@PathVariable Long id) {
        return ApiResponse.success(templateService.get(id));
    }

    @Operation(summary = "创建模板", description = "字段清单整体提交；保留键（提升字段）由系统校验")
    @PostMapping("/api/admin/form-templates")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> create(
            @RequestBody SaveFormTemplateRequest request) {
        return ApiResponse.success(templateService.create(request));
    }

    @Operation(summary = "更新模板", description = "字段清单整体替换，版本号 +1；提升字段不可删除")
    @PutMapping("/api/admin/form-templates/{id}")
    @PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
    public ApiResponse<FormTemplateManagementService.TemplateView> update(
            @PathVariable Long id, @RequestBody SaveFormTemplateRequest request) {
        return ApiResponse.success(templateService.update(id, request));
    }
}
