package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.domain.query.FilePageQuery;
import com.gopair.adminservice.domain.vo.FileVO;
import com.gopair.adminservice.mapper.RoomFileMapper;
import com.gopair.adminservice.annotation.AdminAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileManageService {

    private final RoomFileMapper roomFileMapper;

    public IPage<FileVO> getFilePage(FilePageQuery query) {
        Page<FileVO> page = new Page<>(query.pageNum(), query.pageSize());

        IPage<FileVO> filePage = roomFileMapper.selectFilePage(page, query);
        return filePage;
    }

    public RoomFile getFileById(Long fileId) {
        return roomFileMapper.selectById(fileId);
    }

    @AdminAudit(operation = "FILE_DELETE", targetType = "FILE")
    public void deleteFile(Long fileId) {
        RoomFile file = roomFileMapper.selectById(fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件记录不存在");
        }
        roomFileMapper.deleteById(fileId);
        log.info("[FileManage] 删除文件元数据: fileId={}, fileName={}", fileId, file.getFileName());
    }
}
