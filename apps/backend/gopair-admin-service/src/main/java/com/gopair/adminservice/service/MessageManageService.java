package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.Message;
import com.gopair.adminservice.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 消息管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageManageService {

    private final MessageMapper messageMapper;

    public record MessagePageQuery(Integer pageNum, Integer pageSize, Long roomId, String keyword) {}

    public Page<Message> getMessagePage(MessagePageQuery query) {
        Page<Message> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        if (query.roomId() != null) {
            wrapper.eq(Message::getRoomId, query.roomId());
        }
        if (StringUtils.hasText(query.keyword())) {
            wrapper.like(Message::getContent, query.keyword());
        }
        wrapper.orderByDesc(Message::getCreateTime);
        return messageMapper.selectPage(page, wrapper);
    }

    public Page<Message> getMessageByRoom(Long roomId, Integer pageNum, Integer pageSize) {
        Page<Message> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getRoomId, roomId)
                .orderByDesc(Message::getCreateTime);
        return messageMapper.selectPage(page, wrapper);
    }
}
