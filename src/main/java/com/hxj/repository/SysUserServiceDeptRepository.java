package com.hxj.repository;

import com.hxj.entity.SysUserServiceDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SysUserServiceDeptRepository extends JpaRepository<SysUserServiceDept, Long> {

    List<SysUserServiceDept> findByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);

    /**
     * 某部门的主办会计账号：挂指定职能角色且服务该部门的成员，按用户 ID 升序取首个。
     */
    @Query("select u.account from SysUser u join u.roles r "
            + "where r.name = :roleName and u.id in "
            + " (select s.userId from SysUserServiceDept s where s.departmentId = :deptId) "
            + "order by u.id")
    List<String> findServingAccountantAccounts(@Param("deptId") Long deptId,
                                               @Param("roleName") String roleName);
}
