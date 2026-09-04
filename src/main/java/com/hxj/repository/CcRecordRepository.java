package com.hxj.repository;

import com.hxj.entity.CcRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CcRecordRepository extends JpaRepository<CcRecord, Long> {

    List<CcRecord> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
    boolean existsByDocumentIdAndTargetUserId(Long documentId, Long targetUserId);
}