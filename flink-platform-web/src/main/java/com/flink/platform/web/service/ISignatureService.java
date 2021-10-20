package com.flink.platform.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flink.platform.web.entity.Signature;

import java.util.List;
import java.util.Map;

/** Signature service interface. */
public interface ISignatureService extends IService<Signature> {

    List<Map<String, Object>> listAll();
}
