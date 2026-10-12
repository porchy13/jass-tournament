package ch.varani.jass.persistence;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes files atomically, so that a crash or a full disk never leaves a partial file ({@code AGENTS.md} §5): the
 * content is written into a temporary file of the same folder, synchronised to the disk, then moved over the target
 * in one operation. The temporary file is deleted if anything fails, and the previous version of the target is then
 * kept.
 *
 * <p>The temporary files are hidden: their name starts with {@link #TEMPORARY_PREFIX}. Used by the stores of this
 * package and by the user interface for the generated documents.</p>
 *
 * <p>Utility class without state, safe for concurrent use on different targets.</p>
 */
public final class AtomicFiles {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The prefix of the temporary files, which makes them hidden and ignored. */
  static final String TEMPORARY_PREFIX = ".";
  /** The suffix of the temporary files. */
  private static final String TEMPORARY_SUFFIX = ".tmp";
  /** The size of the buffer of the written files, which are synchronised to the disk at each write. */
  private static final int BUFFER_SIZE = 64 * 1024;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private AtomicFiles() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes a file atomically.
   *
   * @param target the file to write; it is replaced if it exists.
   * @param content writes the content of the file.
   *
   * @throws IOException if the file cannot be written; the previous version of the target is then kept.
   */
  public static void write(final Path target, final Content content) throws IOException {
    final Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), TEMPORARY_PREFIX,
                                                TEMPORARY_SUFFIX);
    try {
      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary, StandardOpenOption.WRITE,
                                                                             StandardOpenOption.SYNC),
                                                       BUFFER_SIZE)) {
        content.writeTo(out);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * Writes the content of a file into a stream.
   */
  @FunctionalInterface
  public interface Content {

    /**
     * Writes the content into a stream, which is not closed.
     *
     * @param out the stream of the file.
     *
     * @throws IOException if the stream cannot be written.
     */
    void writeTo(OutputStream out) throws IOException;

  }

}
