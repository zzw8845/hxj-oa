package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.enums.DocumentTypeEnum;

public record DocumentSearchCondition(
        String keyword,
        DocumentStatusEnum status,
        BusinessTypeEnum businessType,
        DocumentTypeEnum documentType,
        Long applicantId) {
}