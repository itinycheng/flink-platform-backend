package com.itiger.persona.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itiger.persona.common.enums.ResponseStatus;
import com.itiger.persona.common.exception.DefinitionException;
import com.itiger.persona.entity.Signature;
import com.itiger.persona.entity.SignatureValue;
import com.itiger.persona.entity.request.SignatureRequest;
import com.itiger.persona.entity.response.ResultInfo;
import com.itiger.persona.entity.response.SignatureResponse;
import com.itiger.persona.service.ISignatureService;
import com.itiger.persona.service.ISignatureValueService;
import com.itiger.persona.service.RedisService;
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

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author Shik
 * @Title: SignatureController
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2020/11/26 下午5:03
 */
@RestController
@RequestMapping("/signature")
public class SignatureController {

    @Autowired
    private RedisService redisService;

    @Autowired
    private ISignatureService iSignatureService;

    @Autowired
    private ISignatureValueService iSignatureValueService;

    @GetMapping("list")
    public ResultInfo list(HttpServletRequest request) {

        List<Signature> list = this.iSignatureService.list();
        List<SignatureResponse> responseList = list.stream().map(item -> {
            List<SignatureValue> signatureValueList = this.iSignatureValueService.list(new QueryWrapper<SignatureValue>().lambda().eq(SignatureValue::getSignId, item.getId()));
            SignatureResponse response = new SignatureResponse();
            BeanUtils.copyProperties(item, response);
            response.setValues(signatureValueList);
            return response;
        }).collect(Collectors.toList());

        return ResultInfo.success(responseList);

    }

    @GetMapping
    public ResultInfo get(@RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
                          @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
                          SignatureRequest signatureRequest,
                          HttpServletRequest request) {

        Page pager = new Page<>(page, size);
        IPage iPage = this.iSignatureService.page(pager, new QueryWrapper<Signature>().lambda()
                .eq(Objects.nonNull(signatureRequest.getStatus()), Signature::getStatus, signatureRequest.getStatus())
                .like(StringUtils.isNotBlank(signatureRequest.getName()), Signature::getName, signatureRequest.getName())
                .like(StringUtils.isNotBlank(signatureRequest.getAccountType()), Signature::getAccountType, signatureRequest.getAccountType())
        );

        return ResultInfo.success(iPage);
    }

    @GetMapping(value = "{id}")
    public ResultInfo getOne(@PathVariable String id, HttpServletRequest request) {
        Signature signature = this.iSignatureService.getById(id);
        return ResultInfo.success(signature);
    }

    @PostMapping
    public ResultInfo saveOrUpdate(HttpServletRequest request, @RequestBody SignatureRequest signature) {
        if (StringUtils.isNotBlank(signature.getName())) {

            this.buildSignature(signature);

            // save
            if (Objects.isNull(signature.getId())) {
                Signature one = this.iSignatureService.getOne(new QueryWrapper<Signature>().lambda().eq(Signature::getName, signature.getName()));

                if (Objects.isNull(one)) {
                    signature.setStatus(1);
                    signature.setCreateTime(Instant.now().toEpochMilli());
                    Boolean save = this.iSignatureService.save(signature);
                    if (save) {
                        this.redisService.initSignatureInfo();
                    }
                    return ResultInfo.success(true);
                } else {
                    throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
                }
            } else {
                // update
                signature.setUpdateTime(Instant.now().toEpochMilli());
                Boolean save = this.iSignatureService.updateById(signature);
                if (save) {
                    this.redisService.initSignatureInfo();
                }
                return ResultInfo.success(true);
            }
        } else {
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

    }

    private void buildSignature(SignatureRequest signature) {

        signature.setAccountType(Objects.nonNull(signature.getAccountTypeList()) ? StringUtils.join(signature.getAccountTypeList(), ",") : "");

    }

}
