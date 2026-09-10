package com.hxj.repository;

import com.hxj.enums.DocumentStatusEnum;
import com.hxj.entity.OaDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OaDocumentRepository extends JpaRepository<OaDocument, Long>, JpaSpecificationExecutor<OaDocument> {
    Optional<OaDocument> findByDocCode(String docCode);
    Optional<OaDocument> findByProcessInstanceId(String processInstanceId);
    long countByFlowConfigId(Long flowConfigId);
    List<OaDocument> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);
    List<OaDocument> findByStatus(DocumentStatusEnum status);
    List<OaDocument> findByRiskFlagTrue();
    List<OaDocument> findByStatusAndCurrentNode(DocumentStatusEnum status, String currentNode);
    List<OaDocument> findByStatusAndDocCodeContainingIgnoreCase(DocumentStatusEnum status, String docCode);
    List<OaDocument> findByStatusAndContractNoContainingIgnoreCase(DocumentStatusEnum status, String contractNo);
    List<OaDocument> findByStatusAndProjectNameContainingIgnoreCase(DocumentStatusEnum status, String projectName);
    List<OaDocument> findByStatusAndApplicant_NameContainingIgnoreCase(DocumentStatusEnum status, String applicantName);
    List<OaDocument> findByStatusAndUpdatedAtBetween(DocumentStatusEnum status,
                                                     java.time.LocalDateTime start,
                                                     java.time.LocalDateTime end);
    List<OaDocument> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    long countByStatus(DocumentStatusEnum status);
}
