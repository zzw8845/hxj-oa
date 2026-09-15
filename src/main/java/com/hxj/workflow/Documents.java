package com.hxj.workflow;

import com.hxj.entity.OaDocument;
import com.hxj.repository.OaDocumentRepository;
import org.flowable.engine.delegate.DelegateExecution;

/** 流程委托（JavaDelegate）访问单据的公共入口：统一 documentId 变量取值与加载，避免各处重复。 */
final class Documents {

    /** 流程变量名：业务单据 ID（启动流程时写入）。 */
    static final String DOCUMENT_ID_VARIABLE = "documentId";

    private Documents() {
    }

    /** 由执行上下文加载当前单据；无 documentId 或单据不存在返回 null（调用方直接返回，不阻断流程）。 */
    static OaDocument of(DelegateExecution execution, OaDocumentRepository documentRepository) {
        Object documentId = execution.getVariable(DOCUMENT_ID_VARIABLE);
        if (documentId == null) {
            return null;
        }
        return documentRepository.findById(((Number) documentId).longValue()).orElse(null);
    }
}
