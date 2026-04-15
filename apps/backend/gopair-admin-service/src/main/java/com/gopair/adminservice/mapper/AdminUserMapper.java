package com.gopair.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.adminservice.domain.po.AdminUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
