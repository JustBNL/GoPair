package com.gopair.voiceservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.voiceservice.domain.po.VoiceCall;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语音通话 Mapper
 *
 * @author gopair
 */
@Mapper
public interface VoiceCallMapper extends BaseMapper<VoiceCall> {
}
