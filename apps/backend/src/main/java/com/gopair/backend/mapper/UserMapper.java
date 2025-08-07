package com.gopair.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.backend.domain.po.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口
 * 
 * @author gopair
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
} 