package com.hxj.workflow;

import com.hxj.entity.CcRecord;
import com.hxj.enums.CcSourceEnum;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用 BPMN 抄送节点：按节点名称解析抄送角色或部门人员，
 * 生成抄送记录后流程继续流转，不阻塞审批。
 */
@Component("oaCcNodeDelegate")
public class OaCcNodeDelegate implements JavaDelegate {

    /** 常见简称到预置角色的映射，未命中时再按部门找人（如“总经办”）。 */
    private static final Map<String, List<String>> ROLE_ALIASES = Map.of(
            "相关负责人", List.of("二级部门负责人"),
            "管理人员", List.of("行政专员"),
            "财务", List.of("财务经理"),
            "内控", List.of("内控主管"),
            "商务", List.of("商务专员"),
            "运营服务", List.of("运营服务主管"));

    private final OaDocumentRepository documentRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRepository userRepository;
    private final CcRecordRepository ccRepository;

    public OaCcNodeDelegate(
            OaDocumentRepository documentRepository,
            SysRoleRepository roleRepository,
            SysUserRepository userRepository,
            CcRecordRepository ccRepository) {
        this.documentRepository = documentRepository;
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
        if (document == null) {
            return;
        }
        String nodeName = execution.getCurrentFlowElement() == null
                ? null : execution.getCurrentFlowElement().getName();
        for (SysRole role : resolveRoles(nodeName)) {
            ccRepository.save(CcRecord.toRole(document, role, CcSourceEnum.FLOW));
        }
        for (SysUser user : resolveUsers(nodeName)) {
            ccRepository.save(CcRecord.toUser(document, user, CcSourceEnum.FLOW));
        }
    }

    /** 抄送目标角色：节点名剔除“抄送”前缀后按分隔符拆分，逐段匹配预置角色。 */
    private List<SysRole> resolveRoles(String nodeName) {
        Set<SysRole> roles = new LinkedHashSet<>();
        for (String segment : segments(nodeName)) {
            for (SysRole role : roleRepository.findAll()) {
                if (matches(role.getName(), segment)) {
                    roles.add(role);
                }
            }
            for (String alias : ROLE_ALIASES.getOrDefault(segment, List.of())) {
                roleRepository.findByName(alias).ifPresent(roles::add);
            }
        }
        return new ArrayList<>(roles);
    }

    /** 无对应角色时按部门找人（如“总经办”→总经办部门成员）。 */
    private List<SysUser> resolveUsers(String nodeName) {
        Set<SysUser> users = new LinkedHashSet<>();
        for (String segment : segments(nodeName)) {
            if (ROLE_ALIASES.containsKey(segment)) {
                continue;
            }
            boolean roleMatched = roleRepository.findAll().stream()
                    .anyMatch(role -> matches(role.getName(), segment));
            if (!roleMatched) {
                users.addAll(userRepository.findByDepartment(segment));
            }
        }
        return new ArrayList<>(users);
    }

    private List<String> segments(String nodeName) {
        String cleaned = nodeName == null ? "" : nodeName.replaceFirst("^抄送", "");
        List<String> result = new ArrayList<>();
        for (String piece : cleaned.split("[/,、及]")) {
            String trimmed = piece.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean matches(String roleName, String segment) {
        return roleName.contains(segment) || segment.contains(roleName);
    }
}
