package com.gopair.chatservice.service.impl;

import com.gopair.chatservice.domain.dto.UserPublicProfileDto;
import com.gopair.chatservice.domain.vo.FriendVO;
import com.gopair.chatservice.mapper.UserPublicMapper;
import com.gopair.chatservice.service.UserProfileFallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户资料补全服务实现。
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileFallbackServiceImpl implements UserProfileFallbackService {

    private final UserPublicMapper userPublicMapper;
    private final RestTemplate restTemplate;

    private static final String USER_SERVICE_URL = "http://user-service/user/";
    private static final int USER_BATCH_MAX = 200;

    @Override
    public void fillMissingFriendProfiles(List<FriendVO> friends, List<Long> friendIds) {
        if (friends == null || friends.isEmpty() || friendIds == null || friendIds.isEmpty()) {
            return;
        }

        Map<Long, ProfileEntry> profiles = new HashMap<>();

        List<Long> distinct = friendIds.stream().distinct().limit(USER_BATCH_MAX).collect(Collectors.toList());

        List<UserPublicProfileDto> dbResults = userPublicMapper.selectByUserIds(distinct);
        for (UserPublicProfileDto dto : dbResults) {
            profiles.put(dto.getUserId(), new ProfileEntry(dto.getNickname(), dto.getEmail(), dto.getAvatar(), dto.getAvatarOriginalUrl()));
        }

        if (profiles.size() < distinct.size()) {
            mergeFromUserServiceHttp(distinct, profiles);
        }

        for (FriendVO friend : friends) {
            ProfileEntry entry = profiles.get(friend.getFriendId());
            if (entry != null) {
                if (friend.getNickname() == null && entry.nickname != null) {
                    friend.setNickname(entry.nickname);
                }
                if (friend.getEmail() == null && entry.email != null) {
                    friend.setEmail(entry.email);
                }
                if (friend.getAvatar() == null && entry.avatar != null) {
                    friend.setAvatar(entry.avatar);
                }
                if (friend.getAvatarOriginalUrl() == null && entry.avatarOriginalUrl != null) {
                    friend.setAvatarOriginalUrl(entry.avatarOriginalUrl);
                }
            }
            if (friend.getNickname() == null) {
                friend.setNickname("用户" + friend.getFriendId());
            }
        }
    }

    private void mergeFromUserServiceHttp(List<Long> ids, Map<Long, ProfileEntry> profiles) {
        try {
            String idParam = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            String response = restTemplate.getForObject(USER_SERVICE_URL + "by-ids?ids=" + idParam, String.class);
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            var body = om.readValue(response, Map.class);
            var dataArray = (List<Map<String, Object>>) body.get("data");
            if (dataArray != null) {
                for (Map<String, Object> item : dataArray) {
                    Object uid = item.get("userId");
                    if (uid != null) {
                        profiles.put(Long.valueOf(uid.toString()),
                                new ProfileEntry(
                                        (String) item.get("nickname"),
                                        (String) item.get("email"),
                                        (String) item.get("avatar"),
                                        (String) item.get("avatarOriginalUrl")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[聊天-用户资料] 批量接口失败: {}", e.getMessage());
        }
    }

    private record ProfileEntry(String nickname, String email, String avatar, String avatarOriginalUrl) {}
}
