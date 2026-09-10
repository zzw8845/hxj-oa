package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.SysPost;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysPostRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 岗位字典管理服务（全公司统一职种库，钉钉式）：新增/改名/删除；
 * 被员工引用的岗位禁止删除；改名后同步员工与角色的展示快照。
 */
@Service
public class PostManagementService {

    private final SysPostRepository postRepository;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;

    public PostManagementService(
            SysPostRepository postRepository,
            SysUserRepository userRepository,
            SysRoleRepository roleRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /** 新增岗位。 */
    @Transactional
    public PostViews.Post create(SavePostRequest request) {
        if (postRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCodeEnum.POST_NAME_EXISTS, "岗位名称已存在");
        }
        SysPost post = new SysPost();
        post.setName(request.name());
        return toView(postRepository.save(post));
    }

    /** 编辑岗位名称，并同步员工与角色的展示快照。 */
    @Transactional
    public PostViews.Post update(Long postId, SavePostRequest request) {
        SysPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.POST_NOT_FOUND, "岗位不存在"));
        postRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(postId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCodeEnum.POST_NAME_EXISTS, "岗位名称已存在");
                });
        boolean renamed = !post.getName().equals(request.name());
        String oldName = post.getName();
        post.setName(request.name());
        if (renamed) {
            // 同步员工与角色的岗位展示快照（角色的岗位为字符串标签）
            List<SysUser> members = userRepository.findByPostId(postId);
            members.forEach(member -> member.setPost(request.name()));
            userRepository.saveAll(members);
            List<SysRole> roles = roleRepository.findByPost(oldName);
            roles.forEach(role -> role.setPost(request.name()));
            roleRepository.saveAll(roles);
        }
        return toView(postRepository.save(post));
    }

    /** 删除岗位：被员工引用的禁止删除。 */
    @Transactional
    public void delete(Long postId) {
        SysPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.POST_NOT_FOUND, "岗位不存在"));
        if (userRepository.existsByPostId(postId)) {
            throw new BusinessException(ErrorCodeEnum.POST_HAS_EMPLOYEES, "岗位下存在员工，无法删除");
        }
        postRepository.delete(post);
    }

    /** 岗位列表（按名称排序，供下拉与列表页使用）。 */
    @Transactional(readOnly = true)
    public List<PostViews.Post> list() {
        return postRepository.findAll().stream()
                .sorted(Comparator.comparing(SysPost::getName))
                .map(this::toView)
                .toList();
    }

    /** 解析岗位引用（员工创建/编辑使用）：必须存在。 */
    @Transactional(readOnly = true)
    public SysPost requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.POST_NOT_FOUND, "岗位不存在"));
    }

    private PostViews.Post toView(SysPost post) {
        return new PostViews.Post(post.getId(), post.getName());
    }
}
