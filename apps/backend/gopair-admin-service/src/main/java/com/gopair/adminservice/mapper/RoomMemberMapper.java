package com.gopair.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.adminservice.domain.po.RoomMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoomMemberMapper extends BaseMapper<RoomMember> {
}
