package ch.varani.jass.persistence;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
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
 * <p><b>Windows</b>: the target cannot be replaced while another program holds it open (an antivirus, the indexing
 * of the files, a backup), which fails with an {@link AccessDeniedException}; the move is then tried again, up to
 * {@value #MOVE_ATTEMPTS} times, {@value #MOVE_PAUSE} ms apart.</p>
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
  /** The number of attempts to move the temporary file over a target held open by another program. */
  static final int MOVE_ATTEMPTS = 10;
  /** The pause between two attempts to move the temporary file, in milliseconds. */
  static final long MOVE_PAUSE = 50;

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
    write(target, content, AtomicFiles::move);
  }

  /**
   * Writes a file atomically, with the given move of the temporary file over the target.
   *
   * @param target the file to write; it is replaced if it exists.
   * @param content writes the content of the file.
   * @param mover moves the temporary file over the target, {@link #move(Path, Path)} except in the tests.
   *
   * @throws IOException if the file cannot be written; the previous version of the target is then kept.
   */
  static void write(final Path target, final Content content, final Mover mover) throws IOException {
    final Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), TEMPORARY_PREFIX,
                                                TEMPORARY_SUFFIX);
    try {
      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary, StandardOpenOption.WRITE,
                                                                             StandardOpenOption.SYNC),
                                                       BUFFER_SIZE)) {
        content.writeTo(out);
      }
      replace(temporary, target, mover);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /**
   * Moves the temporary file over the target, trying again while the target is held open by another program.
   *
   * @param temporary the temporary file.
   * @param target the file to replace.
   * @param mover moves the temporary file over the target.
   *
   * @throws AccessDeniedException if the target is still held open after {@value #MOVE_ATTEMPTS} attempts, or if the
   *                               thread is interrupted while waiting.
   * @throws IOException if the file cannot be moved for another reason.
   */
  private static void replace(final Path temporary, final Path target, final Mover mover) throws IOException {
    for (int attempt = 1; ; attempt++) {
      try {
        mover.move(temporary, target);
        return;
      } catch (final AccessDeniedException e) {
        if (attempt == MOVE_ATTEMPTS) {
          throw e;
        }
        pause(e);
      }
    }
  }

  /**
   * Waits before the next attempt to move the temporary file.
   *
   * @param failure the failure of the last attempt, thrown if the thread is interrupted.
   *
   * @throws AccessDeniedException if the thread is interrupted while waiting; its interrupted status is kept.
   */
  private static void pause(final AccessDeniedException failure) throws AccessDeniedException {
    try {
      Thread.sleep(MOVE_PAUSE);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failure;
    }
  }

  /**
   * Moves the temporary file over the target in one operation.
   *
   * @param temporary the temporary file.
   * @param target the file to replace.
   *
   * @throws IOException if the file cannot be moved.
   */
  static void move(final Path temporary, final Path target) throws IOException {
    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * Moves the temporary file over the target.
   */
  @FunctionalInterface
  interface Mover {

    /**
     * Moves a file over another one.
     *
     * @param temporary the temporary file.
     * @param target the file to replace.
     *
     * @throws IOException if the file cannot be moved.
     */
    void move(Path temporary, Path target) throws IOException;

  }

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
