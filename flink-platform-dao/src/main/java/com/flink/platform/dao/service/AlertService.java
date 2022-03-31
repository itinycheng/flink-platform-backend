package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.AlertInfo;
import com.flink.platform.dao.mapper.AlertMapper;
import org.springframework.stereotype.Service;

/** job config info. */
@Service
@DS("master_platform")
public class AlertService extends ServiceImpl<AlertMapper, AlertInfo> {}
