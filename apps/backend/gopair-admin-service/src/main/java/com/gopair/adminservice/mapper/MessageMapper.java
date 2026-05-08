package com.gopair.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gopair.adminservice.domain.po.Message;
import com.gopair.adminservice.domain.vo.MessageVO;
import com.gopair.adminservice.domain.query.MessagePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    IPage<MessageVO> selectMessagePage(IPage<MessageVO> page, @Param("query") MessagePageQuery query);
}
