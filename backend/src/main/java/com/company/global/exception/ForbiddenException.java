package com.company.global.exception;

/**
 * 403 Forbidden — 본인의 리소스가 아닌 경우 등 권한이 없을 때 사용
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
