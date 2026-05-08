package com.gopair.adminservice.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理后台文件列表 VO，承载 JOIN 后的展示字段。
 */
@Data
public class FileVO {

    // === 文件自身字段 ===
    private Long fileId;
    private Long roomId;
    private Long uploaderId;
    private String uploaderNickname;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Long thumbnailSize;
    private String fileType;
    private String contentType;
    private Integer downloadCount;
    private LocalDateTime uploadTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // === JOIN 来的关联字段 ===
    /** 所属房间名称 */
    private String roomName;
}
