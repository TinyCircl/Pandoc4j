package org.tinycircl.pandoc4j.exception;

public class PandocException extends RuntimeException {

    public PandocException(String message) {
        super(message);
    }

    public PandocException(String message, Throwable cause) {
        super(message, cause);
    }
}
