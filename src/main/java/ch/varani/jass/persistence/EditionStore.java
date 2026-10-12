package ch.varani.jass.persistence;

import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.AttachmentType;
import ch.varani.jass.domain.Edition;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The editions stored in the data folder of the application ({@link DataFolder}): the user picks an edition from a
 * list and never handles a path, except to export, import or back up.
 *
 * <p><b>Layout</b>: {@code editions/<id>.jass} holds the editions, {@code trash/<id>.jass} the deleted ones; the file
 * name is the identifier of the edition ({@link StoredEdition}), independent of its name. Other files are ignored,
 * among them the temporary files, whose name starts with a dot.</p>
 *
 * <p><b>Operations</b>:</p>
 * <ul>
 *   <li>{@link #list()} reads the header of each edition only ({@link EditionReader#readHeader(InputStream)}); an
 *       unreadable file is reported, not hidden;</li>
 *   <li>{@link #create(Edition)}, {@link #save(UUID, Edition)}: every change is saved at once, atomically
 *       ({@link AtomicFiles}), so that a crash never leaves a partial file; the supporting documents referenced by
 *       the accounting are copied from the previous version of the file, the others are dropped;</li>
 *   <li>{@link #addAttachment(UUID, Path)} checks a document chosen by the user (PDF, PNG or JPEG by its content, at
 *       most {@link Attachment#MAX_SIZE} bytes) and stores it in the edition file at once, so that the file stays
 *       self-contained; the caller then refers to it from an entry of the accounting and saves the edition;
 *       {@link #exportAttachment(UUID, Attachment, Path)} copies a document out of the file, after checking its
 *       digest (e.g. to open it with the application of the system);</li>
 *   <li>{@link #open(UUID)} reads and validates a complete edition;</li>
 *   <li>{@link #delete(UUID)} moves the edition to the trash, {@link #restore(UUID)} brings it back,
 *       {@link #emptyTrash()} deletes the trashed editions for good;</li>
 *   <li>{@link #exportTo(UUID, Path)} copies an edition to a file chosen by the user; {@link #importFrom(Path)}
 *       validates a file completely and copies its edition into the store, with a new identifier when its identifier
 *       is already used (in the editions or in the trash);</li>
 *   <li>{@link #backUpEverything(Path)} writes a ZIP archive of the whole data folder.</li>
 * </ul>
 *
 * <p>The files written outside the data folder (export, backup) are also written atomically. The log mentions the
 * identifiers of the files only, never their content nor their path.</p>
 *
 * <p>Not thread-safe: the operations must run one at a time, from a background thread (e.g. the queue of the saves
 * of the application), never on the JavaFX Application Thread.</p>
 */
public final class EditionStore {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The folder of the editions, in the data folder. */
  static final String EDITIONS_FOLDER = "editions";
  /** The folder of the deleted editions, in the data folder. */
  static final String TRASH_FOLDER = "trash";
  /** The extension of the edition files. */
  static final String EXTENSION = ".jass";
  /** The logger of the store; it logs identifiers only. */
  private static final Logger LOGGER = System.getLogger(EditionStore.class.getName());

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The data folder. */
  private final Path folder;
  /** The folder of the editions. */
  private final Path editions;
  /** The folder of the deleted editions. */
  private final Path trash;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Opens the store of a data folder, creating its folders if needed.
   *
   * @param folder the data folder, usually {@link DataFolder#ofCurrentUser()}.
   *
   * @throws IOException if the folders cannot be created.
   */
  public EditionStore(final Path folder) throws IOException {
    this.folder = folder.toAbsolutePath();
    this.editions = Files.createDirectories(this.folder.resolve(EDITIONS_FOLDER));
    this.trash = Files.createDirectories(this.folder.resolve(TRASH_FOLDER));
  }

  // *******************************************************************************************************************
  // Editions
  // *******************************************************************************************************************

  /**
   * Lists the editions, by reading the header of each file.
   *
   * @return the headers of the editions, the most recent first, and the identifiers of the unreadable files.
   *
   * @throws IOException if the folder cannot be read.
   */
  public EditionList list() throws IOException {
    return listFolder(editions);
  }

  /**
   * Creates a new edition file with a new identifier.
   *
   * @param edition the edition.
   *
   * @return the identifier of the new file.
   *
   * @throws NoSuchFileException if the accounting refers to a supporting document, as a new file has none.
   * @throws IOException if the file cannot be written.
   */
  public UUID create(final Edition edition) throws IOException {
    if (!edition.ledger().attachments().isEmpty()) {
      throw new NoSuchFileException(edition.ledger().attachments().getFirst().fileName());
    }
    final UUID id = UUID.randomUUID();
    save(id, edition);
    return id;
  }

  /**
   * Reads a complete edition.
   *
   * @param id the identifier of the edition.
   *
   * @return the edition.
   *
   * @throws FileFormatException if the file is not a valid edition file, or with
   *                             {@link FormatError#INVALID_CONTENT} if it holds another identifier.
   * @throws IOException if the file does not exist or cannot be read.
   */
  public Edition open(final UUID id) throws FileFormatException, IOException {
    final StoredEdition stored = read(fileIn(editions, id));
    if (!stored.id().equals(id)) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
    return stored.edition();
  }

  /**
   * Saves an edition, atomically replacing its previous version; the supporting documents referenced by its
   * accounting are copied from the previous version.
   *
   * @param id the identifier of the edition.
   * @param edition the edition.
   *
   * @throws NoSuchFileException if a document referenced by the accounting is not in the previous version.
   * @throws IOException if the file cannot be written; the previous version is then kept.
   */
  public void save(final UUID id, final Edition edition) throws IOException {
    final Path file = fileIn(editions, id);
    write(file, new StoredEdition(id, edition), file, Map.of());
  }

  // *******************************************************************************************************************
  // Supporting documents
  // *******************************************************************************************************************

  /**
   * Stores a supporting document in an edition file: the document is read (at most {@link Attachment#MAX_SIZE}
   * bytes), recognised by its content (PDF, PNG or JPEG), named by its SHA-256 digest, and the file is rewritten
   * atomically with it, the edition unchanged. A document already stored is not stored twice. The document is kept
   * by the next saves only once an entry of the accounting refers to it.
   *
   * @param id the identifier of the edition.
   * @param source the document chosen by the user.
   *
   * @return the reference to the document, named after the source file, to attach to an entry of the accounting.
   *
   * @throws FileFormatException with {@link FormatError#TOO_LARGE} or {@link FormatError#UNSUPPORTED_ATTACHMENT} if
   *                             the document is too large, empty, or not a PDF, PNG or JPEG document; or if the
   *                             edition file is not valid.
   * @throws ch.varani.jass.domain.DomainException with
   *         {@link ch.varani.jass.domain.ValidationError#INVALID_CHARACTER} if the name of the source file contains a
   *         character that is not allowed.
   * @throws IOException if a file cannot be read or written; the edition file is then unchanged.
   */
  public Attachment addAttachment(final UUID id, final Path source) throws FileFormatException, IOException {
    final byte[] content;
    try (InputStream in = Files.newInputStream(source)) {
      content = in.readNBytes(Attachment.MAX_SIZE + 1);
    }
    if (content.length > Attachment.MAX_SIZE) {
      throw new FileFormatException(FormatError.TOO_LARGE);
    }
    final AttachmentType type = AttachmentFiles.typeOf(content)
        .orElseThrow(() -> new FileFormatException(FormatError.UNSUPPORTED_ATTACHMENT));
    final Attachment attachment = new Attachment(AttachmentFiles.sha256(content), type,
                                                 source.getFileName().toString(), content.length);
    final Path file = fileIn(editions, id);
    final StoredEdition stored = read(file);
    write(file, stored, file, Map.of(attachment.fileName(), content));
    return attachment;
  }

  /**
   * Copies a supporting document out of an edition file, after checking that its content matches its digest.
   *
   * @param id the identifier of the edition.
   * @param attachment the reference to the document.
   * @param target the file to write; it is replaced if it exists.
   *
   * @throws FileFormatException with {@link FormatError#DAMAGED_ATTACHMENT} if the content does not match its digest.
   * @throws NoSuchFileException if the edition file does not hold the document.
   * @throws IOException if a file cannot be read or written.
   */
  public void exportAttachment(final UUID id, final Attachment attachment, final Path target)
      throws FileFormatException, IOException {
    final byte[] content;
    try (ZipFile zip = new ZipFile(fileIn(editions, id).toFile())) {
      final ZipEntry entry = zip.getEntry(EditionXml.ATTACHMENTS + attachment.fileName());
      if (entry == null) {
        throw new NoSuchFileException(attachment.fileName());
      }
      try (InputStream in = zip.getInputStream(entry)) {
        content = in.readNBytes(Attachment.MAX_SIZE + 1);
      }
    }
    if (!AttachmentFiles.sha256(content).equals(attachment.sha256())) {
      throw new FileFormatException(FormatError.DAMAGED_ATTACHMENT);
    }
    AtomicFiles.write(target, out -> out.write(content));
  }

  // *******************************************************************************************************************
  // Trash
  // *******************************************************************************************************************

  /**
   * Moves an edition to the trash; a trashed edition with the same identifier is replaced.
   *
   * @param id the identifier of the edition.
   *
   * @throws IOException if the edition does not exist or cannot be moved.
   */
  public void delete(final UUID id) throws IOException {
    Files.move(fileIn(editions, id), fileIn(trash, id), StandardCopyOption.ATOMIC_MOVE,
               StandardCopyOption.REPLACE_EXISTING);
  }

  /**
   * Lists the editions of the trash, by reading the header of each file.
   *
   * @return the headers of the trashed editions, the most recent first, and the identifiers of the unreadable files.
   *
   * @throws IOException if the folder cannot be read.
   */
  public EditionList listTrash() throws IOException {
    return listFolder(trash);
  }

  /**
   * Brings an edition back from the trash.
   *
   * @param id the identifier of the edition.
   *
   * @throws FileAlreadyExistsException if an edition with the same identifier exists.
   * @throws IOException if the edition is not in the trash or cannot be moved.
   */
  public void restore(final UUID id) throws IOException {
    final Path target = fileIn(editions, id);
    if (Files.exists(target)) {
      throw new FileAlreadyExistsException(target.getFileName().toString());
    }
    Files.move(fileIn(trash, id), target, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Deletes the editions of the trash for good.
   *
   * @throws IOException if a file cannot be deleted.
   */
  public void emptyTrash() throws IOException {
    for (final Path file : editionFiles(trash)) {
      Files.delete(file);
    }
  }

  // *******************************************************************************************************************
  // Export, import and backup
  // *******************************************************************************************************************

  /**
   * Copies an edition to a file chosen by the user, e.g. to send it to another organiser.
   *
   * @param id the identifier of the edition.
   * @param target the file to write; it is replaced if it exists.
   *
   * @throws IOException if the edition does not exist, or if the file cannot be written.
   */
  public void exportTo(final UUID id, final Path target) throws IOException {
    final Path source = fileIn(editions, id);
    AtomicFiles.write(target, out -> Files.copy(source, out));
  }

  /**
   * Imports an edition file: reads and validates it completely, then saves its edition and its supporting documents
   * in the store, with the identifier of the file, or a new one if this identifier is already used by an edition or
   * by the trash.
   *
   * @param source the file to import.
   *
   * @return the identifier of the imported edition.
   *
   * @throws FileFormatException if the file is not a valid edition file; nothing is imported.
   * @throws IOException if the file cannot be read or the edition cannot be saved.
   */
  public UUID importFrom(final Path source) throws FileFormatException, IOException {
    final StoredEdition stored = read(source);
    final UUID known = stored.id();
    final UUID id = Files.exists(fileIn(editions, known)) || Files.exists(fileIn(trash, known))
                    ? UUID.randomUUID() : known;
    write(fileIn(editions, id), new StoredEdition(id, stored.edition()), source, Map.of());
    return id;
  }

  /**
   * Writes a ZIP archive of the whole data folder (editions, trash), e.g. to move to another computer. The entries
   * are the paths of the files relative to the data folder, with {@code /} as separator; the temporary files are left
   * out.
   *
   * @param target the archive to write; it is replaced if it exists.
   *
   * @throws IOException if a file cannot be read or the archive cannot be written.
   */
  public void backUpEverything(final Path target) throws IOException {
    final List<Path> files;
    try (Stream<Path> walk = Files.walk(folder)) {
      files = walk.filter(Files::isRegularFile)
                  .filter(file -> !file.getFileName().toString().startsWith(AtomicFiles.TEMPORARY_PREFIX))
                  .sorted()
                  .toList();
    }
    AtomicFiles.write(target, out -> {
      final ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
      for (final Path file : files) {
        zip.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace(File.separatorChar, '/')));
        Files.copy(file, zip);
        zip.closeEntry();
      }
      zip.finish();
    });
  }

  // *******************************************************************************************************************
  // Files
  // *******************************************************************************************************************

  /**
   * Lists the editions of a folder: reads the header of each edition file, sorts the headers by date, the most
   * recent first, then by name; the files that cannot be read, or whose identifier does not match their name, are
   * reported apart and logged with their identifier.
   *
   * @param directory the folder of the editions or of the trash.
   *
   * @return the list of the editions.
   *
   * @throws IOException if the folder cannot be read.
   */
  private static EditionList listFolder(final Path directory) throws IOException {
    final List<EditionHeader> headers = new ArrayList<>();
    final List<UUID> unreadable = new ArrayList<>();
    for (final Path file : editionFiles(directory)) {
      final UUID id = idOf(file).orElseThrow();
      try (InputStream in = Files.newInputStream(file)) {
        final EditionHeader header = EditionReader.readHeader(in);
        if (!header.id().equals(id)) {
          throw new FileFormatException(FormatError.INVALID_CONTENT);
        }
        headers.add(header);
      } catch (FileFormatException | IOException _) {
        LOGGER.log(Level.WARNING, "The edition file {0} cannot be read.", id);
        unreadable.add(id);
      }
    }
    headers.sort(Comparator.comparing(EditionHeader::date).reversed().thenComparing(EditionHeader::name));
    unreadable.sort(Comparator.naturalOrder());
    return new EditionList(headers, unreadable);
  }

  /**
   * Returns the edition files of a folder: the files named {@code <id>.jass}, with a UUID in its canonical form.
   *
   * @param directory the folder of the editions or of the trash.
   *
   * @return the edition files.
   *
   * @throws IOException if the folder cannot be read.
   */
  private static List<Path> editionFiles(final Path directory) throws IOException {
    final List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
      for (final Path file : stream) {
        if (idOf(file).isPresent()) {
          files.add(file);
        }
      }
    }
    return files;
  }

  /**
   * Returns the identifier of an edition file, given by its name.
   *
   * @param file the file, named {@code <id>.jass}.
   *
   * @return the identifier, or nothing if the name is not a UUID in its canonical form followed by the extension.
   */
  static Optional<UUID> idOf(final Path file) {
    final String name = file.getFileName().toString();
    final String base = name.substring(0, Math.max(0, name.length() - EXTENSION.length()));
    try {
      final UUID id = UUID.fromString(base);
      return Optional.of(id).filter(uuid -> (uuid + EXTENSION).equals(name));
    } catch (IllegalArgumentException _) {
      return Optional.empty();
    }
  }

  /**
   * Returns the file of an edition in a folder.
   *
   * @param directory the folder of the editions or of the trash.
   * @param id the identifier of the edition.
   *
   * @return the path {@code <directory>/<id>.jass}.
   */
  private static Path fileIn(final Path directory, final UUID id) {
    return directory.resolve(id + EXTENSION);
  }

  /**
   * Writes an edition file atomically, with the supporting documents referenced by the accounting of the edition, plus
   * the added ones and, when documents are added, the documents already in the file.
   *
   * <p>The contents are copied from the archive {@code contents} (closed before the new file replaces the target, as
   * Windows cannot replace an open file) or taken from the added ones.</p>
   *
   * @param target the file to write.
   * @param stored the edition and its identifier.
   * @param contents the archive holding the contents of the documents, usually the previous version of the target; it
   *                 may not exist.
   * @param added the contents of new documents, by name ({@link Attachment#fileName()}).
   *
   * @throws NoSuchFileException if the content of a document cannot be found.
   * @throws IOException if a file cannot be read or written.
   */
  private static void write(final Path target, final StoredEdition stored, final Path contents,
                            final Map<String, byte[]> added) throws IOException {
    final Set<String> names = new TreeSet<>(added.keySet());
    stored.edition().ledger().attachments().forEach(attachment -> names.add(attachment.fileName()));
    final boolean archive = Files.exists(contents);
    if (archive && !added.isEmpty()) {
      try (ZipFile zip = new ZipFile(contents.toFile())) {
        for (final ZipEntry entry : zip.stream().toList()) {
          if (entry.getName().startsWith(EditionXml.ATTACHMENTS)) {
            names.add(entry.getName().substring(EditionXml.ATTACHMENTS.length()));
          }
        }
      }
    }
    AtomicFiles.write(target, out -> {
      try (ZipFile zip = archive ? new ZipFile(contents.toFile()) : null) {
        EditionWriter.write(stored, out, names, (name, entry) -> copy(zip, added, name, entry));
      }
    });
  }

  /**
   * Copies the content of a document: an added one, or the entry of the archive.
   *
   * @param zip the archive holding the contents, or {@code null} if there is none.
   * @param added the contents of the added documents, by name.
   * @param name the name of the document.
   * @param out the stream of the new entry.
   *
   * @throws NoSuchFileException if the content cannot be found.
   * @throws IOException if the content cannot be copied.
   */
  private static void copy(final ZipFile zip, final Map<String, byte[]> added, final String name,
                           final OutputStream out) throws IOException {
    final byte[] content = added.get(name);
    if (content != null) {
      out.write(content);
      return;
    }
    final ZipEntry entry = zip == null ? null : zip.getEntry(EditionXml.ATTACHMENTS + name);
    if (entry == null) {
      throw new NoSuchFileException(name);
    }
    try (InputStream in = zip.getInputStream(entry)) {
      in.transferTo(out);
    }
  }

  /**
   * Reads and validates a complete edition file.
   *
   * @param file the file.
   *
   * @return the edition and the identifier written in the file.
   *
   * @throws FileFormatException if the file is not a valid edition file.
   * @throws IOException if the file does not exist or cannot be read.
   */
  private static StoredEdition read(final Path file) throws FileFormatException, IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return EditionReader.read(in);
    }
  }

}
