package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.mapper.UserMapper;
import org.springframework.stereotype.Service;

/** User service impl. */
@Service
@DS("master_platform")
public class UserService extends ServiceImpl<UserMapper, User> {}
