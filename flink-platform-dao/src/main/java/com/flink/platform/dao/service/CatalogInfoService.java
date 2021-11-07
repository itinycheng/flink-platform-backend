package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.CatalogInfo;
import com.flink.platform.dao.mapper.CatalogInfoMapper;
import org.springframework.stereotype.Service;

/** job catalog info. */
@Service
@DS("master_platform")
public class CatalogInfoService extends ServiceImpl<CatalogInfoMapper, CatalogInfo> {}
