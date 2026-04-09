package com.gopair.messageservice.service.impl;

import com.gopair.framework.logging.annotation.LogRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.messageservice.domain.dto.UserPublicProfileDto;
import com.gopair.messageservice.domain.vo.MessageVO;
import com.gopair.messageservice.mapper.UserPublicMapper;
import com.gopair.messageservice.service.UserProfileFallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户资料降级服务实现
 *
 * <h2>完整流程</h2>
 * <ol>
 * <li><b>收集缺失 userId</b>：遍历消息列表，若 senderNickname 为空则收集 senderId；若 replyToSenderNickname 为空则收集 replyToSenderId。</li>
 * <li><b>查共享库</b>：用 IN 查询从同库 user 表拉资料（与 JOIN 结果互为冗余，避免重复 HTTP）。</li>
 * <li><b>聚合补全</b>：将同库结果合并；仍缺的 userId 再调 user-service HTTP 批量补全。</li>
 * <li><b>回填 VO</b>：直接设入 MessageVO 的 senderNickname / senderAvatar / replyToSenderNickname。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileFallbackServiceImpl implements UserProfileFallbackService {

    private final UserPublicMapper userPublicMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_SERVICE_URL = "http://user-service/user/";
    private static final int USER_BATCH_MAX = 200;
    private static final int USER_SINGLE_FALLBACK_MAX = 64;

    @Override
    @LogRecord(operation = "补全缺失用户资料", module = "消息管理")
    public void fillMissingProfiles(List<MessageVO> messageList, List<Long> replyToIds) {
        if (messageList == null || messageList.isEmpty()) {
            return;
        }

        Set<Long> toFetch = new LinkedHashSet<>();
        Map<Long, ProfileEntry> profiles = new HashMap<>();

        for (MessageVO msg : messageList) {
            if (!StringUtils.hasText(msg.getSenderNickname()) && msg.getSenderId() != null) {
                toFetch.add(msg.getSenderId());
            }
            if (!StringUtils.hasText(msg.getReplyToSenderNickname()) && msg.getReplyToSenderId() != null) {
                toFetch.add(msg.getReplyToSenderId());
            }
        }

        if (replyToIds != null && !replyToIds.isEmpty()) {
            toFetch.addAll(replyToIds);
        }

        if (toFetch.isEmpty()) {
            return;
        }

        List<Long> distinct = toFetch.stream().limit(USER_BATCH_MAX).collect(Collectors.toList());

        loadFromSharedTable(distinct, profiles);
        if (profiles.size() < distinct.size()) {
            mergeFromUserServiceHttp(distinct, profiles);
        }
        fillMissingBySingleFetch(distinct, profiles);

        applyProfiles(messageList, profiles);
    }

    private void loadFromSharedTable(List<Long> ids, Map<Long, ProfileEntry> profiles) {
        try {
            List<UserPublicProfileDto> rows = userPublicMapper.selectByUserIds(ids);
            if (rows == null) {
                return;
            }
            for (UserPublicProfileDto row : rows) {
                if (row == null || row.getUserId() == null) {
                    continue;
                }
                putMerge(profiles, row.getUserId(), new ProfileEntry(row.getNickname(), row.getAvatar()));
            }
        } catch (Exception e) {
            log.warn("[消息-用户资料] 从共享库 user 表拉取失败: {}", e.getMessage());
        }
    }

    private void mergeFromUserServiceHttp(List<Long> ids, Map<Long, ProfileEntry> profiles) {
        String idParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            String url = USER_SERVICE_URL + "by-ids?ids=" + idParam;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return;
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) {
                return;
            }
            for (JsonNode item : dataArray) {
                long uid = parseUserId(item.path("userId"));
                if (uid < 0) {
                    uid = parseUserId(item.path("id"));
                }
                if (uid < 0) {
                    continue;
                }
                String nick = textOrNull(item.path("nickname"));
                String av = textOrNull(item.path("avatar"));
                putMerge(profiles, uid, new ProfileEntry(nick, av));
            }
        } catch (Exception e) {
            log.warn("[消息-用户资料] 用户服务批量接口失败: {}", e.getMessage());
        }
    }

    private void fillMissingBySingleFetch(List<Long> ids, Map<Long, ProfileEntry> profiles) {
        int budget = USER_SINGLE_FALLBACK_MAX;
        for (Long uid : ids) {
            if (profiles.containsKey(uid) && StringUtils.hasText(profiles.get(uid).nickname)) {
                continue;
            }
            if (budget-- <= 0) {
                log.warn("[消息-用户资料] 单个补拉已达上限{}", USER_SINGLE_FALLBACK_MAX);
                break;
            }
            fetchOne(uid).ifPresent(p -> putMerge(profiles, uid, p));
        }
    }

    private Optional<ProfileEntry> fetchOne(Long userId) {
        try {
            String response = restTemplate.getForObject(USER_SERVICE_URL + userId, String.class);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new ProfileEntry(textOrNull(data.path("nickname")), textOrNull(data.path("avatar"))));
        } catch (Exception e) {
            log.debug("[消息-用户资料] 单个拉取用户{}失败: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    private void applyProfiles(List<MessageVO> messageList, Map<Long, ProfileEntry> profiles) {
        for (MessageVO msg : messageList) {
            if (msg.getSenderId() != null) {
                ProfileEntry p = profiles.get(msg.getSenderId());
                if (p != null) {
                    if (!StringUtils.hasText(msg.getSenderNickname()) && StringUtils.hasText(p.nickname)) {
                        msg.setSenderNickname(p.nickname);
                    }
                    if (!StringUtils.hasText(msg.getSenderAvatar()) && StringUtils.hasText(p.avatar)) {
                        msg.setSenderAvatar(p.avatar);
                    }
                }
            }
            if (msg.getReplyToSenderId() != null) {
                ProfileEntry p = profiles.get(msg.getReplyToSenderId());
                if (p != null && !StringUtils.hasText(msg.getReplyToSenderNickname()) && StringUtils.hasText(p.nickname)) {
                    msg.setReplyToSenderNickname(p.nickname);
                }
            }
        }
    }

    private static long parseUserId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return -1L;
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isTextual()) {
            try { return Long.parseLong(node.asText().trim()); } catch (NumberFormatException e) { return -1L; }
        }
        return -1L;
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private static void putMerge(Map<Long, ProfileEntry> map, long uid, ProfileEntry incoming) {
        ProfileEntry old = map.get(uid);
        if (old == null) {
            map.put(uid, incoming);
            return;
        }
        String nick = StringUtils.hasText(incoming.nickname) ? incoming.nickname : old.nickname;
        String av = StringUtils.hasText(incoming.avatar) ? incoming.avatar : old.avatar;
        map.put(uid, new ProfileEntry(nick, av));
    }

    private record ProfileEntry(String nickname, String avatar) {}
}
