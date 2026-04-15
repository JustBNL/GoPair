package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.mapper.RoomFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文件管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileManageService {

    private final RoomFileMapper roomFileMapper;

    public record FilePageQuery(Integer pageNum, Integer pageSize, Long roomId, String keyword) {}

    public Page<RoomFile> getFilePage(FilePageQuery query) {
        Page<RoomFile> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<RoomFile> wrapper = new LambdaQueryWrapper<>();
        if (query.roomId() != null) {
            wrapper.eq(RoomFile::getRoomId, query.roomId());
        }
        if (StringUtils.hasText(query.keyword())) {
            wrapper.like(RoomFile::getFileName, query.keyword());
        }
        wrapper.orderByDesc(RoomFile::getCreateTime);
        return roomFileMapper.selectPage(page, wrapper);
    }

    public RoomFile getFileById(Long fileId) {
        return roomFileMapper.selectById(fileId);
    }

    public void deleteFile(Long fileId) {
        RoomFile file = roomFileMapper.selectById(fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件记录不存在");
        }
        roomFileMapper.deleteById(fileId);
        log.info("[FileManage] 删除文件元数据: fileId={}, fileName={}", fileId, file.getFileName());
    }
}
