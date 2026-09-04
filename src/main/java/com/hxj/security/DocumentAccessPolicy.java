package com.hxj.security;

import com.hxj.entity.BusinessType;
import com.hxj.entity.OaDocument;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将当前用户的数据范围转换为单据查询约束，未知范围默认拒绝。 */
@Component
public class DocumentAccessPolicy {

    public static final String ALL_SCOPE = "ALL_DEPARTMENTS_ALL_NODES";
    public static final List<String> DEPARTMENT_SCOPES = List.of(
            "OWN_DEPARTMENT_DOCUMENTS",
            "OWN_CENTER_DOCUMENTS");
    public static final String OWN_SCOPE = "OWN_DOCUMENTS";
    public static final List<String> FINANCE_SCOPES = List.of(
            "FINANCE_INTERNAL_CONTROL",
            "ASSIGNED_BUSINESS_DEPARTMENTS",
            "FINANCE_MANAGEMENT",
            "FUNDS_ACCOUNTS",
            "ASSIGNED_PAYMENT_LINES");
    public static final List<String> BUSINESS_SCOPES = List.of(
            "OPERATIONS_ADMIN_MATTERS",
            "OPERATIONS_MATTERS",
            "DELIVERY_PICKUP");

    public Specification<OaDocument> visibleTo(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return denyAll();
        }
        List<String> scopes = currentUser.dataScopes();
        if (scopes.contains(ALL_SCOPE)) {
            return Specification.where(null);
        }

        Specification<OaDocument> result = null;
        if (scopes.stream().anyMatch(DEPARTMENT_SCOPES::contains)) {
            result = or(result, department(currentUser.department()));
        }
        if (scopes.contains(OWN_SCOPE)) {
            result = or(result, applicant(currentUser.userId()));
        }
        if (scopes.stream().anyMatch(FINANCE_SCOPES::contains)) {
            result = or(result, financeDocuments());
        }
        if (scopes.stream().anyMatch(BUSINESS_SCOPES::contains)) {
            result = or(result, businessDocuments());
        }
        return result == null ? denyAll() : result;
    }

    private Specification<OaDocument> applicant(Long userId) {
        return (root, query, builder) -> builder.equal(root.get("applicant").get("id"), userId);
    }

    private Specification<OaDocument> department(String department) {
        return (root, query, builder) -> builder.equal(root.get("department"), department);
    }

    private Specification<OaDocument> financeDocuments() {
        return (root, query, builder) -> root.get("businessType").in(
                BusinessType.DAILY_PAYMENT,
                BusinessType.BUSINESS_PAYMENT);
    }

    private Specification<OaDocument> businessDocuments() {
        return (root, query, builder) -> builder.equal(
                root.get("businessType"), BusinessType.BUSINESS_PAYMENT);
    }

    private Specification<OaDocument> denyAll() {
        return (root, query, builder) -> builder.disjunction();
    }

    private Specification<OaDocument> or(
            Specification<OaDocument> left,
            Specification<OaDocument> right) {
        return left == null ? right : left.or(right);
    }
}