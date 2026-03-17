package com.gopair.fileservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO客户端配置
 *
 * 应用启动时自动检查并创建存储桶（私有桶策略）
 *
 * @author gopair
 */
@Slf4j
@Configuration
public class MinioConfig {

    private final MinioProperties minioProperties;

    public MinioConfig(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    /**
     * 注入MinIO客户端Bean，并在启动时确保存储桶存在
     */
    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        ensureBucketExists(client, minioProperties.getBucketName());
        return client;
    }

    /**
     * 确保桶存在，不存在则创建（私有桶）
     */
    private void ensureBucketExists(MinioClient client, String bucketName) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[文件服务] MinIO存储桶已创建 - 桶名: {}", bucketName);
            } else {
                log.info("[文件服务] MinIO存储桶已就绪 - 桶名: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("[文件服务] MinIO存储桶初始化失败 - 桶名: {}, 错误: {}", bucketName, e.getMessage(), e);
            throw new RuntimeException("MinIO桶初始化失败: " + e.getMessage(), e);
        }
    }
}
