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

    /** 房间待移除成员 Hash Key，field=userId, value=leaveType。格式：room:{roomId}:pending_removal */
    public static final String KEY_ROOM_PENDING_REMOVAL = "room:%d:pending_removal";

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
    public static String pendingRemovalKey(Long roomId) { return String.format(KEY_ROOM_PENDING_REMOVAL, roomId); }

    // ====================================================================
    // 房间状态（room.status）
    // ====================================================================

    /** 活跃（正常使用中） */
    public static final int STATUS_ACTIVE = 0;
    /** 已关闭（房主关闭 或 人数归零自动关闭） */
    public static final int STATUS_CLOSED = 1;
    /** 已过期（定时任务触发，只读，可续期恢复为 ACTIVE） */
    public static final int STATUS_EXPIRED = 2;
    /** 已归档（终态，资源已清理，不再现于房间列表） */
    public static final int STATUS_ARCHIVED = 3;
    /** 已禁用（管理员操作，房间不可读写，语音关闭，但不禁踢用户） */
    public static final int STATUS_DISABLED = 4;

    // ====================================================================
    // 定时清理阈值（小时）
    // ====================================================================

    /** 过期房间转为已关闭的前置阈值（小时），expire_time < now - N 小时后触发系统关闭 */
    public static final int EXPIRED_TO_CLOSED_HOURS = 24;

    /** 禁用房间转为已关闭的前置阈值（小时），disabled_time < now - N 小时后触发系统关闭 */
    public static final int DISABLED_TO_CLOSED_HOURS = 24;

    // ====================================================================
    // 续期时长选项（小时）
    // ====================================================================

    /** 续期可选时长 — 1小时 */
    public static final int RENEW_HOURS_1   = 1;
    /** 续期可选时长 — 24小时 */
    public static final int RENEW_HOURS_24 = 24;
    /** 续期可选时长 — 72小时 */
    public static final int RENEW_HOURS_72 = 72;
    /** 续期可选时长 — 168小时（7天） */
    public static final int RENEW_HOURS_168 = 168;
    /** 续期默认时长（小时） */
    public static final int DEFAULT_RENEW_HOURS = 24;

    /** 重新开启默认过期时长（小时），与续期共用同一组选项 */
    public static final int DEFAULT_REOPEN_HOURS = 24;

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
    public static final int ROLE_OWNER  = 1;

    // ====================================================================
    // join token 结果值后缀
    // ====================================================================

    /** 加入成功，完整格式：{roomId}:{userId}:JOINED */
    public static final String JOIN_RESULT_JOINED = "JOINED";
    /** 加入失败，完整格式：{roomId}:{userId}:FAILED */
    public static final String JOIN_RESULT_FAILED = "FAILED";

    // ====================================================================
    // 内部私有常量（不对外暴露，仅本类方法引用）
    // ====================================================================

    /** 元数据初始化时，未抢到锁的轮询重试次数 */
    public static final int META_INIT_RETRY_COUNT = 3;

    /** 元数据初始化时，每次轮询等待间隔（毫秒） */
    public static final int META_INIT_RETRY_INTERVAL_MS = 50;

    /** 元数据初始化分布式锁的 TTL（秒），防止进程崩溃死锁 */
    public static final int META_INIT_LOCK_TTL_SECONDS = 5;

    // ====================================================================
    // HTTP 降级与批处理配置
    // ====================================================================

    /** 用户服务 HTTP 端点前缀 */
    public static final String USER_SERVICE_URL = "http://user-service/user/";

    /** 批量查询用户资料的批量上限 */
    public static final int USER_BATCH_MAX_IDS = 200;

    /** 批量查询失败时单条兜底拉取上限 */
    public static final int USER_SINGLE_FETCH_FALLBACK_MAX = 64;

    /** 分页默认 pageSize */
    public static final int DEFAULT_PAGE_SIZE = 10;

    // ====================================================================
    // 资源清理批处理参数
    // ====================================================================

    /** 关闭房间资源清理任务 — 每批处理上限 */
    public static final int CLEANUP_BATCH_SIZE = 100;

    /** 关闭房间资源清理任务 — 最大循环轮次 */
    public static final int CLEANUP_MAX_ITERATIONS = 10;

    /** 关闭房间待清理阈值（小时），closed_time < now - N 小时视为可清理 */
    public static final int CLEANUP_THRESHOLD_HOURS = 24;

    // ====================================================================
    // 服务间 HTTP 端点
    // ====================================================================

    /** 文件服务清理接口前缀 */
    public static final String FILE_SERVICE_URL = "http://file-service/file/room/";

    /** 消息服务清理接口前缀 */
    public static final String MESSAGE_SERVICE_URL = "http://message-service/message/room/";

    /** 语音通话服务接口前缀 */
    public static final String VOICE_SERVICE_URL = "http://voice-service/voice/room/";

    /** 语音通话服务优雅终止接口前缀（广播 call_end） */
    public static final String VOICE_SERVICE_END_ALL_URL = "http://voice-service/voice/room/";

    // ====================================================================
    // 布尔标志语义常量
    // ====================================================================
    public static final int PASSWORD_VISIBLE = 1;

    /** 密码可见性 — 对成员隐藏 */
    public static final int PASSWORD_HIDDEN = 0;

    /** 乐观锁初始版本号 */
    public static final int INITIAL_VERSION = 0;
}
