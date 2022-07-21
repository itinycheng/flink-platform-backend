package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.constants.Constant;
import com.flink.platform.dao.entity.CatalogInfo;
import com.flink.platform.dao.entity.User;
import com.flink.platform.dao.service.CatalogInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalog info controller. */
@RestController
@RequestMapping("/catalog")
public class CatalogInfoController {

    @Autowired private CatalogInfoService catalogInfoService;

    @GetMapping(value = "/list")
    public ResultInfo<List<CatalogInfo>> list(
            @RequestAttribute(value = Constant.SESSION_USER) User loginUser) {
        List<CatalogInfo> list =
                catalogInfoService.list(
                        new QueryWrapper<CatalogInfo>()
                                .lambda()
                                .eq(CatalogInfo::getUserId, loginUser.getId()));
        return ResultInfo.success(list);
    }
}
