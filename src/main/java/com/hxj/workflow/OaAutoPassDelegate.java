package com.hxj.workflow;

import com.hxj.entity.ApprovalRecord;
import com.hxj.entity.OaDocument;
import com.hxj.enums.ApprovalActionEnum;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 节点自动通过处理器（钉钉"审批类型 = 自动通过"）：
 * 写自动通过留痕后流程继续流转，不经人工、不生成待办。
 *
 * <p>与"审批人去重自动通过"（{@code ApprovalActionService.autoPassRedundant}）的区别：
 * 此处是<b>节点配置</b>的静态自动通过（通常配合条件分支实现全自动流转），
 * 后者是运行时按重复审批人规则动态跳过。
 */
@Component("oaAutoPassDelegate")
public class OaAutoPassDelegate implements JavaDelegate {

    private final OaDocumentRepository documentRepository;
    private final ApprovalRecordRepository approvalRepository;

    public OaAutoPassDelegate(OaDocumentRepository documentRepository,
                              ApprovalRecordRepository approvalRepository) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
    }

    @Override
    public void execute(DelegateExecution execution) {
        OaDocument document = Documents.of(execution, documentRepository);
        if (document == null) {
            return;
        }
        String nodeName = execution.getCurrentFlowElement() == null
                ? null : execution.getCurrentFlowElement().getName();
        ApprovalRecord record = new ApprovalRecord();
        record.setDocument(document);
        record.setNodeName(nodeName);
        // 自动处理留痕挂申请人：approver 为业务必填，系统节点以发起人身份留痕并注明来源
        record.setApprover(document.getApplicant());
        record.setAction(ApprovalActionEnum.AUTO_PASS);
        record.setComment("节点审批类型为自动通过，系统按配置直接流转");
        approvalRepository.save(record);
    }
}
