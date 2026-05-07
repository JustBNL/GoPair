package com.gopair.voiceservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.voiceservice.domain.po.VoiceCallParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 语音通话参与者 Mapper
 *
 * @author gopair
 */
@Mapper
public interface VoiceCallParticipantMapper extends BaseMapper<VoiceCallParticipant> {

    int deleteByCallIds(@Param("callIds") List<Long> callIds);
}
