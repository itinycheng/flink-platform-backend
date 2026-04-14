package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Workspace;
import com.flink.platform.dao.mapper.WorkspaceMapper;
import org.springframework.stereotype.Service;

/** Workspace service. */
@DS("master_platform")
@Service
public class WorkspaceService extends ServiceImpl<WorkspaceMapper, Workspace> {}
