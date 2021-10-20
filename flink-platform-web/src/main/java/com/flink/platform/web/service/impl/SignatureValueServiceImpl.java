package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.SignatureValue;
import com.flink.platform.web.mapper.SignatureValueMapper;
import com.flink.platform.web.service.ISignatureValueService;
import org.springframework.stereotype.Service;

/** Signature value service impl. */
@Service
@DS("signature")
public class SignatureValueServiceImpl extends ServiceImpl<SignatureValueMapper, SignatureValue>
        implements ISignatureValueService {}
