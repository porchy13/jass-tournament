package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link AtomicFiles}: the move of the temporary file over a target held open by another program, as on
 * Windows; the ordinary writes are tested through the stores.
 */
final class AtomicFilesTest {

  /** The content of the target before the write. */
  private static final String BEFORE = "before";
  /** The content written. */
  private static final String AFTER = "after";
  /** The number of failed attempts before the target is released. */
  private static final int HELD = 2;

  /** The folder of the files. */
  @TempDir
  private Path folder;

  /**
   * Creates the target with its previous content.
   *
   * @return the target.
   *
   * @throws IOException if the file cannot be written.
   */
  private Path target() throws IOException {
    return Files.writeString(folder.resolve("file"), BEFORE);
  }

  /**
   * Writes the new content into the target.
   *
   * @param target the target.
   * @param mover moves the temporary file over the target.
   *
   * @throws IOException if the file cannot be written.
   */
  private static void write(final Path target, final AtomicFiles.Mover mover) throws IOException {
    AtomicFiles.write(target, out -> out.write(AFTER.getBytes(StandardCharsets.UTF_8)), mover);
  }

  /**
   * Counts the files of the folder, the temporary files included.
   *
   * @return the number of files.
   *
   * @throws IOException if the folder cannot be read.
   */
  private long files() throws IOException {
    try (Stream<Path> files = Files.list(folder)) {
      return files.count();
    }
  }

  /**
   * A target held open for a while is replaced once it is released.
   *
   * @throws Exception if a file cannot be read or written.
   */
  @Test
  @DisplayName("A target held open by another program is replaced once released")
  void retryTest() throws Exception {
    final Path target = target();
    final AtomicInteger attempts = new AtomicInteger();
    write(target, (temporary, file) -> {
      if (attempts.incrementAndGet() <= HELD) {
        throw new AccessDeniedException(file.toString());
      }
      AtomicFiles.move(temporary, file);
    });
    assertAll(
        () -> assertEquals(AFTER, Files.readString(target)),
        () -> assertEquals(HELD + 1, attempts.get()),
        () -> assertEquals(1, files()));
  }

  /**
   * A target never released is kept, and the failure is reported after the last attempt.
   *
   * @throws Exception if a file cannot be read or written.
   */
  @Test
  @DisplayName("A target never released is kept and the write fails")
  void heldTest() throws Exception {
    final Path target = target();
    final AtomicInteger attempts = new AtomicInteger();
    final AtomicFiles.Mover held = (temporary, file) -> {
      attempts.incrementAndGet();
      throw new AccessDeniedException(file.toString());
    };
    assertThrows(AccessDeniedException.class, () -> write(target, held));
    assertAll(
        () -> assertEquals(BEFORE, Files.readString(target)),
        () -> assertEquals(AtomicFiles.MOVE_ATTEMPTS, attempts.get()),
        () -> assertEquals(1, files()));
  }

  /**
   * An interrupted thread does not wait for the target to be released.
   *
   * @throws Exception if a file cannot be read or written.
   */
  @Test
  @DisplayName("An interrupted write stops waiting and keeps the interruption")
  void interruptedTest() throws Exception {
    final Path target = target();
    final AtomicInteger attempts = new AtomicInteger();
    final AtomicFiles.Mover held = (temporary, file) -> {
      attempts.incrementAndGet();
      throw new AccessDeniedException(file.toString());
    };
    Thread.currentThread().interrupt();
    try {
      assertThrows(AccessDeniedException.class, () -> write(target, held));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
    assertAll(
        () -> assertEquals(BEFORE, Files.readString(target)),
        () -> assertEquals(1, attempts.get()));
  }

}
