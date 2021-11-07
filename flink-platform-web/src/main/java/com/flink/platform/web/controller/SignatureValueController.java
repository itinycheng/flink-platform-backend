package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.SignatureValue;
import com.flink.platform.dao.service.SignatureValueService;
import com.flink.platform.web.entity.response.ResultInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** Signature value controller. */
@RestController
@RequestMapping("/signature-value")
public class SignatureValueController {

    @Autowired private SignatureValueService signatureValueService;

    @GetMapping("list")
    public ResultInfo<List<SignatureValue>> list() {

        List<SignatureValue> list = this.signatureValueService.list();

        return ResultInfo.success(list);
    }

    @GetMapping("list/{signId}")
    public ResultInfo<List<SignatureValue>> list(@PathVariable Long signId) {

        List<SignatureValue> list =
                this.signatureValueService.list(
                        new QueryWrapper<SignatureValue>()
                                .lambda()
                                .eq(SignatureValue::getSignId, signId));

        return ResultInfo.success(list);
    }

    @PostMapping
    public ResultInfo<Boolean> saveOrUpdate(@RequestBody SignatureValue signatureValue) {
        if (StringUtils.isNotBlank(signatureValue.getValue())) {

            // save
            if (Objects.isNull(signatureValue.getId())) {
                SignatureValue one =
                        this.signatureValueService.getOne(
                                new QueryWrapper<SignatureValue>()
                                        .lambda()
                                        .eq(SignatureValue::getSignId, signatureValue.getSignId())
                                        .eq(SignatureValue::getValue, signatureValue.getValue()));

                if (Objects.isNull(one)) {
                    Boolean save = this.signatureValueService.save(signatureValue);
                    return ResultInfo.success(save);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                Boolean save = this.signatureValueService.updateById(signatureValue);
                return ResultInfo.success(save);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }
    }
}
