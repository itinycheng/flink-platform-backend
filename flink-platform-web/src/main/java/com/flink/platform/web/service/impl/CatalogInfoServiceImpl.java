package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.CatalogInfo;
import com.flink.platform.web.mapper.CatalogInfoMapper;
import com.flink.platform.web.service.ICatalogInfoService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * job catalog info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-22
 */
@Service
@DS("master_platform")
public class CatalogInfoServiceImpl extends ServiceImpl<CatalogInfoMapper, CatalogInfo> implements ICatalogInfoService {

}
