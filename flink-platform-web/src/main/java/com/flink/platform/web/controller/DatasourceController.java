package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.common.enums.DbType;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.web.entity.request.DatasourceRequest;
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
import java.util.Objects;

import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** datasource controller. */
@RestController
@RequestMapping("/datasource")
public class DatasourceController {

    @Autowired
    private DatasourceService datasourceService;

    @PostMapping(value = "/create")
    public ResultInfo<Long> create(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestBody DatasourceRequest datasourceRequest) {
        String errorMsg = datasourceRequest.validateOnCreate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        Datasource datasource = datasourceRequest.getDatasource();
        datasource.setId(null);
        datasource.setUserId(loginUser.getId());
        datasourceService.save(datasource);
        return success(datasource.getId());
    }

    @PostMapping(value = "/update")
    public ResultInfo<Long> update(@RequestBody DatasourceRequest datasourceRequest) {
        String errorMsg = datasourceRequest.validateOnUpdate();
        if (StringUtils.isNotBlank(errorMsg)) {
            return failure(ERROR_PARAMETER, errorMsg);
        }

        Datasource datasource = datasourceRequest.getDatasource();
        datasource.setUserId(null);
        datasourceService.updateById(datasource);
        return success(datasource.getId());
    }

    @GetMapping(value = "/get/{dsId}")
    public ResultInfo<Datasource> get(@PathVariable Long dsId) {
        Datasource datasource = datasourceService.getById(dsId);
        return success(datasource);
    }

    @GetMapping(value = "/delete/{dsId}")
    public ResultInfo<Boolean> delete(@PathVariable Long dsId) {
        boolean bool = datasourceService.removeById(dsId);
        return success(bool);
    }

    @GetMapping(value = "/page")
    public ResultInfo<IPage<Datasource>> page(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "type", required = false) DbType type) {
        Page<Datasource> pager = new Page<>(page, size);
        IPage<Datasource> iPage = datasourceService.page(
                pager,
                new QueryWrapper<Datasource>()
                        .lambda()
                        .eq(Datasource::getUserId, loginUser.getId())
                        .eq(Objects.nonNull(type), Datasource::getType, type)
                        .like(Objects.nonNull(name), Datasource::getName, name));
        return success(iPage);
    }

    @GetMapping(value = "/list")
    public ResultInfo<List<Datasource>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @RequestParam(name = "dbType", required = false) DbType dbType,
            @RequestParam(name = "jobType", required = false) JobType jobtype) {
        if (jobtype != null) {
            dbType = jobtype.getDbType();
        }

        List<Datasource> list = datasourceService.list(new QueryWrapper<Datasource>()
                .lambda()
                .eq(Objects.nonNull(dbType), Datasource::getType, dbType)
                .eq(Datasource::getUserId, loginUser.getId()));
        return success(list);
    }
}
