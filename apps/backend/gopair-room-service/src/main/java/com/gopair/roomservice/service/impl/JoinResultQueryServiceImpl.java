package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.JoinResultQueryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JoinResultQueryServiceImpl implements JoinResultQueryService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RoomMemberMapper roomMemberMapper;

    public JoinResultQueryServiceImpl(StringRedisTemplate stringRedisTemplate, RoomMemberMapper roomMemberMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomMemberMapper = roomMemberMapper;
    }

    @Override
    public JoinStatusVO queryByToken(String joinToken) {
        String tokenKey = "join:" + joinToken;
        String status = stringRedisTemplate.opsForValue().get(tokenKey);
        if (status == null) {
            return new JoinStatusVO(JoinStatusVO.Status.PROCESSING, null, null, "处理中");
        }
        switch (status) {
            case "JOINED":
                // 无法直接从token取roomId/userId，这里返回状态为JOINED；前端可刷新房间列表
                return new JoinStatusVO(JoinStatusVO.Status.JOINED, null, null, "加入成功");
            case "FAILED":
                return new JoinStatusVO(JoinStatusVO.Status.FAILED, null, null, "加入失败");
            default:
                return new JoinStatusVO(JoinStatusVO.Status.PROCESSING, null, null, "处理中");
        }
    }
} 