package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.CatalogInfo;
import org.apache.ibatis.annotations.Mapper;

/** job catalog info Mapper. */
@Mapper
public interface CatalogInfoMapper extends BaseMapper<CatalogInfo> {}
