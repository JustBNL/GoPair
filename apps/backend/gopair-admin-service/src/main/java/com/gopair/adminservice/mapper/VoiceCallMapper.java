package com.gopair.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.query.VoiceCallPageQuery;
import com.gopair.adminservice.domain.vo.VoiceCallVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VoiceCallMapper extends BaseMapper<VoiceCall> {

    IPage<VoiceCallVO> selectVoiceCallPage(IPage<VoiceCallVO> page, @Param("query") VoiceCallPageQuery query);
}
