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
     * 动态分页查询房间文件列表
     *
     * @param page      分页参数（传入已构造好的 Page 对象）
     * @param roomId    房间ID（必填）
     * @param keyword   搜索关键字（可选，模糊匹配文件名）
     * @param fileType  文件类型分类（可选，image/document/video/audio/archive/other）
     * @param sortField 排序字段（可选，uploadTime/fileSize/fileName，默认为 uploadTime）
     * @param sortOrder 排序方向（可选，asc/desc，默认为 desc）
     * @return 分页结果
     */
    IPage<RoomFile> selectPage(
        Page<RoomFile> page,
        @Param("roomId") Long roomId,
        @Param("keyword") String keyword,
        @Param("fileType") String fileType,
        @Param("sortField") String sortField,
        @Param("sortOrder") String sortOrder
    );

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
     * 分批查询房间文件（用于清理时防止 OOM）
     *
     * @param roomId 房间ID
     * @param limit  每批大小
     * @return 文件列表
     */
    List<RoomFile> selectBatchByRoomId(@Param("roomId") Long roomId, @Param("limit") int limit);

    /**
     * 批量删除文件记录（替代 BaseMapper 的 deprecated deleteBatchIds）
     *
     * @param ids 文件ID列表
     * @return 删除行数
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 原子递增文件下载计数（用于 generateDownloadUrl，防止并发 Lost Update）
     *
     * @param fileId 文件ID
     * @return 受影响行数
     */
    int incrementDownloadCount(@Param("fileId") Long fileId);
}
