package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentClassifier {

    private static final List<String> BUSINESS_KEYWORDS =
            List.of("合作方退款", "服务费结算", "闭店", "退店");

    public BusinessTypeEnum classify(String projectName, BusinessTypeEnum requestedType) {
        if (requestedType == BusinessTypeEnum.SEAL_APPLICATION) {
            return BusinessTypeEnum.SEAL_APPLICATION;
        }
        String value = projectName == null ? "" : projectName;
        return BUSINESS_KEYWORDS.stream().anyMatch(value::contains)
                ? BusinessTypeEnum.BUSINESS_PAYMENT
                : BusinessTypeEnum.DAILY_PAYMENT;
    }
}