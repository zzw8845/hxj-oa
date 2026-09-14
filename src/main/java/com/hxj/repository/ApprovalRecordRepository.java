package com.hxj.repository;

import com.hxj.enums.ApprovalActionEnum;
import com.hxj.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByDocumentIdOrderByCreatedAtAsc(Long documentId);

    List<ApprovalRecord> findByDocumentIdAndResolvedFalseOrderByIdAsc(Long documentId);

    boolean existsByDocumentIdAndResolvedFalse(Long documentId);

    boolean existsByDocumentIdAndApproverId(Long documentId, Long approverId);

    List<ApprovalRecord> findByAction(ApprovalActionEnum action);

    /** 用户历史审批过的单据 ID 集合（访问策略"流程参与人"豁免的数据源）。 */
    @Query("select r.document.id from ApprovalRecord r where r.approver.id = ?1")
    List<Long> findDocumentIdsByApproverId(Long approverId);
}
