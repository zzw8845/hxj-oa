package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * 部门闭包表实体：存储任意两个部门间的祖先-后代路径（含自身 depth=0）。
 *
 * <p>维护算法见 {@code com.hxj.permission.DepartmentManagementService}：
 * 新建/移动子树时同步增删路径，保证「查子树一条 SQL」的性质恒成立。
 */
@Entity
@Table(name = "sys_department_closure")
public class SysDepartmentClosure {

    /** 路径主键（祖先ID + 后代ID）。 */
    @EmbeddedId
    private Path id;

    /** 祖先到后代的距离，自身为 0。 */
    @Column(nullable = false)
    private Integer depth;

    public SysDepartmentClosure() {
    }

    public SysDepartmentClosure(Path id, Integer depth) {
        this.id = id;
        this.depth = depth;
    }

    /** 构建一条祖先→后代路径。 */
    public static SysDepartmentClosure of(Long ancestorId, Long descendantId, Integer depth) {
        return new SysDepartmentClosure(new Path(ancestorId, descendantId), depth);
    }

    public Path getId() { return id; }
    public void setId(Path id) { this.id = id; }
    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }

    /** 闭包路径复合主键。 */
    @Embeddable
    public static class Path implements Serializable {

        /** 祖先部门ID。 */
        @Column(name = "ancestor_id", nullable = false)
        private Long ancestorId;

        /** 后代部门ID。 */
        @Column(name = "descendant_id", nullable = false)
        private Long descendantId;

        public Path() {
        }

        public Path(Long ancestorId, Long descendantId) {
            this.ancestorId = ancestorId;
            this.descendantId = descendantId;
        }

        public Long getAncestorId() { return ancestorId; }
        public Long getDescendantId() { return descendantId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Path path)) {
                return false;
            }
            return java.util.Objects.equals(ancestorId, path.ancestorId)
                    && java.util.Objects.equals(descendantId, path.descendantId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(ancestorId, descendantId);
        }
    }
}
