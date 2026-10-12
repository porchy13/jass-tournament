package ch.varani.jass.persistence;

import ch.varani.jass.directory.Directory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The directory stored in the data folder of the application ({@link DataFolder}), in the file
 * {@value #FILE_NAME}, shared by all the editions and kept on this computer only; and the text files of its import and
 * export.
 *
 * <p><b>Operations</b>:</p>
 * <ul>
 *   <li>{@link #load()} reads and validates the directory ({@link DirectoryReader}); the directory is empty before
 *       its first save;</li>
 *   <li>{@link #save(Directory)} writes it at once, atomically ({@link AtomicFiles}): a crash never leaves a partial
 *       file, and a person deleted from the directory is really gone from the file;</li>
 *   <li>{@link #readText(Path)} reads a text file chosen by the user to import persons (e.g. a CSV file): at most
 *       {@link #MAX_IMPORT_SIZE} bytes, UTF-8 only, rejected otherwise; its content is never executed nor
 *       evaluated;</li>
 *   <li>{@link #writeText(Path, String)} writes an export (e.g. a CSV file) atomically, in UTF-8 with a byte order
 *       mark, so that the spreadsheets recognise the encoding.</li>
 * </ul>
 *
 * <p>The backup of the whole data folder ({@link EditionStore#backUpEverything(Path)}) includes the directory. Nothing
 * of the directory is logged ({@code AGENTS.md} §5).</p>
 *
 * <p>Not thread-safe: the operations must run one at a time, from a background thread, never on the JavaFX
 * Application Thread.</p>
 */
public final class DirectoryStore {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The name of the directory file, in the data folder. */
  static final String FILE_NAME = "directory.jassdir";
  /** The maximum size of a text file to import, 4 MiB: tens of thousands of rows. */
  public static final int MAX_IMPORT_SIZE = 4 * 1024 * 1024;
  /** The byte order mark written at the start of the exports. */
  private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The directory file. */
  private final Path file;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Opens the directory of a data folder, creating the folder if needed.
   *
   * @param folder the data folder, usually {@link DataFolder#ofCurrentUser()}.
   *
   * @throws IOException if the folder cannot be created.
   */
  public DirectoryStore(final Path folder) throws IOException {
    this.file = Files.createDirectories(folder.toAbsolutePath()).resolve(FILE_NAME);
  }

  // *******************************************************************************************************************
  // Directory
  // *******************************************************************************************************************

  /**
   * Reads the directory.
   *
   * @return the directory, empty if it has never been saved.
   *
   * @throws FileFormatException if the file is not a valid directory file.
   * @throws IOException if the file cannot be read.
   */
  public Directory load() throws FileFormatException, IOException {
    if (!Files.exists(file)) {
      return Directory.empty();
    }
    try (InputStream in = Files.newInputStream(file)) {
      return DirectoryReader.read(in);
    }
  }

  /**
   * Saves the directory, atomically replacing its previous version.
   *
   * @param directory the directory.
   *
   * @throws IOException if the file cannot be written; the previous version is then kept.
   */
  public void save(final Directory directory) throws IOException {
    AtomicFiles.write(file, out -> DirectoryWriter.write(directory, out));
  }

  // *******************************************************************************************************************
  // Import and export
  // *******************************************************************************************************************

  /**
   * Reads a text file to import, e.g. a CSV file.
   *
   * @param source the file chosen by the user.
   *
   * @return the text of the file.
   *
   * @throws FileFormatException with {@link FormatError#TOO_LARGE} if the file has more than
   *                             {@link #MAX_IMPORT_SIZE} bytes, or with {@link FormatError#INVALID_ENCODING} if it is
   *                             not encoded in UTF-8.
   * @throws IOException if the file cannot be read.
   */
  public static String readText(final Path source) throws FileFormatException, IOException {
    final byte[] bytes;
    try (InputStream in = Files.newInputStream(source)) {
      bytes = in.readNBytes(MAX_IMPORT_SIZE + 1);
    }
    if (bytes.length > MAX_IMPORT_SIZE) {
      throw new FileFormatException(FormatError.TOO_LARGE);
    }
    try {
      return StandardCharsets.UTF_8.newDecoder()
                                   .onMalformedInput(CodingErrorAction.REPORT)
                                   .onUnmappableCharacter(CodingErrorAction.REPORT)
                                   .decode(ByteBuffer.wrap(bytes))
                                   .toString();
    } catch (CharacterCodingException _) {
      throw new FileFormatException(FormatError.INVALID_ENCODING);
    }
  }

  /**
   * Writes an export atomically, in UTF-8 preceded by a byte order mark.
   *
   * @param target the file chosen by the user; it is replaced if it exists.
   * @param text the text of the export.
   *
   * @throws IOException if the file cannot be written.
   */
  public static void writeText(final Path target, final String text) throws IOException {
    AtomicFiles.write(target, out -> {
      out.write(BYTE_ORDER_MARK);
      out.write(text.getBytes(StandardCharsets.UTF_8));
    });
  }

}
