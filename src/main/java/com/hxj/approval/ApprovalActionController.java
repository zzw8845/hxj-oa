package com.hxj.approval;

import com.hxj.common.ApiResponse;
import com.hxj.common.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "审批操作", description = "单据审批：通过、驳回、通过但补材料、加签、补充材料回传、用印回传、历史查询")
@RestController
@RequestMapping("/api/documents/{documentId}/actions")
public class ApprovalActionController {

    private final ApprovalActionService service;

    public ApprovalActionController(ApprovalActionService service) {
        this.service = service;
    }

    @Operation(summary = "通过审批", description = "审批人通过当前节点，凭证必传；末节点通过后自动完结归档")
    @PostMapping("/approve")
    @Idempotent
    public ApiResponse<ApprovalResult> approve(@PathVariable Long documentId, @RequestBody ApprovalRequest request) {
        return ApiResponse.success(service.approve(documentId, request));
    }

    @Operation(summary = "指定层级驳回", description = "驳回至指定层级，原因必填，可设置需补充材料")
    @PostMapping("/reject")
    @Idempotent
    public ApiResponse<ApprovalResult> reject(@PathVariable Long documentId, @RequestBody ApprovalRequest request) {
        return ApiResponse.success(service.reject(documentId, request));
    }

    @Operation(summary = "通过但补材料", description = "通过当前节点但要求补充材料（付款前/付款后模式）")
    @PostMapping("/supplement")
    @Idempotent
    public ApiResponse<ApprovalResult> supplement(@PathVariable Long documentId, @RequestBody ApprovalRequest request) {
        return ApiResponse.success(service.supplement(documentId, request));
    }

    @Operation(summary = "加签", description = "添加加签人员，创建加签任务")
    @PostMapping("/sign")
    @Idempotent
    public ApiResponse<ApprovalResult> sign(@PathVariable Long documentId, @RequestBody SignRequest request) {
        return ApiResponse.success(service.sign(documentId, request));
    }

    @Operation(summary = "加签意见", description = "加签人发表意见后归还任务")
    @PostMapping("/sign-comment")
    @Idempotent
    public ApiResponse<ApprovalResult> signComment(@PathVariable Long documentId, @RequestBody String comment) {
        return ApiResponse.success(service.signComment(documentId, comment));
    }


    @Operation(summary = "补充材料回传", description = "回传需补充的材料文件")
    @PostMapping("/supplement-materials")
    @Idempotent
    public ApiResponse<ApprovalResult> submitSupplementMaterials(@PathVariable Long documentId,
                                                     @Parameter(description = "需补充的材料文件列表") @RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files) {
        return ApiResponse.success(service.submitSupplementMaterials(documentId, files));
    }

    @Operation(summary = "用印盖章文件回传", description = "回传盖章文件后流程方可完结")
    @PostMapping("/stamped-file")
    @Idempotent
    public ApiResponse<ApprovalResult> returnStampedFile(@PathVariable Long documentId,
                                            @Parameter(description = "盖章后的文件") @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.success(service.returnStampedFile(documentId, file));
    }

    @Operation(summary = "流程历史查询", description = "查询单据审批历史（Flowable 历史任务与业务审批记录合并）")
    @GetMapping("/history")
    public ApiResponse<List<ApprovalHistoryItem>> history(@PathVariable Long documentId) {
        return ApiResponse.success(service.history(documentId));
    }
}
