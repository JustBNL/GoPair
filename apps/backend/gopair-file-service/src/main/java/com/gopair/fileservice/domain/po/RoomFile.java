package com.gopair.fileservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间文件持久化对象
 *
 * 对应数据库表 room_file，存储文件元数据，
 * 实际文件内容存储于MinIO对象存储服务。
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room_file")
public class RoomFile extends BaseEntity {

    /**
     * 文件ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long fileId;

    /**
     * 所属房间ID
     */
    private Long roomId;

    /**
     * 上传者用户ID
     */
    private Long uploaderId;

    /**
     * 上传者昵称（冗余字段，避免关联查询）
     */
    private String uploaderNickname;

    /**
     * 文件原始名称
     */
    private String fileName;

    /**
     * MinIO中的对象Key（原图路径）
     * 格式：room/{roomId}/original/{uuid}.{ext}
     */
    private String filePath;

    /**
     * MinIO中缩略图的对象Key（仅图片类型有值）
     * 格式：room/{roomId}/thumbnail/{uuid}_thumb.{ext}
     */
    private String thumbnailPath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件扩展名（小写，如 jpg/pdf/zip）
     */
    private String fileType;

    /**
     * MIME类型（如 image/jpeg）
     */
    private String contentType;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;
}
