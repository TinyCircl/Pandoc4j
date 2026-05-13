package org.tinycircl.pandoc4j.exception;

public class PandocConversionException extends PandocException {

    private final int exitCode;
    private final String stderr;

    public PandocConversionException(int exitCode, String stderr) {
        super("Pandoc conversion failed with exit code " + exitCode + ". stderr: " + stderr);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}
