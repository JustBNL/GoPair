package com.gopair.roomservice.constant;

/**
 * 房间服务统一常量类
 *
 * <p>职责范围：
 * <ul>
 *   <li>Redis Key 模板与 Hash 字段名（防止多处硬编码产生静默 bug）</li>
 *   <li>房间状态、密码模式、成员角色等业务枚举值</li>
 *   <li>join token 结果后缀字符串</li>
 * </ul>
 *
 * <p>不包含以下内容（各有其管理机制，不在此重复）：
 * <ul>
 *   <li>错误码 —— 见 {@link com.gopair.roomservice.enums.RoomErrorCode}</li>
 *   <li>MQ Exchange/Queue/RoutingKey —— 由 application.yml + @Value 管理</li>
 *   <li>默认成员数/过期时长 —— 由 {@link com.gopair.roomservice.config.RoomConfig} 管理</li>
 *   <li>AES 主密钥 —— 由 {@link com.gopair.roomservice.config.RoomConfig} 管理</li>
 * </ul>
 *
 * @author gopair
 */
public final class RoomConst {

    private RoomConst() {}

    // ====================================================================
    // Redis Key 模板
    // ====================================================================

    /** 房间元数据 Hash Key，字段见下方 FIELD_* 常量。格式：room:{roomId}:meta */
    public static final String KEY_ROOM_META = "room:%d:meta";

    /** 房间已确认成员 Set Key，元素为 userId 字符串。格式：room:{roomId}:members */
    public static final String KEY_ROOM_MEMBERS = "room:%d:members";

    /** 房间预占 Hash Key，field=userId, value=joinToken。格式：room:{roomId}:pending */
    public static final String KEY_ROOM_PENDING = "room:%d:pending";

    /** 加入结果 String Key，value 格式见 JOIN_RESULT_* 常量。格式：join:{token} */
    public static final String KEY_JOIN_TOKEN = "join:%s";

    /** SCAN 扫描 pending key 使用的匹配模式 */
    public static final String PATTERN_ROOM_PENDING = "room:*:pending";

    // ====================================================================
    // Redis Meta Hash 字段名
    // ====================================================================

    /** 最大成员数 */
    public static final String FIELD_MAX           = "max";
    /** 已确认（正式入房）成员数 */
    public static final String FIELD_CONFIRMED     = "confirmed";
    /** 预占（处理中）计数 */
    public static final String FIELD_RESERVED      = "reserved";
    /** 房间状态，值见 STATUS_* 常量 */
    public static final String FIELD_STATUS        = "status";
    /** 房间过期时间戳（毫秒） */
    public static final String FIELD_EXPIRE_AT     = "expireAt";
    /** 密码模式，值见 PASSWORD_MODE_* 常量 */
    public static final String FIELD_PASSWORD_MODE = "passwordMode";
    /** 房主用户 ID */
    public static final String FIELD_OWNER_ID      = "ownerId";

    // ====================================================================
    // Redis Key 构建工具方法
    // ====================================================================

    public static String metaKey(Long roomId)         { return String.format(KEY_ROOM_META,    roomId); }
    public static String membersKey(Long roomId)      { return String.format(KEY_ROOM_MEMBERS, roomId); }
    public static String pendingKey(Long roomId)      { return String.format(KEY_ROOM_PENDING, roomId); }
    public static String joinTokenKey(String token)   { return String.format(KEY_JOIN_TOKEN,   token);  }
    public static String metaInitLockKey(Long roomId) { return String.format("lock:room:meta:%d", roomId); }

    // ====================================================================
    // 房间状态（room.status）
    // ====================================================================

    /** 活跃（正常使用中） */
    public static final int STATUS_ACTIVE = 0;
    /** 已关闭 */
    public static final int STATUS_CLOSED = 1;

    // ====================================================================
    // 密码模式（room.password_mode）
    // ====================================================================

    /** 无密码 */
    public static final int PASSWORD_MODE_NONE  = 0;
    /** AES 固定密码（可解密展示） */
    public static final int PASSWORD_MODE_FIXED = 1;
    /** TOTP 动态令牌（RFC 6238 变体） */
    public static final int PASSWORD_MODE_TOTP  = 2;

    // ====================================================================
    // 成员状态（room_member.status）
    // ====================================================================

    /** 在线 */
    public static final int MEMBER_STATUS_ONLINE = 0;
    /** 离线 */
    public static final int MEMBER_STATUS_OFFLINE = 1;

    // ====================================================================
    // 成员角色（room_member.role）
    // ====================================================================

    /** 普通成员 */
    public static final int ROLE_MEMBER = 0;
    /** 房主 */
    public static final int ROLE_OWNER  = 2;

    // ====================================================================
    // join token 结果值后缀
    // ====================================================================

    /** 加入成功，完整格式：{roomId}:{userId}:JOINED */
    public static final String JOIN_RESULT_JOINED = "JOINED";
    /** 加入失败，完整格式：{roomId}:{userId}:FAILED */
    public static final String JOIN_RESULT_FAILED = "FAILED";
}
