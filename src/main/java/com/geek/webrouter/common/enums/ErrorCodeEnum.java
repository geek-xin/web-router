package com.geek.webrouter.common.enums;

import lombok.Getter;

/**
 * 错误码枚举。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Getter
public enum ErrorCodeEnum {

    /** 操作成功。 */
    SUCCESS(200, "操作成功"),

    /** 参数错误。 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 资源不存在。 */
    NOT_FOUND(404, "资源不存在"),

    /** 路由配置名称重复。 */
    DUPLICATE_NAME(409, "路由名称已存在"),

    /** 路由配置路径前缀冲突。 */
    DUPLICATE_PREFIX(409, "路径前缀已被其他路由使用"),

    /** 路由配置目标地址重复。 */
    DUPLICATE_TARGET(409, "目标地址已存在"),

    /** 本地监听地址重复。 */
    DUPLICATE_LOCAL_BINDING(409, "本地监听地址已存在"),

    /** 服务器内部错误。 */
    INTERNAL_ERROR(500, "服务器内部错误"),

    /** 配置文件读写失败。 */
    CONFIG_IO_ERROR(500, "配置文件读写失败");

    /** 错误码。 */
    private final int code;

    /** 错误消息。 */
    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
