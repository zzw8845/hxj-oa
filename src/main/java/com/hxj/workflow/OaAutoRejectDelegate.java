package com.hxj.workflow;

import com.hxj.entity.ApprovalRecord;
import com.hxj.entity.OaDocument;
import com.hxj.enums.ApprovalActionEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 节点自动拒绝处理器（钉钉空策略"自动拒绝" + 审批类型"自动拒绝"）：
 * 写自动拒绝留痕、单据置为已驳回，流程沿编译期连好的边走向结束。
 *
 * <p>触发路径有两条：
 * <ol>
 *   <li>节点入口排他网关判定审批人解析为空且空策略为 AUTO_REJECT；</li>
 *   <li>节点审批类型直接配置为 AUTO_REJECT（静态自动拒绝）。</li>
 * </ol>
 */
@Component("oaAutoRejectDelegate")
public class OaAutoRejectDelegate implements JavaDelegate {

    private final OaDocumentRepository documentRepository;
    private final ApprovalRecordRepository approvalRepository;

    public OaAutoRejectDelegate(OaDocumentRepository documentRepository,
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
        record.setApprover(document.getApplicant());
        record.setAction(ApprovalActionEnum.AUTO_REJECT);
        record.setComment("审批人解析为空（或节点配置为自动拒绝），系统按策略自动拒绝");
        approvalRepository.save(record);

        document.setStatus(DocumentStatusEnum.REJECTED);
        document.setCurrentNode(nodeName);
        documentRepository.save(document);
    }
}
