package com.hxj.repository;

import com.hxj.enums.ApprovalActionEnum;
import com.hxj.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByDocumentIdOrderByCreatedAtAsc(Long documentId);

    List<ApprovalRecord> findByDocumentIdAndResolvedFalseOrderByIdAsc(Long documentId);

    boolean existsByDocumentIdAndResolvedFalse(Long documentId);

    boolean existsByDocumentIdAndApproverId(Long documentId, Long approverId);

    List<ApprovalRecord> findByAction(ApprovalActionEnum action);
}
