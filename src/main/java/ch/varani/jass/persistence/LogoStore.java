package ch.varani.jass.persistence;

import ch.varani.jass.domain.AttachmentType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The logo of the application, printed on every generated document (Q11): an image chosen by the user, stored in the
 * data folder ({@link DataFolder}) as the file {@value #FILE_NAME}, so that it is the same for every edition and
 * included in the backup of the data folder.
 *
 * <p><b>Checks</b> ({@code AGENTS.md} §5): the image is a PNG or JPEG image, recognised by its content (magic bytes),
 * of at most {@link #MAX_SIZE} bytes, and readable by PDFBox, which draws it on the documents; it is checked again when
 * it is read, as the file may have been replaced.</p>
 *
 * <p>Not thread-safe: the operations must run one at a time, from a background thread.</p>
 */
public final class LogoStore {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The name of the file of the logo, in the data folder. */
  static final String FILE_NAME = "logo";
  /** The maximum size of the logo, 2 MiB. */
  public static final int MAX_SIZE = 2 * 1024 * 1024;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The file of the logo. */
  private final Path file;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Opens the logo of a data folder, creating the folder if needed.
   *
   * @param folder the data folder, usually {@link DataFolder#ofCurrentUser()}.
   *
   * @throws IOException if the folder cannot be created.
   */
  public LogoStore(final Path folder) throws IOException {
    this.file = Files.createDirectories(folder.toAbsolutePath()).resolve(FILE_NAME);
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Reads the logo.
   *
   * @return the content of the image, or nothing if no logo has been chosen.
   *
   * @throws FileFormatException with {@link FormatError#TOO_LARGE} or {@link FormatError#UNSUPPORTED_IMAGE} if the
   *                             file is not a valid logo any more.
   * @throws IOException if the file cannot be read.
   */
  public Optional<byte[]> load() throws FileFormatException, IOException {
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return Optional.of(read(file));
  }

  /**
   * Replaces the logo by an image chosen by the user, after checking it.
   *
   * @param source the image.
   *
   * @throws FileFormatException with {@link FormatError#TOO_LARGE} or {@link FormatError#UNSUPPORTED_IMAGE} if the
   *                             image is too large, or not a readable PNG or JPEG image; the logo is then unchanged.
   * @throws IOException if a file cannot be read or written.
   */
  public void save(final Path source) throws FileFormatException, IOException {
    final byte[] content = read(source);
    AtomicFiles.write(file, out -> out.write(content));
  }

  /**
   * Removes the logo: the documents are printed without logo.
   *
   * @throws IOException if the file cannot be deleted.
   */
  public void remove() throws IOException {
    Files.deleteIfExists(file);
  }

  /**
   * Reads and checks an image.
   *
   * @param image the file of the image.
   *
   * @return its content.
   *
   * @throws FileFormatException with {@link FormatError#TOO_LARGE} or {@link FormatError#UNSUPPORTED_IMAGE}.
   * @throws IOException if the file cannot be read.
   */
  private static byte[] read(final Path image) throws FileFormatException, IOException {
    final byte[] content;
    try (InputStream in = Files.newInputStream(image)) {
      content = in.readNBytes(MAX_SIZE + 1);
    }
    if (content.length > MAX_SIZE) {
      throw new FileFormatException(FormatError.TOO_LARGE);
    }
    final Optional<AttachmentType> type = AttachmentFiles.typeOf(content);
    if (type.isEmpty() || type.get() == AttachmentType.PDF) {
      throw new FileFormatException(FormatError.UNSUPPORTED_IMAGE);
    }
    try (PDDocument document = new PDDocument()) {
      PDImageXObject.createFromByteArray(document, content, FILE_NAME);
    } catch (IOException | IllegalArgumentException _) {
      throw new FileFormatException(FormatError.UNSUPPORTED_IMAGE);
    }
    return content;
  }

}
