package com.flink.platform.dao.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.dao.entity.Signature;
import com.flink.platform.dao.mapper.SignatureMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Signature service impl. */
@Service
@DS("signature")
public class SignatureService extends ServiceImpl<SignatureMapper, Signature> {

    @Autowired private SignatureMapper signatureMapper;

    public List<Map<String, Object>> listAll() {
        return signatureMapper.selectValues();
    }
}
