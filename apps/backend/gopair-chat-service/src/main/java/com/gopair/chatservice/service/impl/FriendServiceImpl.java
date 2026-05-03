package com.gopair.chatservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.chatservice.config.ChatWebSocketProducer;
import com.gopair.chatservice.domain.dto.FriendRequestDto;
import com.gopair.chatservice.domain.dto.FriendStatusVO;
import com.gopair.chatservice.domain.dto.UserPublicProfileDto;
import com.gopair.chatservice.domain.po.Friend;
import com.gopair.chatservice.domain.po.FriendRequest;
import com.gopair.chatservice.domain.vo.FriendRequestVO;
import com.gopair.chatservice.domain.vo.FriendVO;
import com.gopair.chatservice.domain.vo.UserSearchResultVO;
import com.gopair.common.core.PageResult;
import com.gopair.chatservice.enums.ChatErrorCode;
import com.gopair.chatservice.enums.FriendStatus;
import com.gopair.chatservice.exception.ChatException;
import com.gopair.chatservice.mapper.FriendMapper;
import com.gopair.chatservice.mapper.FriendRequestMapper;
import com.gopair.chatservice.service.FriendService;
import com.gopair.chatservice.service.UserProfileFallbackService;
import com.gopair.framework.logging.annotation.LogRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友关系服务实现类。
 *
 * @author gopair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final ChatWebSocketProducer chatWebSocketProducer;
    private final UserProfileFallbackService userProfileFallbackService;
    private final UserPublicMapper userPublicMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_SERVICE_URL = "http://user-service/user/";
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "发送好友请求", module = "好友管理")
    public FriendRequestVO sendFriendRequest(FriendRequestDto dto, Long fromUserId) {
        Long toUserId = dto.getToUserId();

        if (fromUserId.equals(toUserId)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.CANNOT_ADD_SELF);
        }

        boolean alreadyFriends = friendMapper.isFriend(fromUserId, toUserId);
        if (alreadyFriends) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.ALREADY_FRIENDS);
        }

        boolean pending = friendRequestMapper.existsPending(fromUserId, toUserId);
        if (pending) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        }

        FriendRequest request = new FriendRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setStatus('0');
        request.setMessage(StringUtils.hasText(dto.getMessage()) ? dto.getMessage() : null);
        friendRequestMapper.insert(request);

        FriendRequestVO vo = toFriendRequestVO(request);

        chatWebSocketProducer.sendFriendRequestNotification(toUserId, Map.of(
            "requestId", request.getId(),
            "fromUserId", fromUserId,
            "message", request.getMessage() != null ? request.getMessage() : ""
        ));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "同意好友请求", module = "好友管理")
    public void acceptFriendRequest(Long requestId, Long currentUserId) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUserId().equals(currentUserId)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.NOT_YOUR_REQUEST);
        }
        if (request.getStatus() != '0') {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        request.setStatus('1');
        friendRequestMapper.updateById(request);

        Long userA = request.getFromUserId();
        Long userB = request.getToUserId();
        Long minUser = Math.min(userA, userB);
        Long maxUser = Math.max(userA, userB);

        Friend friend = new Friend();
        friend.setUserId(minUser);
        friend.setFriendId(maxUser);
        friend.setStatus('1');
        friendMapper.insert(friend);

        chatWebSocketProducer.sendFriendStatusNotification(userA, Map.of(
            "action", "accepted",
            "friendId", userB,
            "message", "对方已同意您的好友请求"
        ));

        log.info("好友请求已同意: requestId={}, fromUserId={}, toUserId={}", requestId, userA, userB);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "拒绝好友请求", module = "好友管理")
    public void rejectFriendRequest(Long requestId, Long currentUserId) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUserId().equals(currentUserId)) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.NOT_YOUR_REQUEST);
        }
        if (request.getStatus() != '0') {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        request.setStatus('2');
        friendRequestMapper.updateById(request);

        chatWebSocketProducer.sendFriendStatusNotification(request.getFromUserId(), Map.of(
            "action", "rejected",
            "friendId", currentUserId,
            "message", "对方拒绝了您的好友请求"
        ));

        log.info("好友请求已拒绝: requestId={}", requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "删除好友", module = "好友管理")
    public void deleteFriend(Long friendId, Long currentUserId) {
        int deleted = friendMapper.deleteByPair(currentUserId, friendId);
        if (deleted == 0) {
            throw new ChatException(com.gopair.chatservice.enums.ChatErrorCode.FRIEND_NOT_FOUND);
        }

        chatWebSocketProducer.sendFriendStatusNotification(friendId, Map.of(
            "action", "deleted",
            "friendId", currentUserId,
            "message", "对方已删除您为好友"
        ));

        log.info("删除好友: userId={}, friendId={}", currentUserId, friendId);
    }

    @Override
    @LogRecord(operation = "获取好友列表", module = "好友管理")
    public List<FriendVO> getFriends(Long userId) {
        List<Long> friendIds = friendMapper.selectFriendIds(userId);
        if (friendIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .eq(Friend::getUserId, userId).in(Friend::getFriendId, friendIds)
                .or()
                .eq(Friend::getFriendId, userId).in(Friend::getUserId, friendIds)
        ).eq(Friend::getStatus, '1');

        List<Friend> friends = friendMapper.selectList(wrapper);

        List<FriendVO> result = friends.stream().map(f -> {
            Long actualFriendId = f.getUserId().equals(userId) ? f.getFriendId() : f.getUserId();
            FriendVO vo = new FriendVO();
            vo.setFriendId(actualFriendId);
            vo.setRemark(f.getRemark());
            vo.setCreatedAt(f.getCreateTime() != null ? f.getCreateTime().format(DF) : null);
            return vo;
        }).collect(Collectors.toList());

        userProfileFallbackService.fillMissingFriendProfiles(result, friendIds);

        return result;
    }

    @Override
    @LogRecord(operation = "搜索好友", module = "好友管理")
    public List<FriendVO> searchFriends(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return getFriends(userId);
        }

        List<Long> friendIds = friendMapper.selectFriendIdsByKeyword(userId, keyword);
        if (friendIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .eq(Friend::getUserId, userId).in(Friend::getFriendId, friendIds)
                .or()
                .eq(Friend::getFriendId, userId).in(Friend::getUserId, friendIds)
        ).eq(Friend::getStatus, '1');

        List<Friend> friends = friendMapper.selectList(wrapper);

        List<FriendVO> result = friends.stream().map(f -> {
            Long actualFriendId = f.getUserId().equals(userId) ? f.getFriendId() : f.getUserId();
            FriendVO vo = new FriendVO();
            vo.setFriendId(actualFriendId);
            vo.setRemark(f.getRemark());
            vo.setCreatedAt(f.getCreateTime() != null ? f.getCreateTime().format(DF) : null);
            return vo;
        }).collect(Collectors.toList());

        userProfileFallbackService.fillMissingFriendProfiles(result, friendIds);

        Map<Long, String> emails = fetchEmailsByUserIds(friendIds);
        for (FriendVO friend : result) {
            String email = emails.get(friend.getFriendId());
            if (email != null) {
                friend.setEmail(email);
            }
        }

        return result;
    }

    private Map<Long, String> fetchEmailsByUserIds(List<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        try {
            String idParam = userIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            String response = restTemplate.getForObject(USER_SERVICE_URL + "by-ids?ids=" + idParam, String.class);
            var body = objectMapper.readValue(response, Map.class);
            var dataArray = (List<Map<String, Object>>) body.get("data");
            if (dataArray != null) {
                for (Map<String, Object> item : dataArray) {
                    Object uid = item.get("userId");
                    if (uid != null) {
                        result.put(Long.valueOf(uid.toString()), (String) item.get("email"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[聊天-好友搜索] 获取邮箱失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    @LogRecord(operation = "获取收到的申请", module = "好友管理")
    public List<FriendRequestVO> getIncomingRequests(Long userId) {
        LambdaQueryWrapper<FriendRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, '0')
                .orderByDesc(FriendRequest::getCreateTime);
        List<FriendRequest> requests = friendRequestMapper.selectList(wrapper);

        List<Long> fromUserIds = requests.stream()
                .map(FriendRequest::getFromUserId)
                .collect(Collectors.toList());

        Map<Long, String> nicknames = fetchNicknames(fromUserIds);

        return requests.stream().map(r -> {
            FriendRequestVO vo = toFriendRequestVO(r);
            vo.setFromNickname(nicknames.getOrDefault(r.getFromUserId(), "用户" + r.getFromUserId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @LogRecord(operation = "获取发出的申请", module = "好友管理")
    public List<FriendRequestVO> getOutgoingRequests(Long userId) {
        LambdaQueryWrapper<FriendRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendRequest::getFromUserId, userId)
                .eq(FriendRequest::getStatus, '0')
                .orderByDesc(FriendRequest::getCreateTime);
        List<FriendRequest> requests = friendRequestMapper.selectList(wrapper);

        List<Long> toUserIds = requests.stream()
                .map(FriendRequest::getToUserId)
                .collect(Collectors.toList());

        Map<Long, String> nicknames = fetchNicknames(toUserIds);

        return requests.stream().map(r -> {
            FriendRequestVO vo = toFriendRequestVO(r);
            vo.setFromNickname(nicknames.getOrDefault(r.getToUserId(), "用户" + r.getToUserId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @LogRecord(operation = "检查好友关系状态", module = "好友管理")
    public FriendStatusVO checkFriendStatus(Long targetUserId, Long currentUserId) {
        FriendStatusVO vo = new FriendStatusVO();
        vo.setIsFriend(friendMapper.isFriend(currentUserId, targetUserId));

        boolean pending = friendRequestMapper.existsPending(currentUserId, targetUserId);
        vo.setIsRequestSent(pending);

        LambdaQueryWrapper<FriendRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendRequest::getToUserId, currentUserId)
                .eq(FriendRequest::getFromUserId, targetUserId)
                .eq(FriendRequest::getStatus, '0');
        FriendRequest incoming = friendRequestMapper.selectOne(wrapper);
        vo.setIsRequestReceived(incoming != null);
        if (incoming != null) {
            vo.setRequestId(incoming.getId());
        }

        return vo;
    }

    @Override
    public Map<String, Object> getUserPublicProfile(Long userId) {
        try {
            String response = restTemplate.getForObject(USER_SERVICE_URL + userId, String.class);
            Map<String, Object> body = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return data;
        } catch (Exception e) {
            log.warn("获取用户公开资料失败: userId={}, error={}", userId, e.getMessage());
            return Map.of("userId", userId, "nickname", "用户" + userId);
        }
    }

    private FriendRequestVO toFriendRequestVO(FriendRequest r) {
        FriendRequestVO vo = new FriendRequestVO();
        vo.setRequestId(r.getId());
        vo.setFromUserId(r.getFromUserId());
        vo.setToUserId(r.getToUserId());
        vo.setMessage(r.getMessage());
        vo.setStatus(r.getStatus() == '0' ? "pending" : r.getStatus() == '1' ? "accepted" : "rejected");
        vo.setCreatedAt(r.getCreateTime() != null ? r.getCreateTime().format(DF) : null);
        return vo;
    }

    private Map<Long, String> fetchNicknames(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String response = restTemplate.getForObject(USER_SERVICE_URL + "by-ids?ids=" + ids, String.class);
            Map<String, Object> body = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) body.get("data");
            if (dataList == null) return Collections.emptyMap();
            Map<Long, String> result = new HashMap<>();
            for (Map<String, Object> item : dataList) {
                Object uid = item.get("userId");
                if (uid != null) {
                    result.put(Long.valueOf(uid.toString()), (String) item.get("nickname"));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("批量获取昵称失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @LogRecord(operation = "搜索用户", module = "好友管理")
    public PageResult<UserSearchResultVO> searchUsers(String keyword, int pageNum, int pageSize, Long currentUserId) {
        if (!StringUtils.hasText(keyword)) {
            return new PageResult<>(Collections.emptyList(), 0L, (long) pageNum, (long) pageSize);
        }

        try {
            int offset = (pageNum - 1) * pageSize;
            List<UserPublicProfileDto> records = userPublicMapper.searchUsers(keyword, pageNum, pageSize, offset, currentUserId);
            long total = userPublicMapper.countUsers(keyword, currentUserId);

            if (records == null || records.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), total, (long) pageNum, (long) pageSize);
            }

            List<UserSearchResultVO> results = new ArrayList<>(records.size());
            for (UserPublicProfileDto record : records) {
                UserSearchResultVO vo = new UserSearchResultVO();
                vo.setUserId(record.getUserId());
                vo.setNickname(record.getNickname());
                vo.setAvatar(record.getAvatar());
                vo.setEmail(record.getEmail());
                vo.setFriendStatus(checkFriendStatus(record.getUserId(), currentUserId));
                results.add(vo);
            }

            return new PageResult<>(results, total, (long) pageNum, (long) pageSize);

        } catch (Exception e) {
            log.warn("搜索用户失败: keyword={}, error={}", keyword, e.getMessage());
            throw new ChatException(ChatErrorCode.SEARCH_SERVICE_UNAVAILABLE);
        }
    }
}
