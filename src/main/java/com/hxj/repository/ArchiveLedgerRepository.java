package com.hxj.repository;

import com.hxj.entity.ArchiveLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ArchiveLedgerRepository extends
        JpaRepository<ArchiveLedger, Long>, JpaSpecificationExecutor<ArchiveLedger> {

    Optional<ArchiveLedger> findByDocumentId(Long documentId);

    Optional<ArchiveLedger> findByDocCode(String docCode);
}