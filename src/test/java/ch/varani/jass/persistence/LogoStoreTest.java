package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link LogoStore}: the logo of the application, chosen, replaced, removed, checked.
 */
final class LogoStoreTest {

  /** The size of the generated images, in pixels. */
  private static final int SIZE = 20;
  /** The first bytes of a PNG image. */
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  /** The temporary data folder. */
  @TempDir
  private Path folder;

  /**
   * Returns an image.
   *
   * @param format {@code png} or {@code jpg}.
   *
   * @return its content.
   *
   * @throws IOException never.
   */
  private static byte[] image(final String format) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB), format, out);
    return out.toByteArray();
  }

  /**
   * Q11: there is no logo at first; a PNG or JPEG image becomes the logo, replaces the previous one, and can be
   * removed.
   *
   * @param other another temporary folder, for the chosen images.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q11: a PNG or JPEG image becomes the logo, and can be replaced or removed")
  void logoTest(@TempDir final Path other) throws Exception {
    final LogoStore store = new LogoStore(folder.resolve("data"));
    final Optional<byte[]> none = store.load();
    store.save(Files.write(other.resolve("logo.png"), image("png")));
    final byte[] png = store.load().orElseThrow();
    store.save(Files.write(other.resolve("logo.jpg"), image("jpg")));
    final byte[] jpeg = store.load().orElseThrow();
    store.remove();
    assertAll(
        () -> assertEquals(Optional.empty(), none),
        () -> assertArrayEquals(image("png"), png),
        () -> assertArrayEquals(image("jpg"), jpeg),
        () -> assertEquals(Optional.empty(), store.load()));
  }

  /**
   * A PDF, a file that is not an image, a damaged PNG or a too large image is refused, and the logo is unchanged; a
   * logo file replaced by something else is reported.
   *
   * @param other another temporary folder, for the chosen files.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Only a readable PNG or JPEG image of limited size is accepted")
  void invalidTest(@TempDir final Path other) throws Exception {
    final LogoStore store = new LogoStore(folder);
    store.save(Files.write(other.resolve("logo.png"), image("png")));
    final Path pdf = Files.write(other.resolve("a.pdf"), Fixtures.PDF);
    final Path text = Files.writeString(other.resolve("a.png"), "not an image");
    final Path damaged = Files.write(other.resolve("b.png"), Arrays.copyOf(PNG_SIGNATURE, SIZE));
    final Path large = Files.write(other.resolve("c.png"), Arrays.copyOf(image("png"), LogoStore.MAX_SIZE + 1));
    assertAll(
        () -> assertError(FormatError.UNSUPPORTED_IMAGE, () -> store.save(pdf)),
        () -> assertError(FormatError.UNSUPPORTED_IMAGE, () -> store.save(text)),
        () -> assertError(FormatError.UNSUPPORTED_IMAGE, () -> store.save(damaged)),
        () -> assertError(FormatError.TOO_LARGE, () -> store.save(large)),
        () -> assertArrayEquals(image("png"), store.load().orElseThrow()));
    Files.writeString(folder.resolve(LogoStore.FILE_NAME), "replaced");
    assertError(FormatError.UNSUPPORTED_IMAGE, store::load);
  }

  /**
   * Asserts that an operation is refused with an error.
   *
   * @param error the expected error.
   * @param operation the operation.
   */
  private static void assertError(final FormatError error, final Executable operation) {
    assertEquals(error, assertThrows(FileFormatException.class, operation).getError());
  }

}
