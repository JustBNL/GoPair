package com.gopair.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gopair.adminservice.domain.po.RoomFile;
import com.gopair.adminservice.domain.query.FilePageQuery;
import com.gopair.adminservice.domain.vo.FileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoomFileMapper extends BaseMapper<RoomFile> {

    IPage<FileVO> selectFilePage(IPage<FileVO> page, @Param("query") FilePageQuery query);
}
