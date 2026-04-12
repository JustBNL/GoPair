package com.gopair.fileservice.config;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * MinIO配置属性
 *
 * @author gopair
 */
@Validated
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "gopair.minio")
public class MinioProperties {

    /**
     * MinIO服务端点
     */
    @NotBlank
    private String endpoint = "http://localhost:9000";

    /**
     * 访问密钥
     */
    @NotBlank
    private String accessKey = "minioadmin";

    /**
     * 密钥
     */
    @NotBlank
    private String secretKey = "minioadmin";

    /**
     * 存储桶名称
     */
    @NotBlank
    private String bucketName = "gopair-files";

    /**
     * Presigned URL有效期（秒），默认24小时，与房间生命周期对齐
     */
    @Positive
    private long presignedUrlExpireSeconds = 86400L;
}
