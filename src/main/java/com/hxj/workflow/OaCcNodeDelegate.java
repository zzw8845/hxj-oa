package com.hxj.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.CcRecord;
import com.hxj.entity.OaDocument;
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
 *
 * <p>引用一律走稳定标识（原文案名匹配已退役）：ROLE / DEPT 的 value 为<b>数字 ID</b>，
 * USER 的 value 为账号（登录名天然稳定）。改名不再导致抄送静默失配。
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
                    // 角色按 ID（改名不影响抄送路由）
                    case "ROLE" -> {
                        Long roleId = numericId(value);
                        if (roleId != null) {
                            roleRepository.findById(roleId).ifPresent(role ->
                                    ccRepository.save(CcRecord.toRole(document, role, CcSourceEnum.FLOW)));
                        }
                    }
                    // 部门按 ID（改名不影响抄送路由；闭包子树展开由调用方按需扩展）
                    case "DEPT" -> {
                        Long departmentId = numericId(value);
                        if (departmentId != null) {
                            userRepository.findByDepartmentId(departmentId).forEach(user ->
                                    ccRepository.save(CcRecord.toUser(document, user, CcSourceEnum.FLOW)));
                        }
                    }
                    // 账号是登录名，天然稳定
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

    /** 解析数字 ID；非数字返回 null（坏配置不阻塞流程，仅跳过该目标）。 */
    private static Long numericId(String value) {
        if (value == null || !value.trim().matches("\\d+")) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

}
