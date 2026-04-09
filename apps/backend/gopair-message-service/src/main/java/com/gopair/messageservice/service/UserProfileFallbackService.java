package com.gopair.messageservice.service;

/**
 * 用户资料降级服务接口
 *
 * <h2>降级策略</h2>
 * <ol>
 * <li><b>主路径</b>：各查询方法通过 SQL JOIN user 表直接读出昵称/头像（已内嵌于 Mapper XML）。</li>
 * <li><b>降级补全</b>：若 JOIN 结果中仍有行的昵称为空，将缺失 userId 聚合后批量调 user-service，
 *     再将结果合并回原查询结果，使最终返回给前端的每条消息都有发送者昵称/头像。</li>
 * </ol>
 *
 * <h2>user-service 迁库后的表现</h2>
 * 若 user 表迁至独立库导致 JOIN 结果全为空，各查询方法降级至 user-service HTTP，
 * 最终仍能返回昵称/头像，只是多一次网络开销。若 user-service 也连不上，则保持降级文案。
 */
public interface UserProfileFallbackService {

    /**
     * 补全消息列表中缺失的用户资料
     *
     * @param messageList  消息列表（可修改，方法内直接设值）
     * @param replyToIds   需要补全的被回复消息 ID 列表（来自 messageList 中 replyToId 非空且昵称为空的消息）
     */
    void fillMissingProfiles(java.util.List<com.gopair.messageservice.domain.vo.MessageVO> messageList,
                             java.util.List<Long> replyToIds);
}
