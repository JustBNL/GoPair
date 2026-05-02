package com.gopair.chatservice.service;

import com.gopair.chatservice.domain.dto.UserPublicProfileDto;
import com.gopair.chatservice.domain.vo.FriendVO;

import java.util.List;

/**
 * 用户资料补全服务接口。
 *
 * @author gopair
 */
public interface UserProfileFallbackService {

    /**
     * 批量补全好友列表中缺失的用户资料。
     *
     * @param friends 好友VO列表（会直接修改）
     * @param friendIds 好友用户ID列表
     */
    void fillMissingFriendProfiles(List<FriendVO> friends, List<Long> friendIds);
}
