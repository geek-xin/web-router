package com.geek.webrouter.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geek.webrouter.common.enums.ErrorCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * @param <T> 响应数据类型
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    /** 操作是否成功。 */
    private boolean success;

    /** 业务状态码。 */
    private int code;

    /** 提示消息。 */
    private String message;

    /** 响应数据。 */
    private T data;

    /** 服务器时间戳。 */
    private long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.code = ErrorCodeEnum.SUCCESS.getCode();
        result.message = ErrorCodeEnum.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ErrorCodeEnum errorCode) {
        Result<T> result = new Result<>();
        result.success = false;
        result.code = errorCode.getCode();
        result.message = errorCode.getMessage();
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.code = code;
        result.message = message;
        return result;
    }
}
