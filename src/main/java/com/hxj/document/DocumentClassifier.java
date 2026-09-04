package com.hxj.document;

import com.hxj.entity.BusinessType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentClassifier {

    private static final List<String> BUSINESS_KEYWORDS =
            List.of("合作方退款", "服务费结算", "闭店", "退店");

    public BusinessType classify(String projectName, BusinessType requestedType) {
        if (requestedType == BusinessType.SEAL_APPLICATION) {
            return BusinessType.SEAL_APPLICATION;
        }
        String value = projectName == null ? "" : projectName;
        return BUSINESS_KEYWORDS.stream().anyMatch(value::contains)
                ? BusinessType.BUSINESS_PAYMENT
                : BusinessType.DAILY_PAYMENT;
    }
}