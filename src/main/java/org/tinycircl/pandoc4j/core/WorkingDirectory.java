package org.tinycircl.pandoc4j.core;

import org.tinycircl.pandoc4j.ConversionRequest;
import org.tinycircl.pandoc4j.exception.PandocException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A per-conversion isolated working directory for Pandoc processes.
 *
 * <p>In a concurrent web-service scenario, multiple conversions may run
 * simultaneously. Without isolation, side-effects such as
 * {@code --extract-media} writes, Pandoc's own temporary files, and
 * relative-path resolution can collide between requests.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Managed</b> – created by {@link #createTemp()}.
 *       The directory is deleted recursively when {@link #close()} is called.</li>
 *   <li><b>Unmanaged</b> – created by {@link #of(Path)}.
 *       The caller owns the directory; {@link #close()} is a no-op.</li>
 * </ul>
 *
 * <p>Typical usage inside {@link ConversionRequest}:
 * <pre>{@code
 * try (WorkingDirectory wd = WorkingDirectory.createTemp()) {
 *     executor.execute(args, stdin, wd.getPath());
 * }
 * }</pre>
 *
 * <p>If you want Pandoc to extract media to a <em>persistent</em> location,
 * use an absolute path with {@code extractMedia("/absolute/path/to/media")} –
 * the temp dir cleanup will not affect files written outside it.
 */
public final class WorkingDirectory implements AutoCloseable {

    private static final String TEMP_DIR_PREFIX = "pandoc4j-";

    private final Path path;
    private final boolean managed;

    private WorkingDirectory(Path path, boolean managed) {
        this.path = path;
        this.managed = managed;
    }

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Creates a new unique temporary working directory under the system temp folder.
     * The directory will be deleted (with all its contents) when {@link #close()} is called.
     *
     * @throws PandocException if the directory cannot be created
     */
    public static WorkingDirectory createTemp() {
        try {
            Path dir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            return new WorkingDirectory(dir, true);
        } catch (IOException e) {
            throw new PandocException("Failed to create temporary working directory: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps an existing directory as an unmanaged {@link WorkingDirectory}.
     * {@link #close()} is a no-op; the caller is responsible for the directory lifecycle.
     *
     * @throws IllegalArgumentException if {@code path} does not point to an existing directory
     */
    public static WorkingDirectory of(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not an existing directory: " + path);
        }
        return new WorkingDirectory(path, false);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns the absolute path of this working directory.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns {@code true} if this is a managed (temporary) directory that will be
     * deleted on {@link #close()}.
     */
    public boolean isManaged() {
        return managed;
    }

    /**
     * Deletes this directory and all its contents if it is <em>managed</em>.
     * For unmanaged instances this is a no-op.
     *
     * <p>Deletion errors are silently suppressed so that a cleanup failure never
     * masks the original conversion result.
     */
    @Override
    public void close() {
        if (!managed) {
            return;
        }
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
            // Intentional: cleanup failure must not propagate
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public String toString() {
        return "WorkingDirectory{path=" + path + ", managed=" + managed + "}";
    }
}
