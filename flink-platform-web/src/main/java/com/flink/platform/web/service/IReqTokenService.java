package com.flink.platform.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flink.platform.web.entity.ReqToken;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author shik
 * @since 2021-03-03
 */
public interface IReqTokenService extends IService<ReqToken> {

    ReqToken getByToken(String token);

    ReqToken saveCache(ReqToken reqToken);

}
