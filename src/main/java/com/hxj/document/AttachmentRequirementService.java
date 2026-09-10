package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttachmentRequirementService {

    public List<String> requiredFor(BusinessTypeEnum type, String projectName) {
        if (type == BusinessTypeEnum.SEAL_APPLICATION) {
            return List.of("用印文件附件");
        }
        String project = projectName == null ? "" : projectName;
        if (project.contains("报销") || project.contains("差旅") || project.contains("招待费")) {
            return List.of("关联前置单据", "发票及费用明细", "付款截图或行程凭证");
        }
        if (project.contains("供应商") || project.contains("货款")) {
            return List.of("关联前置单据", "原始明细账单", "对账单", "发票");
        }
        return List.of("关联前置单据", "业务证明资料", "发票", "收款信息");
    }
}