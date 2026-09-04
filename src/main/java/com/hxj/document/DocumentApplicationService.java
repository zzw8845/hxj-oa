package com.hxj.document;

import com.hxj.common.ErrorCode;
import com.hxj.entity.ApprovalRecord;
import com.hxj.entity.BusinessType;
import com.hxj.entity.CcRecord;
import com.hxj.entity.CcSource;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.OaAttachment;
import com.hxj.entity.OaDocument;
import com.hxj.common.PageResponse;
import com.hxj.entity.SysUser;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.QuickDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUser;
import com.hxj.security.CurrentUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.service.DocumentCodeGenerator;
import com.hxj.workflow.WorkflowPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentApplicationService {

    private final OaDocumentRepository documentRepository;
    private final OaAttachmentRepository attachmentRepository;
    private final ApprovalRecordRepository approvalRepository;
    private final CcRecordRepository ccRepository;
    private final QuickDocumentRepository quickRepository;
    private final SysUserRepository userRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final DocumentClassifier classifier;
    private final DocumentCodeGenerator codeGenerator;
    private final WorkflowPort workflowPort;
    private final DocumentAccessPolicy accessPolicy;
    private final AttachmentRequirementService requirementService;
    private final LocalAttachmentStorage attachmentStorage;
    private final BigDecimal riskThreshold;

    public DocumentApplicationService(
            OaDocumentRepository documentRepository,
            OaAttachmentRepository attachmentRepository,
            ApprovalRecordRepository approvalRepository,
            CcRecordRepository ccRepository,
            QuickDocumentRepository quickRepository,
            SysUserRepository userRepository,
            FlowConfigRepository flowConfigRepository,
            DocumentClassifier classifier,
            DocumentCodeGenerator codeGenerator,
            WorkflowPort workflowPort,
            DocumentAccessPolicy accessPolicy,
            AttachmentRequirementService requirementService,
            LocalAttachmentStorage attachmentStorage,
            @Value("${app.risk-threshold:80000}") BigDecimal riskThreshold) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.approvalRepository = approvalRepository;
        this.ccRepository = ccRepository;
        this.quickRepository = quickRepository;
        this.userRepository = userRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.classifier = classifier;
        this.codeGenerator = codeGenerator;
        this.workflowPort = workflowPort;
        this.accessPolicy = accessPolicy;
        this.requirementService = requirementService;
        this.attachmentStorage = attachmentStorage;
        this.riskThreshold = riskThreshold;
    }

    @Transactional
    public DocumentSummary submit(SubmitDocumentRequest request) {
        AuthenticatedUser currentUser = CurrentUser.require();
        SysUser applicant = currentUserEntity(currentUser);
        BusinessType businessType = classifier.classify(request.projectName(), request.businessType());
        validate(request, businessType);
        FlowConfig flowConfig = flowConfigRepository.findByType(request.projectName())
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_CONFIG_NOT_FOUND, "未配置对应审批流程"));

        OaDocument document = new OaDocument();
        document.setDocCode(codeGenerator.generate(businessType));
        applyRequest(document, request, businessType);
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setStatus(DocumentStatus.PENDING);
        document.setCurrentNode(flowConfig.firstActionNode() == null
                ? null : flowConfig.firstActionNode().getName());
        document.setRiskFlag(document.getAmount() != null
                && document.getAmount().compareTo(riskThreshold) >= 0);
        if (request.linkedDocumentId() != null) {
            document.setLinkedDocument(approvedDocument(request.linkedDocumentId()));
        }
        documentRepository.saveAndFlush(document);
        createSelfSelectedCc(document, request.ccUserIds());

        String processInstanceId = workflowPort.startProcess(
                flowConfig.getId(), document.getId(), workflowVariables(document, request));
        document.setProcessInstanceId(processInstanceId);
        document.setFlowConfigId(flowConfig.getId());
        return toSummary(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> search(DocumentSearchCriteria criteria) {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and(criteriaSpecification(criteria));
        return documentRepository.findAll(spec).stream().map(this::toSummary).toList();
    }

    /** 分页版单据查询：分页参数见 {@link DocumentPageRequest}，按更新时间倒序。 */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummary> searchPaged(DocumentPageRequest request) {
        AuthenticatedUser currentUser = CurrentUser.require();
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and(criteriaSpecification(request.toCriteria()));
        return PageResponse.of(documentRepository
                .findAll(spec, request.toPageable(Sort.by(Sort.Direction.DESC, "updatedAt"))).map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public DocumentDetail detail(Long documentId) {
        AuthenticatedUser currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        List<ApprovalRecord> approvals = approvalRepository
                .findByDocumentIdOrderByCreatedAtAsc(documentId);
        List<CcRecord> ccRecords = ccRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        return new DocumentDetail(
                document.getId(), document.getDocCode(), document.getProjectName(),
                document.getBusinessType(), document.getDocumentType(), document.getApplicantName(),
                document.getDepartment(), document.getAmount(), document.getStatus(),
                document.getCurrentNode(), document.getProcessInstanceId(), linked(document),
                document.getAttachments().stream().map(this::attachmentItem).toList(),
                approvals.stream().map(this::approvalItem).toList(),
                ccRecords.stream().map(this::ccItem).toList(),
                document.getProcessInstanceId() == null ? List.of()
                        : workflowPort.history(document.getProcessInstanceId()));
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> findLinkCandidates(String query, boolean byContract) {
        String value = query == null ? "" : query;
        Set<OaDocument> documents = new LinkedHashSet<>();
        if (byContract) {
            documents.addAll(documentRepository.findByStatusAndContractNoContainingIgnoreCase(
                    DocumentStatus.APPROVED, value));
        } else {
            documents.addAll(documentRepository.findByStatusAndDocCodeContainingIgnoreCase(
                    DocumentStatus.APPROVED, value));
        }
        documents.addAll(documentRepository.findByStatusAndProjectNameContainingIgnoreCase(
                DocumentStatus.APPROVED, value));
        documents.addAll(documentRepository.findByStatusAndApplicant_NameContainingIgnoreCase(
                DocumentStatus.APPROVED, value));
        return documents.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<QuickDocumentItem> quickDocuments(BusinessType businessType) {
        return quickRepository.findByBusinessTypeOrderBySortOrderAsc(businessType).stream()
                .map(item -> new QuickDocumentItem(
                        item.getId(), item.getBusinessType(), item.getName(), item.getSortOrder()))
                .toList();
    }

    @Transactional
    public DocumentSummary repeat(Long sourceId) {
        AuthenticatedUser currentUser = CurrentUser.require();
        OaDocument source = visibleDocument(sourceId, currentUser);
        SubmitDocumentRequest request = new SubmitDocumentRequest(
                source.getBusinessType(), source.getProjectName(), source.getCompany(), source.getAmount(),
                source.getInvoiceSummary(), source.getReason(), source.isNeedPostMaterial(),
                source.getContractNo(), source.getLinkedDocument() == null ? null : source.getLinkedDocument().getId(),
                false, false, null, source.getSealProject(), source.getSealDepartment(), source.getSealTime(),
                source.getSealFileName(), source.getSealType(), source.getSealReason(), List.of());
        return submit(request);
    }

    @Transactional
    public DocumentDetail.AttachmentItem upload(
            Long documentId,
            String nodeName,
            MultipartFile file) {
        AuthenticatedUser currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        LocalAttachmentStorage.StoredFile stored = attachmentStorage.store(file);
        OaAttachment attachment = new OaAttachment(
                stored.originalName(), stored.path(), stored.contentType(), stored.size());
        attachment.setNodeName(nodeName);
        attachment.setUploader(currentUserEntity(currentUser));
        document.addAttachment(attachment);
        documentRepository.flush();
        return attachmentItem(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(Long attachmentId) {
        AuthenticatedUser currentUser = CurrentUser.require();
        OaAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在"));
        visibleDocument(attachment.getDocument().getId(), currentUser);
        return new AttachmentDownload(
                attachment.getFileName(), attachment.getContentType(), attachmentStorage.load(attachment.getFilePath()));
    }

    public List<String> attachmentRequirements(BusinessType type, String projectName) {
        return requirementService.requiredFor(type, projectName);
    }

    private void validate(SubmitDocumentRequest request, BusinessType type) {
        if (type == BusinessType.SEAL_APPLICATION) {
            if (!StringUtils.hasText(request.sealProject()) || !StringUtils.hasText(request.sealDepartment())
                    || request.sealTime() == null || !StringUtils.hasText(request.sealFileName())
                    || request.sealType() == null || !StringUtils.hasText(request.sealReason())) {
                throw new BusinessException(ErrorCode.SEAL_INFO_INCOMPLETE, "请完整填写用印申请信息");
            }
            return;
        }
        if (request.company() == null || request.amount() == null || request.amount().signum() < 0
                || !StringUtils.hasText(request.reason())) {
            throw new BusinessException(ErrorCode.PAYMENT_INFO_INCOMPLETE, "请完整填写付款申请信息");
        }
    }

    private void applyRequest(OaDocument document, SubmitDocumentRequest request, BusinessType type) {
        document.setBusinessType(type);
        document.setProjectName(request.projectName());
        document.setCompany(request.company());
        document.setAmount(type == BusinessType.SEAL_APPLICATION ? null : request.amount());
        document.setInvoiceSummary(request.invoiceSummary());
        document.setReason(request.reason());
        document.setNeedPostMaterial(request.needPostMaterial());
        document.setContractNo(request.contractNo());
        document.setSealProject(request.sealProject());
        document.setSealDepartment(request.sealDepartment());
        document.setSealTime(request.sealTime());
        document.setSealFileName(request.sealFileName());
        document.setSealType(request.sealType());
        document.setSealReason(request.sealReason());
    }

    private Map<String, Object> workflowVariables(OaDocument document, SubmitDocumentRequest request) {
        return Map.ofEntries(
                Map.entry("amount", document.getAmount() == null ? BigDecimal.ZERO : document.getAmount()),
                Map.entry("involvesFunds", request.involvesFunds()),
                Map.entry("requiresAdminReview", request.requiresAdminReview()),
                Map.entry("businessMode", request.businessMode() == null ? "" : request.businessMode()));
    }

    /** ccUserIds 来自请求 DTO 的不可变列表（紧凑构造器已保证非 null），可直接构造集合。 */
    private void createSelfSelectedCc(OaDocument document, List<Long> ccUserIds) {
        Set<Long> ids = new LinkedHashSet<>(ccUserIds);
        List<SysUser> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new BusinessException(ErrorCode.CC_USER_NOT_FOUND, "抄送人员不存在");
        }
        users.stream().map(user -> CcRecord.toUser(document, user, CcSource.SELF_SELECTED))
                .forEach(ccRepository::save);
    }

    private OaDocument approvedDocument(Long id) {
        OaDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINKED_DOCUMENT_NOT_FOUND, "前置单据不存在"));
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.LINKED_DOCUMENT_NOT_APPROVED, "仅可关联已审批通过单据");
        }
        return document;
    }

    private OaDocument visibleDocument(Long id, AuthenticatedUser currentUser) {
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and((root, query, builder) -> builder.equal(root.get("id"), id));
        return documentRepository.findOne(spec)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));
    }

    private SysUser currentUserEntity(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "当前用户不存在");
        }
        return currentUser.userId() == null
                ? userRepository.findByAccount(currentUser.account())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "当前用户不存在"))
                : userRepository.findById(currentUser.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "当前用户不存在"));
    }

    private Specification<OaDocument> criteriaSpecification(DocumentSearchCriteria criteria) {
        if (criteria == null) return Specification.where(null);
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (StringUtils.hasText(criteria.keyword())) {
                String keyword = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("docCode")), keyword),
                        builder.like(builder.lower(root.get("projectName")), keyword),
                        builder.like(builder.lower(root.get("applicant").get("name")), keyword)));
            }
            if (criteria.status() != null) predicates.add(builder.equal(root.get("status"), criteria.status()));
            if (criteria.businessType() != null) predicates.add(builder.equal(root.get("businessType"), criteria.businessType()));
            if (criteria.documentType() != null) predicates.add(builder.equal(root.get("documentType"), criteria.documentType()));
            if (criteria.applicantId() != null) predicates.add(builder.equal(root.get("applicant").get("id"), criteria.applicantId()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private DocumentSummary toSummary(OaDocument document) {
        return new DocumentSummary(
                document.getId(), document.getDocCode(), document.getProjectName(),
                document.getBusinessType(), document.getDocumentType(), document.getApplicantName(),
                document.getDepartment(), document.getAmount(), document.getStatus(),
                document.getCurrentNode(), document.isRiskFlag(), document.getUpdatedAt());
    }

    private DocumentDetail.LinkedDocument linked(OaDocument document) {
        OaDocument linked = document.getLinkedDocument();
        return linked == null ? null : new DocumentDetail.LinkedDocument(
                linked.getId(), linked.getDocCode(), linked.getProjectName(), linked.getContractNo());
    }

    private DocumentDetail.AttachmentItem attachmentItem(OaAttachment attachment) {
        return new DocumentDetail.AttachmentItem(
                attachment.getId(), attachment.getFileName(), attachment.getContentType(), attachment.getFileSize(),
                attachment.getNodeName(), attachment.getUploader() == null ? null : attachment.getUploader().getName(),
                attachment.getCreatedAt());
    }

    private DocumentDetail.ApprovalItem approvalItem(ApprovalRecord approval) {
        return new DocumentDetail.ApprovalItem(
                approval.getId(), approval.getNodeName(), approval.getApprover().getName(),
                approval.getAction(), approval.getComment(), approval.getCreatedAt());
    }

    private DocumentDetail.CcItem ccItem(CcRecord cc) {
        return new DocumentDetail.CcItem(
                cc.getId(), cc.getTargetName(), cc.getSource().name(), cc.getCreatedAt());
    }

    public record AttachmentDownload(String fileName, String contentType, Resource resource) {}
}
