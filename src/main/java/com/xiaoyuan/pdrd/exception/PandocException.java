package com.xiaoyuan.pdrd.exception;

public class PandocException extends RuntimeException {

    public PandocException(String message) {
        super(message);
    }

    public PandocException(String message, Throwable cause) {
        super(message, cause);
    }
}
