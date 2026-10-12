package ch.varani.jass.service;

import ch.varani.jass.directory.Directory;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.persistence.AtomicFiles;
import ch.varani.jass.persistence.DirectoryStore;
import ch.varani.jass.persistence.EditionList;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.persistence.FileFormatException;
import ch.varani.jass.persistence.LogoStore;
import ch.varani.jass.persistence.SettingsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The use cases of the data folder of the application, for the user interface: the editions (list, create, open,
 * save, trash, export, import, backup), the directory, the logo and the settings of the interface.
 *
 * <p><b>Saving an edition</b> ({@link #save(UUID, Edition)}): the edition file is written at once, then the
 * participations of its players are recorded in the directory ({@link Directory#recordParticipations(UUID, Edition)})
 * and the directory is saved if they changed. When the directory could not be read at start-up (damaged file), it is
 * left untouched, never replaced by an empty one, and {@link #directoryError()} reports the problem.</p>
 *
 * <p><b>Supporting documents</b>: a document chosen by the user is stored in the edition file
 * ({@link #addAttachment(UUID, Path)}); to be opened by the application of the system, it is copied
 * ({@link #openCopy(UUID, Attachment)}) into a temporary folder of this session, readable by its owner only, which
 * {@link #deleteCopies()} deletes when the application exits.</p>
 *
 * <p>The operations read and write files: they must run one at a time, in order, on a background thread (the queue
 * of the user interface), never on the JavaFX Application Thread. Not thread-safe.</p>
 */
public final class StoreService {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The prefix of the temporary folder of the copies of the documents opened. */
  private static final String COPIES_FOLDER = "jass-documents";
  /** The characters that cannot appear in a file name on some systems, replaced in the names of the copies. */
  private static final Pattern FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The editions. */
  private final EditionStore editions;
  /** The file of the directory. */
  private final DirectoryStore directoryStore;
  /** The logo. */
  private final LogoStore logo;
  /** The settings of the user interface. */
  private final SettingsStore settings;
  /** The directory, or {@code null} if it could not be read. */
  private Directory directory;
  /** The problem met when reading the directory, or {@code null}. */
  private final Exception directoryError;
  /** The temporary folder of the copies of the documents opened, or {@code null} before the first one. */
  private Path copies;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Opens the data folder: its editions, its logo, and reads its directory.
   *
   * @param folder the data folder.
   *
   * @throws IOException if the folders cannot be created.
   */
  public StoreService(final Path folder) throws IOException {
    editions = new EditionStore(folder);
    directoryStore = new DirectoryStore(folder);
    logo = new LogoStore(folder);
    settings = new SettingsStore(folder);
    Directory loaded = null;
    Exception error = null;
    try {
      loaded = directoryStore.load();
    } catch (final FileFormatException | IOException e) {
      error = e;
    }
    directory = loaded;
    directoryError = error;
  }

  // *******************************************************************************************************************
  // Editions
  // *******************************************************************************************************************

  /**
   * Lists the editions.
   *
   * @return the headers of the editions and the unreadable files.
   *
   * @throws IOException if the folder cannot be read.
   */
  public EditionList list() throws IOException {
    return editions.list();
  }

  /**
   * Creates a new edition file and records the participations of its players.
   *
   * @param edition the new edition.
   *
   * @return the identifier of the edition.
   *
   * @throws IOException if a file cannot be written.
   */
  public UUID create(final Edition edition) throws IOException {
    final UUID id = editions.create(edition);
    recordParticipations(id, edition);
    return id;
  }

  /**
   * Reads an edition.
   *
   * @param id the identifier of the edition.
   *
   * @return the edition.
   *
   * @throws FileFormatException if the file is not a valid edition file.
   * @throws IOException if the file cannot be read.
   */
  public Edition open(final UUID id) throws FileFormatException, IOException {
    return editions.open(id);
  }

  /**
   * Saves an edition at once, then records the participations of its players in the directory.
   *
   * @param id the identifier of the edition.
   * @param edition the edition.
   *
   * @throws IOException if a file cannot be written; the previous version is then kept.
   */
  public void save(final UUID id, final Edition edition) throws IOException {
    editions.save(id, edition);
    recordParticipations(id, edition);
  }

  /**
   * Records the participations of the players of an edition in the directory, and saves the directory if they
   * changed; nothing is done when the directory could not be read.
   *
   * @param id the identifier of the edition.
   * @param edition the edition.
   *
   * @throws IOException if the directory cannot be written.
   */
  private void recordParticipations(final UUID id, final Edition edition) throws IOException {
    if (directory == null) {
      return;
    }
    final Directory recorded = directory.recordParticipations(id, edition);
    if (!recorded.equals(directory)) {
      directoryStore.save(recorded);
      directory = recorded;
    }
  }

  /**
   * Moves an edition to the trash.
   *
   * @param id the identifier of the edition.
   *
   * @throws IOException if the file cannot be moved.
   */
  public void delete(final UUID id) throws IOException {
    editions.delete(id);
  }

  /**
   * Lists the editions of the trash.
   *
   * @return the headers of the trashed editions and the unreadable files.
   *
   * @throws IOException if the folder cannot be read.
   */
  public EditionList listTrash() throws IOException {
    return editions.listTrash();
  }

  /**
   * Brings an edition back from the trash.
   *
   * @param id the identifier of the edition.
   *
   * @throws IOException if the file cannot be moved, or an edition with the same identifier exists.
   */
  public void restore(final UUID id) throws IOException {
    editions.restore(id);
  }

  /**
   * Deletes the editions of the trash for good.
   *
   * @throws IOException if a file cannot be deleted.
   */
  public void emptyTrash() throws IOException {
    editions.emptyTrash();
  }

  /**
   * Copies an edition to a file chosen by the user.
   *
   * @param id the identifier of the edition.
   * @param target the file to write.
   *
   * @throws IOException if a file cannot be read or written.
   */
  public void exportTo(final UUID id, final Path target) throws IOException {
    editions.exportTo(id, target);
  }

  /**
   * Imports an edition file chosen by the user.
   *
   * @param source the file to import.
   *
   * @return the identifier of the imported edition.
   *
   * @throws FileFormatException if the file is not a valid edition file.
   * @throws IOException if a file cannot be read or written.
   */
  public UUID importFrom(final Path source) throws FileFormatException, IOException {
    return editions.importFrom(source);
  }

  /**
   * Writes a ZIP archive of the whole data folder.
   *
   * @param target the archive to write.
   *
   * @throws IOException if a file cannot be read or written.
   */
  public void backUpEverything(final Path target) throws IOException {
    editions.backUpEverything(target);
  }

  // *******************************************************************************************************************
  // Directory and logo
  // *******************************************************************************************************************

  /**
   * Returns the directory.
   *
   * @return the directory, or nothing if it could not be read at start-up.
   */
  public Optional<Directory> directory() {
    return Optional.ofNullable(directory);
  }

  /**
   * Changes the directory and saves it at once: the change is applied here, on the background thread, to the latest
   * version of the directory (which the saves of the editions may have changed meanwhile).
   *
   * @param change the change, e.g. the addition of a person.
   *
   * @return the new directory.
   *
   * @throws IllegalStateException if the directory could not be read at start-up: it is never replaced.
   * @throws ch.varani.jass.domain.DomainException if the change is rejected by the directory.
   * @throws IOException if the directory cannot be written; it is then unchanged.
   */
  public Directory updateDirectory(final UnaryOperator<Directory> change) throws IOException {
    if (directory == null) {
      throw new IllegalStateException("The directory could not be read.");
    }
    final Directory changed = change.apply(directory);
    directoryStore.save(changed);
    directory = changed;
    return changed;
  }

  /**
   * Returns the problem met when reading the directory at start-up.
   *
   * @return the exception, or nothing if the directory was read.
   */
  public Optional<Exception> directoryError() {
    return Optional.ofNullable(directoryError);
  }

  /**
   * Reads a text file chosen by the user to import persons (CSV, UTF-8, at most 4 MiB).
   *
   * @param source the file.
   *
   * @return its text.
   *
   * @throws FileFormatException if the file is too large or not in UTF-8.
   * @throws IOException if the file cannot be read.
   */
  public String readText(final Path source) throws FileFormatException, IOException {
    return DirectoryStore.readText(source);
  }

  /**
   * Writes a text file chosen by the user, e.g. an export of the directory, atomically, in UTF-8.
   *
   * @param target the file.
   * @param text the text.
   *
   * @throws IOException if the file cannot be written.
   */
  public void writeText(final Path target, final String text) throws IOException {
    DirectoryStore.writeText(target, text);
  }

  /**
   * Writes a generated document (PDF) to a file chosen by the user, atomically.
   *
   * @param target the file.
   * @param content writes the document.
   *
   * @throws IOException if the file cannot be written; a previous file is then kept.
   */
  public void writeDocument(final Path target, final AtomicFiles.Content content) throws IOException {
    AtomicFiles.write(target, content);
  }

  /**
   * Reads the logo of the application.
   *
   * @return the content of the logo, or nothing without logo.
   *
   * @throws FileFormatException if the logo file is not a valid image any more.
   * @throws IOException if the file cannot be read.
   */
  public Optional<byte[]> logo() throws FileFormatException, IOException {
    return logo.load();
  }

  /**
   * Replaces the logo by an image chosen by the user.
   *
   * @param source the image.
   *
   * @throws FileFormatException if the image is not a readable PNG or JPEG image of at most 2 MiB.
   * @throws IOException if a file cannot be read or written.
   */
  public void setLogo(final Path source) throws FileFormatException, IOException {
    logo.save(source);
  }

  /**
   * Removes the logo.
   *
   * @throws IOException if the file cannot be deleted.
   */
  public void removeLogo() throws IOException {
    logo.remove();
  }

  // *******************************************************************************************************************
  // Supporting documents
  // *******************************************************************************************************************

  /**
   * Stores a supporting document chosen by the user in an edition file, at once.
   *
   * @param id the identifier of the edition.
   * @param source the document.
   *
   * @return the reference to the document, to attach to an entry of the accounting.
   *
   * @throws FileFormatException if the document is too large or not a PDF, PNG or JPEG document.
   * @throws IOException if a file cannot be read or written; the edition file is then unchanged.
   */
  public Attachment addAttachment(final UUID id, final Path source) throws FileFormatException, IOException {
    return editions.addAttachment(id, source);
  }

  /**
   * Copies a supporting document out of an edition file into the temporary folder of this session, to be opened by
   * the application of the system; the copy is named after the document, with the extension of its kind.
   *
   * @param id the identifier of the edition.
   * @param attachment the reference to the document.
   *
   * @return the copy.
   *
   * @throws FileFormatException if the content of the document does not match its digest.
   * @throws IOException if a file cannot be read or written.
   */
  public Path openCopy(final UUID id, final Attachment attachment) throws FileFormatException, IOException {
    if (copies == null) {
      copies = Files.createTempDirectory(COPIES_FOLDER);
    }
    final Path target = Files.createDirectories(copies.resolve(attachment.sha256())).resolve(copyName(attachment));
    editions.exportAttachment(id, attachment, target);
    return target;
  }

  /**
   * Returns the name of the copy of a document: its name, the characters that some systems forbid replaced, never
   * starting with a dot (no hidden file, no parent folder), ending with the extension of its kind.
   *
   * @param attachment the reference to the document.
   *
   * @return the name of the copy.
   */
  static String copyName(final Attachment attachment) {
    String name = FORBIDDEN.matcher(attachment.name()).replaceAll("_");
    if (name.startsWith(".")) {
      name = "_" + name;
    }
    final String extension = "." + attachment.type().extension();
    return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name : name + extension;
  }

  /**
   * Deletes the temporary copies of the documents opened during this session, e.g. when the application exits.
   *
   * @throws IOException if a copy cannot be deleted.
   */
  public void deleteCopies() throws IOException {
    if (copies == null) {
      return;
    }
    try (Stream<Path> files = Files.walk(copies)) {
      for (final Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
    copies = null;
  }

  // *******************************************************************************************************************
  // Settings
  // *******************************************************************************************************************

  /**
   * Reads the settings of the user interface.
   *
   * @return the settings by key; empty when none is saved or the file is not valid.
   *
   * @throws IOException if the file exists but cannot be read.
   */
  public Map<String, String> settings() throws IOException {
    return settings.load();
  }

  /**
   * Saves the settings of the user interface, atomically.
   *
   * @param values the settings by key.
   *
   * @throws IOException if the file cannot be written.
   */
  public void saveSettings(final Map<String, String> values) throws IOException {
    settings.save(values);
  }

}
