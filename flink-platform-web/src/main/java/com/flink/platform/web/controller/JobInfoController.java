package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.web.entity.JobInfo;
import com.flink.platform.web.entity.request.JobInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.common.enums.JobStatusEnum;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.web.service.IJobInfoService;
import com.flink.platform.web.service.JobInfoQuartzService;
import com.flink.platform.web.service.RedisService;
import com.flink.platform.common.util.UuidGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * @Author Shik
 * @Title: TJobInfoController
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/4/14 上午10:49
 */
@RestController
@RequestMapping("/t-job-info")
public class JobInfoController {

    @Autowired
    private IJobInfoService iJobInfoService;

    @Resource
    public JobInfoQuartzService jobInfoQuartzService;

    @Autowired
    private RedisService redisService;

    @GetMapping
    public ResultInfo get(@RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
                          @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
                          JobInfoRequest jobInfoRequest,
                          HttpServletRequest request) {

        Page pager = new Page<>(page, size);
        IPage iPage = this.iJobInfoService.page(pager, new QueryWrapper<JobInfo>().lambda()
                .eq(Objects.nonNull(jobInfoRequest.getStatus()), JobInfo::getStatus, jobInfoRequest.getStatus())
        );

        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "{id}")
    public ResultInfo getOne(@PathVariable String id, HttpServletRequest request) {
        JobInfo jobInfo = this.iJobInfoService.getById(id);
        return ResultInfo.success(jobInfo);
    }

    @PostMapping(value = "open/{id}")
    public ResultInfo openOne(@PathVariable String id, String cronExpr, HttpServletRequest request) {
        JobInfo jobInfo = this.iJobInfoService.getById(id);

        boolean result;
        if(Objects.nonNull(jobInfo)) {
            jobInfo.setCronExpr(cronExpr);
            result = this.iJobInfoService.openJob(jobInfo);
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

        return ResultInfo.success(result);
    }

    @PostMapping(value = "stop/{id}")
    public ResultInfo stopOne(@PathVariable String id, HttpServletRequest request) {
        JobInfo jobInfo = this.iJobInfoService.getById(id);

        boolean result;
        if(Objects.nonNull(jobInfo)) {
            result = iJobInfoService.stopJob(jobInfo);
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

        return ResultInfo.success(result);
    }

    @PostMapping
    public ResultInfo saveOrUpdate(HttpServletRequest request, @RequestBody JobInfoRequest jobInfoRequest) {
        if (StringUtils.isNotBlank(jobInfoRequest.getName())) {
            // save
            if (Objects.isNull(jobInfoRequest.getId())) {
                JobInfo one = this.iJobInfoService.getOne(new QueryWrapper<JobInfo>().lambda().eq(JobInfo::getName, jobInfoRequest.getName()));
                if (Objects.isNull(one)) {
                    // TODO set time status
                    this.buildJobInfo(jobInfoRequest);
                    this.iJobInfoService.save(jobInfoRequest);
                    return ResultInfo.success(true);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                // TODO update column
                this.iJobInfoService.updateById(jobInfoRequest);
                return ResultInfo.success(true);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

    }

    private void buildJobInfo(JobInfoRequest tJobInfoRequest) {
        if (StringUtils.isBlank(tJobInfoRequest.getCode())) {
            tJobInfoRequest.setCode(UuidGenerator.generateShortUuid());
        }

        if (Objects.isNull(tJobInfoRequest.getStatus())) {
            tJobInfoRequest.setStatus(JobStatusEnum.NEW.getCode());
        }

        if (StringUtils.isNotBlank(tJobInfoRequest.getSqlMain())) {
            tJobInfoRequest.setSubject(tJobInfoRequest.getSqlMain());
        }

        tJobInfoRequest.setCatalogs(Objects.nonNull(tJobInfoRequest.getCatalogIds()) ? tJobInfoRequest.getCatalogIds().toString() : "");
    }

}
