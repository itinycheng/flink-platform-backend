package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.Status;
import com.flink.platform.common.util.UuidGenerator;
import com.flink.platform.dao.entity.JobFlow;
import com.flink.platform.dao.entity.TagInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.JobFlowService;
import com.flink.platform.dao.service.TagInfoService;
import com.flink.platform.web.config.annotation.ApiException;
import com.flink.platform.web.entity.request.TagInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.common.enums.ResponseStatus.OPERATION_NOT_ALLOWED;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

/** Job flow tag controller. */
@RestController
@RequestMapping("/tag")
public class TagInfoController {

    @Autowired
    private TagInfoService tagInfoService;

    @Autowired
    private JobFlowService jobFlowService;

    @ApiException
    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser, @RequestBody TagInfoRequest tagRequest) {
        String errorMsg = tagRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        TagInfo tagInfo = tagRequest.getTagInfo();
        tagInfo.setId(null);
        tagInfo.setCode(UuidGenerator.generateShortUuid());
        tagInfo.setUserId(loginUser.getId());
        tagInfo.setStatus(Status.ENABLE);
        tagInfoService.save(tagInfo);
        return success(tagInfo.getId());
    }

    @ApiException
    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody TagInfoRequest tagRequest) {
        String errorMsg = tagRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        tagRequest.setCode(null);
        tagRequest.setUserId(null);
        tagInfoService.updateById(tagRequest.getTagInfo());
        return success(tagRequest.getId());
    }

    @GetMapping(value = "/get/{tagId}")
    public ResultInfo<TagInfo> get(@PathVariable Long tagId) {
        TagInfo tagInfo = tagInfoService.getById(tagId);
        return success(tagInfo);
    }

    @GetMapping(value = "/delete/{tagId}")
    public ResultInfo<Boolean> delete(@PathVariable Long tagId) {
        TagInfo tagInfo = tagInfoService.getById(tagId);
        JobFlow jobFlow = jobFlowService.getOne(new QueryWrapper<JobFlow>()
                .lambda()
                .like(JobFlow::getTags, tagInfo.getCode())
                .last("LIMIT 1"));
        if (jobFlow != null) {
            return failure(
                    OPERATION_NOT_ALLOWED,
                    format("The tag is being used in jobFlow: %s, cannot be removed", jobFlow.getName()));
        }

        boolean bool = tagInfoService.removeById(tagId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<TagInfo>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name) {
        Page<TagInfo> pager = new Page<>(page, size);
        IPage<TagInfo> iPage = tagInfoService.page(
                pager,
                new QueryWrapper<TagInfo>()
                        .lambda()
                        .eq(TagInfo::getUserId, loginUser.getId())
                        .like(nonNull(name), TagInfo::getName, name));

        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<TagInfo>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) Status status) {
        List<TagInfo> list = tagInfoService.list(new QueryWrapper<TagInfo>()
                .lambda()
                .eq(TagInfo::getUserId, loginUser.getId())
                .eq(nonNull(status), TagInfo::getStatus, status)
                .like(nonNull(name), TagInfo::getName, name));
        return success(list);
    }
}
