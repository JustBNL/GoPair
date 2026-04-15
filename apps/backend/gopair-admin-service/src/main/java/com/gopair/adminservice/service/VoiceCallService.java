package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.domain.po.VoiceCallParticipant;
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

    public record VoiceCallPageQuery(Integer pageNum, Integer pageSize, Long roomId, Integer status) {}

    public Page<VoiceCall> getVoiceCallPage(VoiceCallPageQuery query) {
        Page<VoiceCall> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<VoiceCall> wrapper = new LambdaQueryWrapper<>();
        if (query.roomId() != null) {
            wrapper.eq(VoiceCall::getRoomId, query.roomId());
        }
        if (query.status() != null) {
            wrapper.eq(VoiceCall::getStatus, query.status());
        }
        wrapper.orderByDesc(VoiceCall::getStartTime);
        return voiceCallMapper.selectPage(page, wrapper);
    }

    public VoiceCall getVoiceCallById(Long callId) {
        return voiceCallMapper.selectById(callId);
    }

    public List<VoiceCallParticipant> getParticipants(Long callId) {
        return participantMapper.selectList(
                new LambdaQueryWrapper<VoiceCallParticipant>().eq(VoiceCallParticipant::getCallId, callId)
        );
    }
}
