package com.flink.platform.web.controller;

import com.flink.platform.dao.entity.CatalogInfo;
import com.flink.platform.dao.service.CatalogInfoService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalog info controller. */
@RestController
@RequestMapping("/catalogInfo")
public class CatalogInfoController {

    @Autowired private CatalogInfoService catalogInfoService;

    @GetMapping
    public ResultInfo<List<CatalogInfo>> list() {
        List<CatalogInfo> list = catalogInfoService.list();
        return ResultInfo.success(list);
    }
}
