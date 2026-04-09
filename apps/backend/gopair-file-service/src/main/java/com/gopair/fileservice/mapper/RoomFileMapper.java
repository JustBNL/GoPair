package com.gopair.fileservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.fileservice.domain.po.RoomFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房间文件Mapper接口
 *
 * @author gopair
 */
@Mapper
public interface RoomFileMapper extends BaseMapper<RoomFile> {

    /**
     * 分页查询房间文件列表（按上传时间倒序）
     *
     * @param page   分页参数
     * @param roomId 房间ID
     * @return 文件分页数据
     */
    IPage<RoomFile> selectPageByRoomId(Page<RoomFile> page, @Param("roomId") Long roomId);

    /**
     * 统计房间文件数量
     *
     * @param roomId 房间ID
     * @return 文件数量
     */
    long countByRoomId(@Param("roomId") Long roomId);

    /**
     * 统计房间文件总大小（字节）
     *
     * @param roomId 房间ID
     * @return 总大小字节数，无文件时返回0
     */
    long sumFileSizeByRoomId(@Param("roomId") Long roomId);

    /**
     * 查询房间所有文件（用于批量清理）
     *
     * @param roomId 房间ID
     * @return 文件列表
     */
    List<RoomFile> selectAllByRoomId(@Param("roomId") Long roomId);

    /**
     * 分批查询房间文件（用于清理时防止 OOM）
     *
     * @param roomId 房间ID
     * @param limit  每批大小
     * @return 文件列表
     */
    List<RoomFile> selectBatchByRoomId(@Param("roomId") Long roomId, @Param("limit") int limit);

    /**
     * 原子递增文件下载计数（用于 generateDownloadUrl，防止并发 Lost Update）
     *
     * @param fileId 文件ID
     * @return 受影响行数
     */
    int incrementDownloadCount(@Param("fileId") Long fileId);
}
