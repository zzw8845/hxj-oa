package com.hxj.repository;

import com.hxj.entity.SysPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 岗位字典 Repository。 */
public interface SysPostRepository extends JpaRepository<SysPost, Long> {

    boolean existsByName(String name);

    Optional<SysPost> findByName(String name);
}
