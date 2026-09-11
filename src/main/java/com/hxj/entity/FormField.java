package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 表单模板字段定义：key 为稳定契约（条件变量名），label 为显示名。 */
@Entity
@Table(name = "form_field", uniqueConstraints = @UniqueConstraint(name = "uk_template_field",
        columnNames = {"template_id", "field_key"}))
public class FormField {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_TEXTAREA = "TEXTAREA";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_SELECT = "SELECT";
    public static final String TYPE_BOOLEAN = "BOOLEAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private FormTemplate template;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "control_type", nullable = false, length = 20)
    private String controlType;

    @Column(nullable = false)
    private boolean required;

    /** SELECT 控件的选项，JSON 数组字符串。 */
    @Column
    private String options;

    /** 提升字段：值写结构化列/流程变量（amount/title 等），不可删除。 */
    @Column(nullable = false)
    private boolean reserved;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public FormField fieldKey(String fieldKey) { this.fieldKey = fieldKey; return this; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public FormField label(String label) { this.label = label; return this; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public boolean isReserved() { return reserved; }
    public void setReserved(boolean reserved) { this.reserved = reserved; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
