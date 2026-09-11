package com.hxj.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.CcRecord;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.CcSourceEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 通用 BPMN 抄送节点：按节点配置的结构化目标（cc_targets JSON：
 * [{"type":"ROLE|DEPT|USER","value":"..."}]）生成抄送记录后流程继续流转，不阻塞审批。
 */
@Component("oaCcNodeDelegate")
public class OaCcNodeDelegate implements JavaDelegate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OaDocumentRepository documentRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final com.hxj.repository.SysRoleRepository roleRepository;
    private final SysUserRepository userRepository;
    private final CcRecordRepository ccRepository;

    public OaCcNodeDelegate(
            OaDocumentRepository documentRepository,
            FlowConfigRepository flowConfigRepository,
            com.hxj.repository.SysRoleRepository roleRepository,
            SysUserRepository userRepository,
            CcRecordRepository ccRepository) {
        this.documentRepository = documentRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.ccRepository = ccRepository;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object documentId = execution.getVariable("documentId");
        if (documentId == null) {
            return;
        }
        OaDocument document = documentRepository.findById(((Number) documentId).longValue()).orElse(null);
        if (document == null || document.getFlowConfigId() == null) {
            return;
        }
        String nodeName = execution.getCurrentFlowElement() == null
                ? null : execution.getCurrentFlowElement().getName();
        FlowNodeConfig node = flowConfigRepository.findById(document.getFlowConfigId())
                .map(FlowConfig::getNodes)
                .orElse(List.of())
                .stream()
                .filter(n -> nodeName != null && nodeName.equals(n.getName()))
                .filter(n -> n.getNodeType() == FlowNodeTypeEnum.CC)
                .findFirst()
                .orElse(null);
        if (node == null || node.getCcTargets() == null || node.getCcTargets().isBlank()) {
            return;
        }
        try {
            List<Map<String, String>> targets = MAPPER.readValue(
                    node.getCcTargets(), new TypeReference<List<Map<String, String>>>() {
                    });
            for (Map<String, String> target : targets) {
                String type = target.getOrDefault("type", "");
                String value = target.getOrDefault("value", "");
                switch (type) {
                    case "ROLE" -> roleRepository.findByName(value).ifPresent(role ->
                            ccRepository.save(CcRecord.toRole(document, role, CcSourceEnum.FLOW)));
                    case "DEPT" -> userRepository.findByDepartment(value).forEach(user ->
                            ccRepository.save(CcRecord.toUser(document, user, CcSourceEnum.FLOW)));
                    case "USER" -> userRepository.findByAccount(value).ifPresent(user ->
                            ccRepository.save(CcRecord.toUser(document, user, CcSourceEnum.FLOW)));
                    default -> {
                        // 未知目标类型：忽略，避免阻塞流程
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("抄送目标解析失败：" + nodeName, e);
        }
    }

}
