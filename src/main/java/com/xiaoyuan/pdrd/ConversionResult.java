package com.xiaoyuan.pdrd;

/**
 * Holds the outcome of a Pandoc conversion process.
 */
public final class ConversionResult {

    private final int exitCode;
    private final String output;
    private final String stderr;

    public ConversionResult(int exitCode, String output, String stderr) {
        this.exitCode = exitCode;
        this.output = output;
        this.stderr = stderr;
    }

    /** Returns {@code true} when Pandoc exited with code 0. */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /** The converted document as a string (stdout of the Pandoc process). */
    public String getOutput() {
        return output;
    }

    /** Pandoc's stderr output – may contain warnings even on success. */
    public String getStderr() {
        return stderr;
    }

    /** Raw process exit code. */
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public String toString() {
        return "ConversionResult{exitCode=" + exitCode +
               ", output.length=" + (output == null ? 0 : output.length()) +
               ", stderr='" + stderr + "'}";
    }
}
