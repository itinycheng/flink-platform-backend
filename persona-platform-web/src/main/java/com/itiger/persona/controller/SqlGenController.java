package com.itiger.persona.controller;

import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.parser.SqlSelect;
import com.itiger.persona.service.SqlGenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tiny.wang
 */
@RestController
@RequestMapping("/sql")
public class SqlGenController {

    @Autowired
    private SqlGenService sqlGenService;

    @PostMapping(value = "generateSelect")
    public ResultInfo generateSelect(@RequestBody SqlSelect sqlSelect) {
        // validate first
        String sqlString = sqlGenService.generateSelect(sqlSelect);
        return ResultInfo.success(sqlString);
    }
}
