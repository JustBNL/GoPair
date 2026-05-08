package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
import com.gopair.adminservice.domain.query.VoiceCallPageQuery;
import com.gopair.adminservice.domain.vo.VoiceCallVO;
import com.gopair.adminservice.mapper.VoiceCallMapper;
import com.gopair.adminservice.mapper.VoiceCallParticipantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通话记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceCallService {

    private final VoiceCallMapper voiceCallMapper;
    private final VoiceCallParticipantMapper participantMapper;

    public Page<VoiceCallVO> getVoiceCallPage(VoiceCallPageQuery query) {
        Page<VoiceCallVO> page = new Page<>(query.pageNum(), query.pageSize());
        return voiceCallMapper.selectVoiceCallPage(page, query);
    }

    public VoiceCall getVoiceCallById(Long callId) {
        return voiceCallMapper.selectById(callId);
    }

    public List<VoiceCallParticipant> getParticipants(Long callId) {
        return participantMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoiceCallParticipant>()
                        .eq(VoiceCallParticipant::getCallId, callId)
        );
    }
}
