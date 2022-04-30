package com.flink.platform.web.controller;

import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.Datasource;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.DatasourceService;
import com.flink.platform.web.entity.request.ReactiveRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.entity.vo.ReactiveDataVo;
import com.flink.platform.web.service.ReactiveService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static com.flink.platform.common.enums.ResponseStatus.DATASOURCE_NOT_FOUND;
import static com.flink.platform.common.enums.ResponseStatus.ERROR_PARAMETER;
import static com.flink.platform.web.entity.response.ResultInfo.failure;
import static com.flink.platform.web.entity.response.ResultInfo.success;

/** Reactive programing. */
@Slf4j
@RestController
@RequestMapping("/reactive")
public class ReactiveController {

    @Autowired private DatasourceService datasourceService;

    @Autowired private ReactiveService reactiveService;

    @PostMapping(value = "/sync")
    public ResultInfo<ReactiveDataVo> sync(@RequestBody ReactiveRequest reactiveRequest) {
        ReactiveDataVo reactiveDataVo;
        try {
            String errorMsg = reactiveRequest.validate();
            if (StringUtils.isNotBlank(errorMsg)) {
                return failure(ERROR_PARAMETER, errorMsg);
            }

            Datasource datasource = datasourceService.getById(reactiveRequest.getDatasourceId());
            if (datasource == null) {
                return failure(DATASOURCE_NOT_FOUND);
            }

            reactiveDataVo = reactiveService.executeAndGet(reactiveRequest, datasource);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer, true));
            reactiveDataVo =
                    new ReactiveDataVo(new String[] {"exception"}, null, writer.toString());
        }
        return success(reactiveDataVo);
    }

    @GetMapping(value = "/data/{uuid}")
    public ResultInfo<List<String>> data(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser,
            @PathVariable String uuid)
            throws Exception {
        String key = String.join("-", loginUser.getId().toString(), uuid);
        return success(null);
    }
}
