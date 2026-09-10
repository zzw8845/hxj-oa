package com.hxj.permission;

import com.hxj.exception.BusinessException;
import com.hxj.repository.SysDepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 部门闭包表语义测试：任意层级建树、移动子树、环检测与闭包一致性。
 *
 * <p>树形：中心(1) → 部门A(2) → 组A1(3)；组A1 后移动到中心下，验证闭包随移动重建。
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(DepartmentManagementService.class)
class DepartmentManagementServiceTest {

    @Autowired private DepartmentManagementService departmentService;
    @Autowired private SysDepartmentRepository departmentRepository;

    private Long centerId;
    private Long deptAId;
    private Long groupA1Id;

    @BeforeEach
    void setUpThreeLevelTree() {
        centerId = departmentService.create(new SaveDepartmentRequest("中心", null, 1)).id();
        deptAId = departmentService.create(new SaveDepartmentRequest("部门A", centerId, 1)).id();
        groupA1Id = departmentService.create(new SaveDepartmentRequest("组A1", deptAId, 1)).id();
    }

    @Test
    void shouldBuildClosurePathsForArbitraryDepth() {
        // 三层树：中心 → 部门A → 组A1
        assertThat(departmentRepository.findSubtreeIds(centerId))
                .containsExactlyInAnyOrder(centerId, deptAId, groupA1Id);
        assertThat(departmentRepository.findSubtreeIds(deptAId))
                .containsExactlyInAnyOrder(deptAId, groupA1Id);
        assertThat(departmentRepository.findSubtreeIds(groupA1Id))
                .containsExactlyInAnyOrder(groupA1Id);

        // 闭包距离正确：中心→组A1 距离 2
        assertThat(departmentRepository.countPath(centerId, groupA1Id)).isEqualTo(1);

        // 树形返回
        List<DepartmentViews.Department> tree = departmentService.tree();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).extracting(DepartmentViews.Department::name).containsExactly("部门A");
        assertThat(tree.get(0).children().get(0).children())
                .extracting(DepartmentViews.Department::name).containsExactly("组A1");
    }

    @Test
    void shouldRejectMoveIntoOwnSubtree() {
        // 移动到自身子树内（部门A 移到 组A1 下）→ 成环拒绝
        assertThatThrownBy(() -> departmentService.update(
                deptAId, new SaveDepartmentRequest("部门A", groupA1Id, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能将部门移动到自身或其下级部门下");
        // 移动到自身
        assertThatThrownBy(() -> departmentService.update(
                deptAId, new SaveDepartmentRequest("部门A", deptAId, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能将部门移动到自身或其下级部门下");

        // 闭包未被破坏
        assertThat(departmentRepository.findSubtreeIds(centerId))
                .containsExactlyInAnyOrder(centerId, deptAId, groupA1Id);
    }

    @Test
    void shouldMoveSubtreeAndRebuildClosure() {
        // 新建兄弟部门 部门B，把 组A1 移动到 部门B 下
        Long deptBId = departmentService.create(new SaveDepartmentRequest("部门B", centerId, 2)).id();
        departmentService.update(groupA1Id, new SaveDepartmentRequest("组A1", deptBId, 1));

        // 子树跟随移动
        assertThat(departmentRepository.findSubtreeIds(deptAId)).containsExactly(deptAId);
        assertThat(departmentRepository.findSubtreeIds(deptBId)).containsExactlyInAnyOrder(deptBId, groupA1Id);
        assertThat(departmentRepository.findSubtreeIds(centerId))
                .containsExactlyInAnyOrder(centerId, deptAId, deptBId, groupA1Id);

        // 组A1 移回根部门（parentId=null）
        departmentService.update(groupA1Id, new SaveDepartmentRequest("组A1", null, 1));
        assertThat(departmentRepository.findSubtreeIds(centerId))
                .containsExactlyInAnyOrder(centerId, deptAId, deptBId);
        assertThat(departmentRepository.countPath(groupA1Id, groupA1Id)).isEqualTo(1);
        assertThat(departmentService.tree()).hasSize(2);
    }

    @Test
    void shouldDeleteLeafAndCleanClosure() {
        // 非叶部门禁止删除
        assertThatThrownBy(() -> departmentService.delete(deptAId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("存在下级部门，无法删除");

        // 叶部门删除后闭包同步清理
        departmentService.delete(groupA1Id);
        assertThat(departmentRepository.findSubtreeIds(deptAId)).containsExactly(deptAId);
        assertThat(departmentRepository.countPath(centerId, groupA1Id)).isZero();

        // 重名部门不可创建
        assertThatThrownBy(() -> departmentService.create(new SaveDepartmentRequest("中心", null, 9)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门名称已存在");
    }
}
