package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.core.PageResult;
import com.gopair.common.util.BeanCopyUtils;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.dto.UserPublicProfileDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.mapper.UserPublicMapper;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 房间成员服务实现类
 * 
 * @author gopair
 */
@Slf4j
@Service
public class RoomMemberServiceImpl extends ServiceImpl<RoomMemberMapper, RoomMember> implements RoomMemberService {

    private final RoomMemberMapper roomMemberMapper;
    private final RoomMapper roomMapper;
    private final UserPublicMapper userPublicMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 用户服务内部调用地址（Nacos 服务名） */
    private static final String USER_SERVICE_URL = "http://user-service/user/";

    /** 单次批量查询用户资料上限，与用户服务 {@code listUsersByIds} 对齐 */
    private static final int USER_BATCH_MAX_IDS = 200;

    /** 批量解析失败或旧版无 by-ids 时，对仍缺资料的成员逐个补拉，避免整页显示「用户{id}」 */
    private static final int USER_SINGLE_FETCH_FALLBACK_MAX = 64;

    public RoomMemberServiceImpl(RoomMemberMapper roomMemberMapper, RoomMapper roomMapper,
                                  UserPublicMapper userPublicMapper,
                                  RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.roomMemberMapper = roomMemberMapper;
        this.roomMapper = roomMapper;
        this.userPublicMapper = userPublicMapper;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 将用户加入指定房间。
     *
     * * [核心策略]
     * - 防重入房：先查 room_member 判重；DB 唯一键（roomId + userId）兜底，极端并发下唯一键冲突不抛异常。
     *
     * * [执行链路]
     * 1. 幂等判断：查 room_member，若已在房间则直接返回 false。
     * 2. 插入记录：写入 room_member（包含角色、加入时间、最后活跃时间）。
     * 3. 结果判定：insert 影响行数 > 0 → true；唯一键冲突 → false。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @param role   角色（0=普通成员；null 时默认为 0）
     * @return true=新增成功；false=已在房间或插入失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "添加房间成员", module = "成员管理")
    public boolean addMember(Long roomId, Long userId, Integer role) {
        // 检查是否已经在房间中
        if (isMemberInRoom(roomId, userId)) {
            return false;
        }

        // 创建房间成员记录
        RoomMember roomMember = new RoomMember();
        roomMember.setRoomId(roomId);
        roomMember.setUserId(userId);
        roomMember.setRole(role != null ? role : 0);
        roomMember.setStatus(0); // 在线状态
        roomMember.setJoinTime(LocalDateTime.now());
        roomMember.setLastActiveTime(LocalDateTime.now());

        int insertRows = roomMemberMapper.insert(roomMember);
        
        if (insertRows > 0) {
            log.info("[房间服务] 用户{}加入房间{}成功，角色={}", userId, roomId, role);
        }
        
        return insertRows > 0;
    }

    /**
     * 将用户从指定房间移除（物理删除）。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @return true=删除成功；false=成员不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "移除房间成员", module = "成员管理", includeResult = true)
    public boolean removeMember(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);

        int deleteRows = roomMemberMapper.delete(queryWrapper);
        
        if (deleteRows > 0) {
            log.info("[房间服务] 用户{}离开房间{}成功", userId, roomId);
        }
        
        return deleteRows > 0;
    }

    /**
     * 查询用户是否在指定房间中。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @return true=在房间中；false=不在或任一参数为 null
     */
    @Override
    @LogRecord(operation = "查询成员是否在房间", module = "成员管理")
    public boolean isMemberInRoom(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);
        
