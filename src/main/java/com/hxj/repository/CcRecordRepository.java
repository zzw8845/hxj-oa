package com.hxj.repository;

import com.hxj.entity.CcRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CcRecordRepository extends JpaRepository<CcRecord, Long> {

    List<CcRecord> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
    boolean existsByDocumentIdAndTargetUserId(Long documentId, Long targetUserId);

    boolean existsByTargetRoleId(Long targetRoleId);

    /** 被抄送的单据 ID 集合（访问策略"流程参与人"豁免的数据源）。 */
    @Query("select c.document.id from CcRecord c where c.targetUser.id = ?1")
    List<Long> findDocumentIdsByTargetUserId(Long userId);
}