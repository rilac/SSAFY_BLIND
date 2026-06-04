package com.company.global.exception;

// C-NEW-2: 로그인 시도 레이트리밋 초과 → HTTP 429
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
