package com.itiger.persona.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itiger.persona.entity.JobInfo;
import com.itiger.persona.entity.request.TJobInfoRequest;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.enums.ResponseStatus;
import com.itiger.persona.exception.DefinitionException;
import com.itiger.persona.service.IJobInfoService;
import com.itiger.persona.service.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * @Author Shik
 * @Title: TJobInfoController
 * @ProjectName: persona-platform-backend
 * @Description: TODO
 * @Date: 2021/4/14 上午10:49
 */
@RestController
@RequestMapping("/t-job-info")
public class TJobInfoController {

    @Autowired
    private IJobInfoService iJobInfoService;

    @Autowired
    private RedisService redisService;

    @PostMapping
    public ResultInfo saveOrUpdate(HttpServletRequest request, @RequestBody TJobInfoRequest tJobInfoRequest) {
        if (StringUtils.isNotBlank(tJobInfoRequest.getJobName())) {

            // save
            if (Objects.isNull(tJobInfoRequest.getId())) {
                JobInfo one = this.iJobInfoService.getOne(new QueryWrapper<JobInfo>().lambda().eq(JobInfo::getJobName, tJobInfoRequest.getJobName()));

                if (Objects.isNull(one)) {
                    // TODO set time status
                    Boolean save = this.iJobInfoService.save(tJobInfoRequest);
                    if (save) {
                        // init cache
//                        this.redisService.initSignatureInfo();
                    }
                    return ResultInfo.success(true);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                // TODO update column
                Boolean save = this.iJobInfoService.updateById(tJobInfoRequest);
                if (save) {
                    // init cache
//                    this.redisService.initSignatureInfo();
                }
                return ResultInfo.success(true);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

    }

}
