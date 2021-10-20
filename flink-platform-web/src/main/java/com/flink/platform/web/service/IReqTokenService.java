package com.flink.platform.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flink.platform.web.entity.ReqToken;

/** Req token service interface. */
public interface IReqTokenService extends IService<ReqToken> {

    ReqToken getByToken(String token);

    ReqToken saveCache(ReqToken reqToken);
}
