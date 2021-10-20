package com.flink.platform.web.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flink.platform.web.entity.Signature;
import com.flink.platform.web.mapper.SignatureMapper;
import com.flink.platform.web.service.ISignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Signature service impl. */
@Service
@DS("signature")
public class SignatureServiceImpl extends ServiceImpl<SignatureMapper, Signature>
        implements ISignatureService {

    @Autowired private SignatureMapper signatureMapper;

    @Override
    public List<Map<String, Object>> listAll() {
        return signatureMapper.selectValues();
    }
}