        RoomMember member = roomMemberMapper.selectOne(queryWrapper);
        return member != null;
    }

    /**
     * 查询房间内全部成员并拼装展示用 VO（含昵称、头像、是否房主）。
     *
     * * [核心策略]
     * - 降级链路：同库 IN 查询（主）→ HTTP 批量 → HTTP 单条（兜底），确保任意成员数量下方法不会无限循环（批量上限 200 + 单条上限 64）。
     * - 兼容字段名：批量接口 JSON 解析时兼容 userId vs id 两种字段名。
     *
     * * [执行链路]
     * 1. 参数校验：roomId 为 null 则抛 RoomException（ROOM_NOT_FOUND）。
     * 2. 查库：按 roomId 查 room_member，按加入时间倒序。
     * 3. 收集 userId：提取所有成员 userId 去重，截取至多 200 个。
     * 4. 获取用户资料：走三层降级链路填充 nickname/avatar；仍未命中则兜底为「用户{userId}」。
     * 5. 组装 VO：BeanCopyUtils 拷贝字段；以资料填 nickname/avatar；以 role 是否等于房主判定 isOwner。
     *
     * @param roomId 房间 ID
     * @return 成员 VO 列表（不含已离室成员）
     */
    @Override
    @LogRecord(operation = "查询房间成员列表", module = "成员管理")
    public List<RoomMemberVO> getRoomMembers(Long roomId) {
        if (roomId == null) {
            throw new com.gopair.roomservice.exception.RoomException(
                com.gopair.roomservice.enums.RoomErrorCode.ROOM_NOT_FOUND);
        }

        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .orderByDesc(RoomMember::getJoinTime);

        List<RoomMember> members = roomMemberMapper.selectList(queryWrapper);

        List<Long> userIds = members.stream()
                .map(RoomMember::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserProfileBrief> profiles = fetchProfilesFromUserService(userIds);

        return members.stream().map(member -> {
            RoomMemberVO memberVO = BeanCopyUtils.copyBean(member, RoomMemberVO.class);
            Long uid = member.getUserId();
            UserProfileBrief p = uid != null ? profiles.get(uid) : null;
            String nickname = p != null && StringUtils.hasText(p.nickname()) ? p.nickname() : null;
            if (!StringUtils.hasText(nickname) && uid != null) {
                nickname = "用户" + uid;
            }
            memberVO.setNickname(nickname);
            memberVO.setAvatar(p != null && StringUtils.hasText(p.avatar()) ? p.avatar() : null);
            memberVO.setIsOwner(member.getRole() != null && member.getRole() == RoomConst.ROLE_OWNER);
            return memberVO;
        }).collect(Collectors.toList());
    }

    /**
     * 三层降级入口：同库 IN 查询（主）→ HTTP 批量 → HTTP 单条兜底。
     * 与 message 服务的降级策略保持一致。
     *
     * @param userIds 待查询的 userId 列表
     * @return userId → 昵称/头像映射；可能不包含所有 userId（表示该用户在库中不存在）
     */
    private Map<Long, UserProfileBrief> fetchProfilesFromUserService(List<Long> userIds) {
        Map<Long, UserProfileBrief> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        List<Long> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(USER_BATCH_MAX_IDS)
                .collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return map;
        }

        loadProfilesFromSharedUserTable(distinct, map);
        //数量少了
        if (map.size() < distinct.size()) {
            log.info("[房间成员] 从 user 表读取公开资料不足,尝试用户服务 HTTP 批量接口");
            mergeProfilesFromUserServiceHttpBatch(distinct, map);
        }
        //兜底
        fillMissingProfilesFromUserService(userIds, map);
        return map;
    }

    /**
     * 路径一（最快）：IN 查询共享库 user 表。异常仅打 warn 日志，不阻断流程，自动降级到路径二。
     */
    private void loadProfilesFromSharedUserTable(List<Long> distinct, Map<Long, UserProfileBrief> map) {
        try {
            List<UserPublicProfileDto> rows = userPublicMapper.selectByUserIds(distinct);
            if (rows == null) {
                return;
            }
            for (UserPublicProfileDto row : rows) {
                if (row == null || row.getUserId() == null) {
                    continue;
                }
                putProfileMerge(map, row.getUserId(),
                        new UserProfileBrief(row.getNickname(), row.getAvatar()));
            }
        } catch (Exception e) {
            log.warn("[房间成员] 从共享库 user 表读取公开资料失败（将尝试用户服务 HTTP）: {}", e.getMessage());
        }
    }

    /**
     * 路径二（降级）：HTTP 调 user-service /by-ids 批量接口。仅在路径一结果数不足时触发,单体数据库一般不会触发,保留后续拓展空间
     */
    private void mergeProfilesFromUserServiceHttpBatch(List<Long> distinct, Map<Long, UserProfileBrief> map) {
        String idParam = distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
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
                long uid = parseUserIdFromJson(item.path("userId"));
                if (uid < 0) {
                    uid = parseUserIdFromJson(item.path("id"));
                }
                if (uid < 0) {
                    continue;
                }
                JsonNode nickNode = item.path("nickname");
                JsonNode avatarNode = item.path("avatar");
                String nick = nickNode.isMissingNode() || nickNode.isNull() ? null : nickNode.asText();
                String avatar = avatarNode.isMissingNode() || avatarNode.isNull() ? null : avatarNode.asText();
                putProfileMerge(map, uid, new UserProfileBrief(nick, avatar));
            }
        } catch (Exception e) {
            log.warn("[房间成员] 用户服务批量拉取资料失败: {}", e.getMessage());
        }
    }

    /**
     * 新值与旧值取非空者合并（不因降级路径数据不完整而覆盖已查得的完整资料）。
     */
    private static void putProfileMerge(Map<Long, UserProfileBrief> map, long uid, UserProfileBrief incoming) {
        UserProfileBrief old = map.get(uid);
        if (old == null) {
            map.put(uid, incoming);
            return;
        }
        //看看之前有没有值,没有值就重新创建对象赋值
        String nick = StringUtils.hasText(incoming.nickname()) ? incoming.nickname() : old.nickname();
        String av = StringUtils.hasText(incoming.avatar()) ? incoming.avatar() : old.avatar();
        map.put(uid, new UserProfileBrief(nick, av));
    }

    /**
     * 兼容 JSON 中 userId 为数字或字符串；无法解析时返回负数。
     */
    private static long parseUserIdFromJson(JsonNode idNode) {
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            return -1L;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.longValue();
        }
        if (idNode.isNumber()) {
            return idNode.longValue();
        }
        if (idNode.isTextual()) {
            try {
                return Long.parseLong(idNode.asText().trim());
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return -1L;
    }

    /**
     * 路径三（最终兜底）：对路径一、二仍未命中的 userId，逐个调 GET /user/{id}（上限 64 次，防止 HTTP 过载）。
     * 仅补拉 nickname 仍为空的用户，已有昵称的跳过。
     * 作为检查
     */
    private void fillMissingProfilesFromUserService(List<Long> userIds, Map<Long, UserProfileBrief> map) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        int budget = USER_SINGLE_FETCH_FALLBACK_MAX;
        for (Long uid : distinct) {
            UserProfileBrief existing = map.get(uid);
            if (existing != null && StringUtils.hasText(existing.nickname())) {
                continue;
            }
            if (budget-- <= 0) {
                log.warn("[房间成员] 单个补拉用户资料已达上限{}，仍有成员无用户服务资料", USER_SINGLE_FETCH_FALLBACK_MAX);
                break;
            }
            fetchOneUserProfile(uid).ifPresent(p -> {
                if (existing == null) {
                    map.put(uid, p);
                    return;
                }
                String nick = StringUtils.hasText(p.nickname()) ? p.nickname() : existing.nickname();
                String av = StringUtils.hasText(p.avatar()) ? p.avatar() : existing.avatar();
                map.put(uid, new UserProfileBrief(nick, av));
                log.info("[房间成员] 补充单个拉取用户{}资料成功", uid);
            });
        }
    }

    /**
     * 单条查询：调 GET /user/{userId} 获取用户公开资料。仅用于路径三（最终兜底）。
     *
     * @param userId 用户 ID
     * @return 有数据时返回 ProfileEntry；无数据或解析失败时返回空 Optional
     */
    private Optional<UserProfileBrief> fetchOneUserProfile(Long userId) {
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
            JsonNode nickNode = data.path("nickname");
            JsonNode avatarNode = data.path("avatar");
            String nick = nickNode.isMissingNode() || nickNode.isNull() ? null : nickNode.asText();
            String avatar = avatarNode.isMissingNode() || avatarNode.isNull() ? null : avatarNode.asText();
            return Optional.of(new UserProfileBrief(nick, avatar));
        } catch (Exception e) {
            log.warn("[房间成员] 单个拉取用户{}资料失败: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 用户公开资料传输对象（昵称 + 头像），作为三层降级链路的统一中间格式。
     * 使用 record 保证非空合并逻辑集中在 putProfileMerge 中。
     */
    private record UserProfileBrief(String nickname, String avatar) {}

    /**
     * 查询指定用户参与的房间列表（分页），支持按状态过滤。
     *
     * * [核心策略]
     * - 两段式分页：过滤条件（status）在 room 表而非 room_member 表，必须先过滤再分页；仅查询当页数据，避免一次查出大量房间后丢弃。
     *
     * * [执行链路]
     * 1. 查成员表：以 userId 查 room_member，提取所有关联 roomId（去重）；无记录则直接返回空结果。
     * 2. 过滤房间状态：IN 查询 room 表，按 status 过滤。
     *    - status != null → 精确匹配。
     *    - status == null 且 includeHistory != true → 默认仅返回 status=0 活跃房间。
     *    - includeHistory == true → 返回所有状态。
     * 3. 内存排序：按房间创建时间倒序。
     * 4. Java 侧分页：根据 pageNum/pageSize 截取当页 roomId 列表。
     * 5. 查房间详情：IN 查询当页 roomId，按截取顺序转换为 RoomVO 并返回。
     *
     * @param userId 用户 ID
     * @param query  查询条件（含分页参数 pageNum/pageSize、status、includeHistory）
     * @return 房间分页结果（total=过滤后总房间数）
     */
    @Override
    @LogRecord(operation = "查询用户参与的房间", module = "成员管理")
    public PageResult<RoomVO> getUserRooms(Long userId, RoomQueryDto query) {
        LambdaQueryWrapper<RoomMember> allMemberQuery = new LambdaQueryWrapper<>();
        allMemberQuery.eq(RoomMember::getUserId, userId)
                .select(RoomMember::getRoomId);
        List<Long> allRoomIds = roomMemberMapper.selectList(allMemberQuery).stream()
                .map(RoomMember::getRoomId)
                .distinct()
                .collect(Collectors.toList());

        if (allRoomIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L, (long) query.getPageNum(), (long) query.getPageSize());
        }

        // 2. 按房间状态过滤（基于 Room 表）
        LambdaQueryWrapper<Room> statusQuery = new LambdaQueryWrapper<>();
        statusQuery.in(Room::getRoomId, allRoomIds);
        applyRoomStatusFilter(statusQuery, query);
        List<Room> filteredRooms = roomMapper.selectList(statusQuery);
        long realTotal = filteredRooms.size();

        if (realTotal == 0) {
            return new PageResult<>(List.of(), 0L, (long) query.getPageNum(), (long) query.getPageSize());
        }

        // 3. 构建过滤后的 roomId 有序列表（按创建时间倒序），用于分页
        List<Long> filteredRoomIds = filteredRooms.stream()
                .sorted((a, b) -> {
                    if (a.getCreateTime() != null && b.getCreateTime() != null) {
                        return b.getCreateTime().compareTo(a.getCreateTime());
                    }
                    return 0;
                })
                .map(Room::getRoomId)
                .collect(Collectors.toList());

        // 4. 按房间维度分页（计算起始位置和结束位置）
        long total = filteredRoomIds.size();
        int pageNum = query.getPageNum();
        int pageSize = query.getPageSize();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredRoomIds.size());

        if (fromIndex >= total) {
            return new PageResult<>(List.of(), realTotal, (long) pageNum, (long) pageSize);
        }

        // 5. 获取当前页的 roomId 并查询房间详情
        List<Long> pageRoomIds = filteredRoomIds.subList(fromIndex, toIndex);
        LambdaQueryWrapper<Room> roomQueryWrapper = new LambdaQueryWrapper<>();
        roomQueryWrapper.in(Room::getRoomId, pageRoomIds);
        List<Room> pageRooms = roomMapper.selectList(roomQueryWrapper);

        // 6. 按 pageRoomIds 顺序转换（保持分页顺序）
        Map<Long, Room> roomMap = pageRooms.stream()
                .collect(Collectors.toMap(Room::getRoomId, room -> room));
        List<RoomVO> roomVOList = pageRoomIds.stream()
                .map(roomMap::get)
                .filter(room -> room != null)
                .map(room -> BeanCopyUtils.copyBean(room, RoomVO.class))
                .collect(Collectors.toList());

        return new PageResult<>(roomVOList, realTotal, (long) pageNum, (long) pageSize);
    }

    /**
     * 按房间状态过滤：精确匹配 status（若传）或不传时默认仅返回活跃（status=0）的房间。
     */
    private void applyRoomStatusFilter(LambdaQueryWrapper<Room> roomQueryWrapper, RoomQueryDto query) {
        if (query.getStatus() != null) {
            roomQueryWrapper.eq(Room::getStatus, query.getStatus());
            return;
        }
        if (!Boolean.TRUE.equals(query.getIncludeHistory())) {
            roomQueryWrapper.eq(Room::getStatus, 0);
        }
    }

    /**
     * 删除指定房间的所有成员记录（通常在房间永久销毁时调用）。
     *
     * * [核心策略]
     * - 无并发防护：按 roomId 批量删除，天然幂等（重复调用效果相同）。
     *
     * * [执行链路]
     * 1. 批量删除：按 roomId 从 room_member 表删除所有该房间成员记录。
     * 2. 结果判定：影响行数 > 0 → true；原本无成员 → false。
     *
     * @param roomId 房间 ID
     * @return true=有成员被删除；false=房间无成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "删除房间所有成员", module = "成员管理", includeResult = true)
    public boolean deleteByRoomId(Long roomId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId);
        
        int deleteRows = roomMemberMapper.delete(queryWrapper);
        
        if (deleteRows > 0) {
            log.info("[房间服务] 房间{}的所有成员已删除", roomId);
        }
        
        return deleteRows > 0;
    }

    /**
     * 更新用户在指定房间的最后活跃时间。
     *
     * * [核心策略]
     * - 无特殊约束：按 roomId + userId 条件更新，成员不存在时返回 false。
     *
     * * [执行链路]
     * 1. 条件更新：按 roomId + userId 更新 room_member 的 last_active_time 为当前时间。
     * 2. 结果判定：影响行数 > 0 → true；成员不存在 → false。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @return true=更新成功；false=成员不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "更新最后活跃时间", module = "成员管理", includeResult = true)
    public boolean updateLastActiveTime(Long roomId, Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getRoomId, roomId)
                   .eq(RoomMember::getUserId, userId);

        RoomMember member = new RoomMember();
        member.setLastActiveTime(LocalDateTime.now());

        int updateRows = roomMemberMapper.update(member, queryWrapper);

        return updateRows > 0;
    }

    /**
     * 批量将用户在所有房间的在线状态更新为离线。
     *
     * <h2>完整流程</h2>
     * <ol>
     *   <li><b>条件更新</b>：按 userId 且 status = 0 更新 room_member 的 status 为 1。</li>
     *   <li><b>幂等保证</b>：WHERE status = 0，已离线用户不会被重复更新。</li>
     *   <li><b>返回影响行数</b>：用户不在任何房间或已全部离线时返回 0。</li>
     * </ol>
     *
     * <h2>触发时机</h2>
     * 由 UserOfflineConsumer 消费 MQ 消息后调用，用户所有 WebSocket 连接断开时触发。
     *
     * @param userId 用户ID
     * @return 影响的行数（更新为离线的成员记录数）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(operation = "批量更新成员状态为离线", module = "成员管理")
    public int updateStatusToOffline(Long userId) {
        LambdaQueryWrapper<RoomMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomMember::getUserId, userId)
                   .eq(RoomMember::getStatus, RoomConst.MEMBER_STATUS_ONLINE);

        RoomMember updates = new RoomMember();
        updates.setStatus(RoomConst.MEMBER_STATUS_OFFLINE);

        int updated = roomMemberMapper.update(updates, queryWrapper);
        if (updated > 0) {
            log.info("[房间服务] 用户{}在{}个房间的在线状态已更新为离线", userId, updated);
        }
        return updated;
    }
} 