package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.JobParam;
import com.flink.platform.dao.mapper.JobParamMapper;
import org.springframework.stereotype.Service;

/** job param service. */
@Service
@DS("master_platform")
public class JobParamService extends ServiceImpl<JobParamMapper, JobParam> {}
