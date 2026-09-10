package com.hxj.repository;

import com.hxj.entity.SysDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 部门 Repository，包含闭包表（sys_department_closure）的维护与查询。
 *
 * <p>闭包原生 SQL 均使用标准语法（兼容 MySQL 与 H2，便于测试环境复用）。
 */
public interface SysDepartmentRepository extends JpaRepository<SysDepartment, Long> {

    boolean existsByName(String name);

    Optional<SysDepartment> findByName(String name);

    boolean existsByParentId(Long parentId);

    List<SysDepartment> findAllByOrderBySortOrderAscIdAsc();

    // —— 闭包维护（写）——

    /** 新部门的自身路径（depth=0）。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth) "
            + "VALUES (:id, :id, 0)", nativeQuery = true)
    void insertSelfPath(@Param("id") Long id);

    /** 新部门挂到父部门下：复制父部门的全部祖先路径并加一。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth) "
            + "SELECT p.ancestor_id, :id, p.depth + 1 FROM sys_department_closure p "
            + "WHERE p.descendant_id = :parentId", nativeQuery = true)
    void attachUnderParent(@Param("id") Long id, @Param("parentId") Long parentId);

    /**
     * 移动子树第一步：删除「后代在子树内、祖先在子树外」的路径，
     * 保留子树内部路径，使子树与原祖先链脱离。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM sys_department_closure "
            + "WHERE descendant_id IN (SELECT d.descendant_id FROM sys_department_closure d "
            + "WHERE d.ancestor_id = :rootId) "
            + "AND ancestor_id NOT IN (SELECT d.descendant_id FROM sys_department_closure d "
            + "WHERE d.ancestor_id = :rootId)", nativeQuery = true)
    void detachSubtree(@Param("rootId") Long rootId);

    /**
     * 移动子树第二步：新父部门的祖先链（含自身）× 子树全部后代，
     * 距离 = 新祖先距离 + 1 + 子树内距离。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO sys_department_closure (ancestor_id, descendant_id, depth) "
            + "SELECT a.ancestor_id, s.descendant_id, a.depth + 1 + s.depth "
            + "FROM sys_department_closure a "
            + "CROSS JOIN sys_department_closure s "
            + "WHERE a.descendant_id = :newParentId AND s.ancestor_id = :rootId", nativeQuery = true)
    void attachSubtreeUnderParent(@Param("rootId") Long rootId, @Param("newParentId") Long newParentId);

    /** 删除与某部门直接相关的全部路径（叶部门删除时使用，兼容无外键级联的环境）。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM sys_department_closure "
            + "WHERE ancestor_id = :id OR descendant_id = :id", nativeQuery = true)
    void deleteAllPathsOf(@Param("id") Long id);

    // —— 闭包查询（读）——

    /** 是否存在祖先→后代路径（用于环检测：目标父部门是否在子树内）。 */
    @Query(value = "SELECT COUNT(*) FROM sys_department_closure "
            + "WHERE ancestor_id = :ancestorId AND descendant_id = :descendantId", nativeQuery = true)
    long countPath(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);

    /** 查询某部门的全部后代（含自身）ID 列表。 */
    @Query(value = "SELECT descendant_id FROM sys_department_closure WHERE ancestor_id = :rootId",
            nativeQuery = true)
    List<Long> findSubtreeIds(@Param("rootId") Long rootId);
}
