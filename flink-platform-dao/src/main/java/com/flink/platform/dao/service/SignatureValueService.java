package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.SignatureValue;
import com.flink.platform.dao.mapper.SignatureValueMapper;
import org.springframework.stereotype.Service;

/** Signature value service impl. */
@Service
@DS("signature")
public class SignatureValueService extends ServiceImpl<SignatureValueMapper, SignatureValue> {}
