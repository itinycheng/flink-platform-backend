package com.flink.platform.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flink.platform.web.entity.JobInfo;

/**
 * <p>
 * job config info 服务类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
public interface IJobInfoService extends IService<JobInfo> {

    boolean save(JobInfo jobInfo);

    boolean updateById(JobInfo jobInfo);

    boolean stopJob(JobInfo jobInfo);

    boolean openJob(JobInfo jobInfo);
}
