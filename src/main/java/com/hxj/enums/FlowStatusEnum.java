package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 流程配置的发布状态（两态，配置与运行时分离）：
 * <ul>
 *   <li>{@link #DRAFT}：草稿——保存只做校验落库，不出现在可提交入口，改错不污染任何单据；</li>
 *   <li>{@link #PUBLISHED}：已发布——发布动作完成 Flowable 部署后生效，模板可绑定、单据可提交。</li>
 * </ul>
 * 已发布流程再编辑自动回退草稿，需重新发布才能影响新单据；在途实例由 Flowable
 * 定义版本隔离，天然按旧版本继续流转。
 */
public enum FlowStatusEnum {
    DRAFT,
    PUBLISHED;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static FlowStatusEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
