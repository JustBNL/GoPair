package com.gopair.fileservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO配置属性
 *
 * @author gopair
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.minio")
public class MinioProperties {

    /**
     * MinIO服务端点
     */
    private String endpoint = "http://localhost:9000";

    /**
     * 访问密钥
     */
    private String accessKey = "minioadmin";

    /**
     * 密钥
     */
    private String secretKey = "minioadmin";

    /**
     * 存储桶名称
     */
    private String bucketName = "gopair-files";

    /**
     * Presigned URL有效期（秒），默认24小时，与房间生命周期对齐
     */
    private long presignedUrlExpireSeconds = 86400L;
}
