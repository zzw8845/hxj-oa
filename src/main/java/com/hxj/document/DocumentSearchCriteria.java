package com.hxj.document;

import com.hxj.entity.BusinessType;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.DocumentType;

public record DocumentSearchCriteria(
        String keyword,
        DocumentStatus status,
        BusinessType businessType,
        DocumentType documentType,
        Long applicantId) {
}