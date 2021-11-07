package com.flink.platform.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.dao.entity.Signature;
import com.flink.platform.dao.entity.SignatureValue;
import com.flink.platform.dao.service.SignatureService;
import com.flink.platform.dao.service.SignatureValueService;
import com.flink.platform.web.entity.request.SignatureRequest;
import com.flink.platform.web.entity.response.ResultInfo;
import com.flink.platform.web.entity.response.SignatureResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Signature controller. */
@RestController
@RequestMapping("/signature")
public class SignatureController {

    @Autowired private SignatureService signatureService;

    @Autowired private SignatureValueService signatureValueService;

    @GetMapping("list")
    public ResultInfo<List<SignatureResponse>> list() {

        List<Signature> list = this.signatureService.list();
        List<SignatureResponse> responseList =
                list.stream()
                        .map(
                                item -> {
                                    List<SignatureValue> signatureValueList =
                                            this.signatureValueService.list(
                                                    new QueryWrapper<SignatureValue>()
                                                            .lambda()
                                                            .eq(
                                                                    SignatureValue::getSignId,
                                                                    item.getId()));
                                    SignatureResponse response = new SignatureResponse();
                                    BeanUtils.copyProperties(item, response);
                                    response.setValues(signatureValueList);
                                    return response;
                                })
                        .collect(Collectors.toList());

        return ResultInfo.success(responseList);
    }

    @GetMapping
    public ResultInfo<IPage<Signature>> get(
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
            SignatureRequest signatureRequest) {

        Page<Signature> pager = new Page<>(page, size);
        IPage<Signature> iPage =
                this.signatureService.page(
                        pager,
                        new QueryWrapper<Signature>()
                                .lambda()
                                .eq(
                                        Objects.nonNull(signatureRequest.getStatus()),
                                        Signature::getStatus,
                                        signatureRequest.getStatus())
                                .like(
                                        StringUtils.isNotBlank(signatureRequest.getName()),
                                        Signature::getName,
                                        signatureRequest.getName())
                                .like(
                                        StringUtils.isNotBlank(signatureRequest.getAccountType()),
                                        Signature::getAccountType,
                                        signatureRequest.getAccountType()));

        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "{id}")
    public ResultInfo<Signature> getOne(@PathVariable String id) {
        Signature signature = this.signatureService.getById(id);
        return ResultInfo.success(signature);
    }

    @PostMapping
    public ResultInfo<Boolean> saveOrUpdate(@RequestBody SignatureRequest signature) {
        if (StringUtils.isNotBlank(signature.getName())) {

            this.buildSignature(signature);

            // save
            if (Objects.isNull(signature.getId())) {
                Signature one =
                        this.signatureService.getOne(
                                new QueryWrapper<Signature>()
                                        .lambda()
                                        .eq(Signature::getName, signature.getName()));

                if (Objects.isNull(one)) {
                    signature.setStatus(1);
                    signature.setCreateTime(Instant.now().toEpochMilli());
                    this.signatureService.save(signature);
                    return ResultInfo.success(true);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                signature.setUpdateTime(Instant.now().toEpochMilli());
                this.signatureService.updateById(signature);
                return ResultInfo.success(true);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }
    }

    private void buildSignature(SignatureRequest signature) {

        signature.setAccountType(
                Objects.nonNull(signature.getAccountTypeList())
                        ? StringUtils.join(signature.getAccountTypeList(), ",")
                        : "");
    }
}
