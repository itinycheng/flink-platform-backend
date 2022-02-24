package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Session;
import com.flink.platform.dao.mapper.SessionMapper;
import org.springframework.stereotype.Service;

/** session service impl. */
@Service
@DS("master_platform")
public class SessionService extends ServiceImpl<SessionMapper, Session> {}
