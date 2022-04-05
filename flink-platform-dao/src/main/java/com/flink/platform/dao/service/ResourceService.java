package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.mapper.ResourceMapper;
import org.springframework.stereotype.Service;

/** job config info. */
@Service
@DS("master_platform")
public class ResourceService extends ServiceImpl<ResourceMapper, Resource> {}
