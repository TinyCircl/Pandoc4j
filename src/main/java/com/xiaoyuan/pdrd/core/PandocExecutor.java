package com.xiaoyuan.pdrd.core;

import com.xiaoyuan.pdrd.ConversionResult;
import com.xiaoyuan.pdrd.exception.PandocConversionException;
import com.xiaoyuan.pdrd.exception.PandocException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Low-level executor that invokes the Pandoc process via {@link ProcessBuilder}.
 *
 * <p>This class is intentionally package-private. External callers should use
 * {@link com.xiaoyuan.pdrd.Pandoc4j} or {@link com.xiaoyuan.pdrd.ConversionRequest}.
 */
public final class PandocExecutor {

    /** Default timeout in seconds before a conversion is forcibly killed. */
    public static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private final PandocInstallation installation;
    private final long timeoutSeconds;

    public PandocExecutor(PandocInstallation installation) {
        this(installation, DEFAULT_TIMEOUT_SECONDS);
    }

    public PandocExecutor(PandocInstallation installation, long timeoutSeconds) {
        this.installation = installation;
        this.timeoutSeconds = timeoutSeconds;
    }

    // ── Execute with file I/O ─────────────────────────────────────────────

    /**
     * Runs Pandoc with the given arguments. Stdout is captured as the result string.
     *
     * @param args full argument list (must not include the pandoc binary itself)
     * @return {@link ConversionResult} on success
     * @throws PandocConversionException if Pandoc exits with a non-zero code
     * @throws PandocException           if the process cannot be started or times out
     */
    public ConversionResult execute(List<String> args) {
        return execute(args, null, null);
    }

    /**
     * Runs Pandoc with the given arguments, optionally writing {@code stdinContent}
     * to the process stdin (used for text-based conversion).
     *
     * @param args         full argument list
     * @param stdinContent text to pipe into Pandoc stdin, or {@code null}
     */
    public ConversionResult execute(List<String> args, String stdinContent) {
        return execute(args, stdinContent, null);
    }

    /**
     * Runs Pandoc with the given arguments in the specified working directory.
     *
     * <p>Setting a per-request {@code workingDir} ensures that concurrent conversions
     * do not interfere with each other via relative-path side-effects
     * (e.g. {@code --extract-media}, Pandoc's own temp files).
     *
     * @param args         full argument list
     * @param stdinContent text to pipe into Pandoc stdin, or {@code null}
     * @param workingDir   directory to use as Pandoc's working directory, or {@code null}
     *                     to inherit the JVM's working directory
     */
    public ConversionResult execute(List<String> args, String stdinContent, Path workingDir) {
        List<String> command = buildCommand(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("LANG", "en_US.UTF-8");
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new PandocException("Failed to start Pandoc process: " + e.getMessage(), e);
        }

        // Write stdin in a separate thread to avoid blocking on full pipe buffers
        Thread stdinThread = null;
        if (stdinContent != null) {
            final String content = stdinContent;
            stdinThread = new Thread(() -> {
                try (OutputStream os = process.getOutputStream();
                     Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    writer.write(content);
                } catch (IOException ignored) {
                    // Broken pipe is expected when Pandoc exits early
                }
            });
            stdinThread.setDaemon(true);
            stdinThread.start();
        } else {
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {}
        }

        // Read stdout and stderr concurrently
        StringBuilderReader stdoutReader = new StringBuilderReader(process.getInputStream());
        StringBuilderReader stderrReader = new StringBuilderReader(process.getErrorStream());

        Thread stdoutThread = new Thread(stdoutReader);
        Thread stderrThread = new Thread(stderrReader);
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PandocException("Pandoc process was interrupted", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new PandocException(
                    "Pandoc process timed out after " + timeoutSeconds + " seconds. " +
                    "Command: " + String.join(" ", command));
        }

        // Join reader threads
        try {
            stdoutThread.join(5_000);
            stderrThread.join(5_000);
            if (stdinThread != null) {
                stdinThread.join(5_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitCode = process.exitValue();
        String stdout = stdoutReader.getContent();
        String stderr  = stderrReader.getContent();

        if (exitCode != 0) {
            throw new PandocConversionException(exitCode, stderr);
        }

        return new ConversionResult(exitCode, stdout, stderr);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private List<String> buildCommand(List<String> args) {
        List<String> command = new java.util.ArrayList<>();
        command.add(installation.getExecutablePath().toString());
        command.addAll(args);
        return command;
    }

    /** Runnable that drains an {@link InputStream} into a {@link StringBuilder}. */
    private static final class StringBuilderReader implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder content = new StringBuilder();

        StringBuilderReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
            } catch (IOException ignored) {}
        }

        String getContent() {
            return content.toString();
        }
    }
}
