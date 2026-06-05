package com.geek.webrouter.common.exception;

import com.geek.webrouter.common.enums.ErrorCodeEnum;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务异常。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public class BusinessException extends RuntimeException implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final int code;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCodeEnum errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.code = errorCode.getCode();
    }
}
