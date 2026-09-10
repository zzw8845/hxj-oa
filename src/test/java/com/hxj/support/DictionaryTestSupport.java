package com.hxj.support;

import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysPost;
import com.hxj.entity.SysUser;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysPostRepository;

/**
 * 测试夹具：确保部门/岗位字典存在，并回填员工的字典外键与展示快照。
 *
 * <p>员工实体的 department_id/post_id 已收紧为非空，测试中直接构造 SysUser
 * 持久化时必须先通过本工具补齐字典引用。
 */
public final class DictionaryTestSupport {

    private DictionaryTestSupport() {
    }

    /** 确保指定名称的部门存在并返回（不存在则创建）。 */
    public static SysDepartment ensureDepartment(SysDepartmentRepository repository, String name) {
        return repository.findByName(name).orElseGet(() -> {
            SysDepartment department = new SysDepartment();
            department.setName(name);
            department.setSortOrder(0);
            return repository.save(department);
        });
    }

    /** 确保指定名称的岗位存在并返回（不存在则创建）。 */
    public static SysPost ensurePost(SysPostRepository repository, String name) {
        return repository.findByName(name).orElseGet(() -> {
            SysPost post = new SysPost();
            post.setName(name);
            return repository.save(post);
        });
    }

    /** 为员工写入字典外键与名称快照。 */
    public static void applyDictionary(SysUser user, SysDepartment department, SysPost post) {
        user.setDepartmentId(department.getId());
        user.setDepartment(department.getName());
        user.setPostId(post.getId());
        user.setPost(post.getName());
    }
}
