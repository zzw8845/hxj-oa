package com.hxj.document;

import com.hxj.common.ApiResponse;
import com.hxj.common.Idempotent;
import com.hxj.common.PageResponse;
import com.hxj.enums.BusinessTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

/** 单据管理接口：提交、查询、详情、关联、快捷目录、再次提交、附件。 */
@Tag(name = "单据管理", description = "单据提交、查询、详情、前置关联、快捷目录、再次提交、附件上传下载")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentApplicationService documentService;

    public DocumentController(DocumentApplicationService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "提交单据", description = "创建并提交单据，自动生成编号、初始化状态、启动流程实例")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUBMIT_ALL_FORMS','VIEW_OWN_FORMS')")
    @Idempotent
    public ApiResponse<DocumentSummaryResponse> submit(
            @Valid @RequestBody SubmitDocumentRequest request) {
        return ApiResponse.success(documentService.submit(request));
    }


    @Operation(summary = "分页查询单据列表", description = "按关键字、状态、业务类型、单据类型、申请人组合筛选，按数据范围过滤")
    @GetMapping("/page")
    public ApiResponse<PageResponse<DocumentSummaryResponse>> searchPaged(
            @Valid DocumentPageRequest request) {
        return ApiResponse.success(documentService.searchPaged(request));
    }

    @Operation(summary = "单据详情", description = "含流程节点、附件、审批记录、前置关联、抄送记录")
    @GetMapping("/{id}")
    public ApiResponse<DocumentDetailResponse> detail(
            @PathVariable Long id) {
        return ApiResponse.success(documentService.detail(id));
    }

    @Operation(summary = "可驳回层级候选", description = "按单据实际流程给出可驳回目标：提交人 + 当前节点之前已流转的审批节点")
    @GetMapping("/{id}/reject-targets")
    public ApiResponse<List<String>> rejectTargets(@PathVariable Long id) {
        return ApiResponse.success(documentService.rejectTargets(id));
    }

    @Operation(summary = "前置单据关联候选", description = "报销按单据编号、业务付款按合同编号查询已审批单据")
    @GetMapping("/link-candidates")
    public ApiResponse<List<DocumentSummaryResponse>> linkCandidates(
            @Parameter(description = "查询关键字：报销按单据编号、业务付款按合同编号") @RequestParam String query,
            @Parameter(description = "是否按合同编号匹配：true 按合同编号，false 按单据编号") @RequestParam(defaultValue = "false") boolean byContract) {
        return ApiResponse.success(documentService.findLinkCandidates(query, byContract));
    }

    @Operation(summary = "快捷单据目录", description = "按业务类型返回可快捷提交的单据列表")
    @GetMapping("/quick")
    public ApiResponse<List<QuickDocumentItemResponse>> quickDocuments() {
         return ApiResponse.success(documentService.quickDocuments());
    }

    @Operation(summary = "再次提交", description = "复制历史单据信息生成新编号再次提交")
    @PostMapping("/{id}/repeat")
    @Idempotent
    public ApiResponse<DocumentSummaryResponse> repeat(
            @PathVariable Long id) {
        return ApiResponse.success(documentService.repeat(id));
    }

    @Operation(summary = "上传附件", description = "上传单据附件并记录元数据")
    @PostMapping(path = "/{id}/attachments", consumes = "multipart/form-data")
    public ApiResponse<DocumentDetailResponse.Attachment> upload(
            @PathVariable Long id,
            @Parameter(description = "上传时所处的审批节点名称（可选）") @RequestParam(required = false) String nodeName,
            @Parameter(description = "附件文件") @RequestParam MultipartFile file) {
        return ApiResponse.success(documentService.upload(id, nodeName, file));
    }

    @Operation(summary = "下载附件", description = "下载单据附件文件")
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @PathVariable Long attachmentId,
            @Parameter(description = "是否内联预览：true 时浏览器内打开，false 时下载") @RequestParam(defaultValue = "false") boolean preview)
            throws IOException {
        DocumentApplicationService.AttachmentDownload download =
                documentService.download(attachmentId);
        byte[] content = download.resource().getInputStream().readAllBytes();
        String disposition = (preview ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encodeFileName(download.fileName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(content);
    }

    /** 按 RFC 5987 对下载文件名做百分号编码（空格转 %20，避免浏览器截断）。 */
    private static String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Operation(summary = "必传附件清单", description = "按表单模板返回必传附件清单")
    @GetMapping("/attachment-requirements")
    public ApiResponse<List<String>> attachmentRequirements(
            @Parameter(description = "表单模板 ID") @RequestParam Long templateId) {
        return ApiResponse.success(documentService.attachmentRequirements(templateId));
    }
}
