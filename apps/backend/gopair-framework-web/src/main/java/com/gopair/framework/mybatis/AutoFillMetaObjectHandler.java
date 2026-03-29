package com.gopair.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus自动填充处理器
 * 
 * 自动处理BaseEntity中的时间审计字段填充：
 * - INSERT操作：自动填充createTime
 * - UPDATE操作：自动填充updateTime
 * 
 * @author gopair
 */
@Slf4j
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @PostConstruct
    public void init() {
        log.info("[框架配置] MyBatis Plus自动填充处理器初始化完成");
    }

    /**
     * INSERT操作时的自动填充
     * 
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // INSERT 操作同时填充 createTime 和 updateTime
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            
            log.debug("[自动填充] INSERT操作填充createTime/updateTime: {}", now);
        } catch (Exception e) {
            log.error("[自动填充] INSERT操作自动填充失败", e);
        }
    }

    /**
     * UPDATE操作时的自动填充
     * 
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 填充更新时间
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
            
            log.debug("[自动填充] UPDATE操作填充updateTime: {}", now);
        } catch (Exception e) {
            log.error("[自动填充] UPDATE操作自动填充失败", e);
        }
    }
} 