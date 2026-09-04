package com.hxj.repository;

import com.hxj.entity.DocumentStatus;
import com.hxj.entity.OaDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OaDocumentRepository extends JpaRepository<OaDocument, Long>, JpaSpecificationExecutor<OaDocument> {
    Optional<OaDocument> findByDocCode(String docCode);
    Optional<OaDocument> findByProcessInstanceId(String processInstanceId);
    List<OaDocument> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);
    List<OaDocument> findByStatus(DocumentStatus status);
    List<OaDocument> findByRiskFlagTrue();
    List<OaDocument> findByStatusAndCurrentNode(DocumentStatus status, String currentNode);
    List<OaDocument> findByStatusAndDocCodeContainingIgnoreCase(DocumentStatus status, String docCode);
    List<OaDocument> findByStatusAndContractNoContainingIgnoreCase(DocumentStatus status, String contractNo);
    List<OaDocument> findByStatusAndProjectNameContainingIgnoreCase(DocumentStatus status, String projectName);
    List<OaDocument> findByStatusAndApplicant_NameContainingIgnoreCase(DocumentStatus status, String applicantName);
    List<OaDocument> findByStatusAndUpdatedAtBetween(DocumentStatus status,
                                                     java.time.LocalDateTime start,
                                                     java.time.LocalDateTime end);
    List<OaDocument> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    long countByStatus(DocumentStatus status);
}
