package com.company.global.exception;

// H-NEW-3: Mattermost 호출 타임아웃/연결 실패 → HTTP 503 (빠른 실패)
public class MattermostUnavailableException extends RuntimeException {

    public MattermostUnavailableException(String message) {
        super(message);
    }
}
