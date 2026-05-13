package org.tinycircl.pandoc4j.binary;

import org.tinycircl.pandoc4j.exception.PandocException;

public class PandocBinaryException extends PandocException {

    public PandocBinaryException(String message) {
        super(message);
    }

    public PandocBinaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
