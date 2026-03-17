package com.gopair.fileservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息视图对象
 *
 * 返回给前端的文件信息，包含MinIO动态生成的Presigned URL。
 * URL在每次查询时动态生成，有效期与房间生命周期一致（默认24小时）。
 *
 * @author gopair
 */
@Data
public class FileVO {

    /**
     * 文件ID
     */
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
     * 上传者昵称
     */
    private String uploaderNickname;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件大小格式化字符串（如 1.5 MB）
     */
    private String fileSizeFormatted;

    /**
     * 文件类型（扩展名小写，如 jpg/pdf）
     */
    private String fileType;

    /**
     * MIME类型
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

    /**
     * 下载URL（原图Presigned URL，有时效性）
     */
    private String downloadUrl;

    /**
     * 预览URL（图片为缩略图Presigned URL，其他类型同downloadUrl）
     */
    private String previewUrl;

    /**
     * 是否支持在线预览
     */
    private boolean previewable;

    /**
     * 前端图标类型标识（image/pdf/word/excel/video/audio/archive/text/default）
     */
    private String iconType;

    /**
     * 格式化文件大小工具方法
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = (int) (Math.log(bytes) / Math.log(1024));
        i = Math.min(i, units.length - 1);
        return String.format("%.2f %s", bytes / Math.pow(1024, i), units[i]);
    }

    /**
     * 判断文件是否可预览
     */
    public static boolean isPreviewable(String fileType) {
        if (fileType == null) return false;
        String type = fileType.toLowerCase();
        return type.matches("jpg|jpeg|png|gif|bmp|webp|svg|pdf|txt|md|json|xml|csv|mp3|wav|aac|ogg|mp4|webm|mov");
    }

    /**
     * 获取前端图标类型
     */
    public static String resolveIconType(String fileType) {
        if (fileType == null) return "default";
        String type = fileType.toLowerCase();
        if (type.matches("jpg|jpeg|png|gif|bmp|webp|svg")) return "image";
        if (type.equals("pdf")) return "pdf";
        if (type.matches("doc|docx")) return "word";
        if (type.matches("xls|xlsx")) return "excel";
        if (type.matches("ppt|pptx")) return "powerpoint";
        if (type.matches("txt|md|json|xml|csv")) return "text";
        if (type.matches("mp4|avi|mov|wmv|flv|mkv|webm")) return "video";
        if (type.matches("mp3|wav|flac|aac|ogg|wma")) return "audio";
        if (type.matches("zip|rar|7z|tar|gz|bz2")) return "archive";
        return "default";
    }
}
