package com.itiger.persona.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itiger.persona.entity.JobRunInfo;
import com.itiger.persona.mapper.SignatureMapper;
import com.itiger.persona.service.IJobRunInfoService;
import com.itiger.persona.mapper.JobRunInfoMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * job run info 服务实现类
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Service
@DS("master_platform")
public class JobRunInfoServiceImpl extends ServiceImpl<JobRunInfoMapper, JobRunInfo> implements IJobRunInfoService {

    @Autowired
    private JobRunInfoMapper jobRunInfoMapper;

    @Override
    public List<Map<String,Object>> listAllRunningJobs() {
        return jobRunInfoMapper.selectJobStates();
    }

    @Override
    public List<Map<String,Object>> selectById(Integer id) {
        return jobRunInfoMapper.selectById(id);
    }

    @Override
    public void updateJobState(Integer id, Integer status) throws Exception {
        if (id == null || status == null) {
            throw new Exception("Param is null");
        }
        try {
            List<Map<String, Object>> userMp = selectById(id);
            if (userMp == null) {
                throw new Exception("record can not be found");
            }
            jobRunInfoMapper.updateJobState(id, status);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
