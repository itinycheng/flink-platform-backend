package com.flink.platform.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flink.platform.web.entity.CatalogInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * job catalog info Mapper 接口
 * </p>
 *
 * @author shik
 * @since 2021-04-22
 */
@Mapper
public interface CatalogInfoMapper extends BaseMapper<CatalogInfo> {

}
