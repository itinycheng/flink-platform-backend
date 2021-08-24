package com.flink.platform.web.config.aspect;

import com.flink.platform.common.enums.ResponseStatus;
import com.flink.platform.common.exception.DefinitionException;
import com.flink.platform.web.config.annotation.TokenChecker;
import com.flink.platform.web.entity.ReqToken;
import com.flink.platform.web.service.IReqTokenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;

/**
 * @Author Shik
 * @Title: HeaderCheckerAspect
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/3 下午5:40
 */
@Slf4j
@Aspect
@Component
public class TokenCheckerAspect {

    @Autowired
    private IReqTokenService iReqTokenService;

    @Before("@within(tokenChecker)")
    public void doBeforeForClass(TokenChecker tokenChecker) {
        doBefore(tokenChecker);
    }

    @Before("@annotation(tokenChecker)")
    public void doBefore(TokenChecker tokenChecker) {
        HttpServletRequest request = currentRequest();
        if (Objects.isNull(request)) {
            log.info("without request, skip");
            throw new DefinitionException(ResponseStatus.ERROR_PARAMETER);
        }

        String headerName = tokenChecker.token();

        String token = request.getHeader(headerName);
        if(StringUtils.isBlank(token)) {
            token = request.getParameter(headerName);
        }

        if (StringUtils.isNotBlank(token)) {
            ReqToken byToken = this.iReqTokenService.getByToken(token);
            if (null != byToken && byToken.getStatus()) {
                return;
            } else {
                throw new DefinitionException(ResponseStatus.UNAUTHORIZED);
            }
        } else {
            throw new DefinitionException(ResponseStatus.UNAUTHORIZED);
        }

    }

    /**
     * Return request current thread bound or null if none bound.
     *
     * @return Current request or null
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(servletRequestAttributes).map(ServletRequestAttributes::getRequest).orElse(null);
    }

}
