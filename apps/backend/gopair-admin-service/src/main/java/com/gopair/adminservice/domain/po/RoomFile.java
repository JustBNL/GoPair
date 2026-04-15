package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间文件实体类，对应数据库room_file表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room_file")
public class RoomFile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    private Long roomId;

    private Long uploaderId;

    private String uploaderNickname;

    private String fileName;

    private String filePath;

    private String thumbnailPath;

    private Long fileSize;

    private String fileType;

    private String contentType;

    private Integer downloadCount;

    private LocalDateTime uploadTime;
}
