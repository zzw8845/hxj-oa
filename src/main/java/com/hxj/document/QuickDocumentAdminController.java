package com.hxj.document;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 快捷单据目录管理接口（仅管理员）：业务名目的增删改不再依赖数据库预置。 */
@Tag(name = "快捷单据目录管理", description = "快捷单据目录增删改查（需 CONFIGURE_FLOW_PERMISSION 权限）")
@RestController
@RequestMapping("/api/admin/quick-documents")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class QuickDocumentAdminController {

    private final QuickDocumentManagementService quickDocumentService;

    public QuickDocumentAdminController(QuickDocumentManagementService quickDocumentService) {
        this.quickDocumentService = quickDocumentService;
    }

    @Operation(summary = "目录列表", description = "查询全部快捷单据条目（按业务类型、排序号升序）")
    @GetMapping
    public ApiResponse<List<QuickDocumentViews.Entry>> list() {
        return ApiResponse.success(quickDocumentService.list());
    }

    @Operation(summary = "新增条目", description = "新增快捷单据条目，同业务类型下名称唯一")
    @PostMapping
    public ApiResponse<QuickDocumentViews.Entry> create(@Valid @RequestBody SaveQuickDocumentRequest request) {
        return ApiResponse.success(quickDocumentService.create(request));
    }

    @Operation(summary = "编辑条目", description = "修改名称/类目/排序号")
    @PutMapping("/{id}")
    public ApiResponse<QuickDocumentViews.Entry> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveQuickDocumentRequest request) {
        return ApiResponse.success(quickDocumentService.update(id, request));
    }

    @Operation(summary = "删除条目", description = "删除快捷单据条目")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        quickDocumentService.delete(id);
        return ApiResponse.success(null);
    }
}
