package com.gopair.fileservice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 头像上传结果
 *
 * @author gopair
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarVO {

    /**
     * 压缩图永久直链（用于前端展示）
     */
    private String avatarUrl;

    /**
     * 原图永久直链（存入用户表，用于下载）
     */
    private String avatarOriginalUrl;
}
