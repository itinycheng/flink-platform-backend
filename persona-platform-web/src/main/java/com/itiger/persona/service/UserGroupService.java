package com.itiger.persona.service;

import com.itiger.persona.common.util.FunctionUtil;
import com.itiger.persona.common.util.JsonUtil;
import com.itiger.persona.enums.SqlVar;
import com.itiger.persona.mapper.JobRunInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author tiny.wang
 */
@Slf4j
@Service
public class UserGroupService {

    private static final String HIVE_USER_GROUP_PATH = "/user/hive/warehouse/signature.db/t_hive_user_group_result/id=%s/ts=%s";

    @Resource
    private HdfsService hdfsService;

    @Resource
    private JobRunInfoMapper jobRunInfoMapper;

    public long getAndStoreResultSize(Long id, String variables) {
        Map<String, String> map = JsonUtil.toStrMap(variables);
        String jobCode = map.get(SqlVar.JOB_CODE.name());
        long sum = map.entrySet().stream()
                .filter(entry -> !SqlVar.JOB_CODE.name().equals(entry.getKey()))
                .map(entry -> String.format(HIVE_USER_GROUP_PATH, jobCode, entry.getValue()))
                .filter(path -> hdfsService.exists(path))
                .flatMap(FunctionUtil.uncheckedFunction((value) -> hdfsService.listVisibleFiles(value).stream()))
                .map(FunctionUtil.uncheckedFunction(value -> hdfsService.lineNumber(value)))
                .mapToLong(Integer::longValue)
                .sum();
        jobRunInfoMapper.updateResultSize(id, sum);
        return sum;
    }

}
