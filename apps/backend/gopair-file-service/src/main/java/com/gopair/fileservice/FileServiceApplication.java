package com.gopair.fileservice;

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
@SpringBootApplication
@MapperScan("com.gopair.fileservice.mapper")
@EnableDiscoveryClient
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
