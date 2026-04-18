package com.gopair.fileservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 *
 * 提供基于MinIO的文件上传、存储、下载及图片缩略图生成能力
 *
 * @author gopair
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.gopair.fileservice.mapper")
@EnableDiscoveryClient
public class FileServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(FileServiceApplication.class, args);
            log.info("[文件服务启动] ========================================");
            log.info("[文件服务启动] GoPair 文件服务启动成功！");
            log.info("[文件服务启动] ========================================");
        } catch (Exception e) {
            log.error("[文件服务启动] 文件服务启动失败", e);
            System.exit(1);
        }
    }
}
