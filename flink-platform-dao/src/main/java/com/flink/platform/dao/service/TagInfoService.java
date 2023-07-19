package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.TagInfo;
import com.flink.platform.dao.mapper.TagInfoMapper;
import org.springframework.stereotype.Service;

/** tag info service. */
@Service
@DS("master_platform")
public class TagInfoService extends ServiceImpl<TagInfoMapper, TagInfo> {}
