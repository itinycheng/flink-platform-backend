package com.flink.platform.web.controller;

import com.flink.platform.dao.entity.CatalogInfo;
import com.flink.platform.dao.service.CatalogInfoService;
import com.flink.platform.web.entity.request.CatalogInfoRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.List;

/** Catalog info controller. */
@RestController
@RequestMapping("/catalog-info")
public class CatalogInfoController {

    @Autowired private CatalogInfoService iCatalogInfoService;

    @GetMapping
    public ResultInfo<List<CatalogInfo>> list(
            CatalogInfoRequest catalogInfoRequest, HttpServletRequest request) {

        List<CatalogInfo> list = this.iCatalogInfoService.list();

        return ResultInfo.success(list);
    }
}
