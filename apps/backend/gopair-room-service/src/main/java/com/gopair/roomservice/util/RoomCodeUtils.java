package com.gopair.roomservice.util;

import com.gopair.roomservice.enums.RoomErrorCode;
import com.gopair.roomservice.exception.RoomException;

import java.security.SecureRandom;
import java.util.function.Function;

/**
 * 房间码生成工具类
 * 
 * @author gopair
 */
public class RoomCodeUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成房间码
     * 使用时间戳后6位 + 2位随机数 = 8位房间码
     * 
     * @return 8位房间码
     */
    public static String generateRoomCode() {
        // 获取当前时间戳后6位
        long timestamp = System.currentTimeMillis();
        String timePart = String.valueOf(timestamp).substring(7);
        
        // 生成2位随机数（00-99）
        int randomPart = RANDOM.nextInt(100);
        
        return timePart + String.format("%02d", randomPart);
    }

    /**
     * 生成房间码并检查唯一性
     * 最多重试3次，全部失败后抛异常并记录 ERROR 日志。
     *
     * @param checkUnique 唯一性检查函数，返回true表示唯一
     * @return 唯一的房间码
     * @throws RoomException 如果重试3次都失败
     */
    public static String generateWithRetry(Function<String, Boolean> checkUnique) {
        for (int i = 0; i < 3; i++) {
            String code = generateRoomCode();
            if (checkUnique.apply(code)) {
                return code;
            }
        }
        throw new RoomException(RoomErrorCode.ROOM_CODE_GENERATION_FAILED);
    }

    /**
     * 验证房间码格式
     * 
     * @param roomCode 房间码
     * @return 是否为有效格式
     */
    public static boolean isValidFormat(String roomCode) {
        return roomCode != null && roomCode.matches("\\d{8}");
    }
} 