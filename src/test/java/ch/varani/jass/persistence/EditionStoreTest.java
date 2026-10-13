package ch.varani.jass.persistence;

import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.AttachmentType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link EditionStore}: editions, trash, export, import and backup, in a temporary data folder.
 */
final class EditionStoreTest {

  /** The temporary data folder. */
  @TempDir
  private Path folder;

  /** The store of the temporary data folder. */
  private EditionStore store;

  /**
   * Opens the store of the temporary data folder.
   *
   * @throws IOException never.
   */
  @BeforeEach
  void setUp() throws IOException {
    store = new EditionStore(folder);
  }

  /**
   * Opening the store creates its folders.
   */
  @Test
  @DisplayName("Opening the store creates the folders of the editions and of the trash")
  void foldersTest() {
    assertAll(
        () -> assertTrue(Files.isDirectory(folder.resolve(EditionStore.EDITIONS_FOLDER))),
        () -> assertTrue(Files.isDirectory(folder.resolve(EditionStore.TRASH_FOLDER))));
  }

  /**
   * A created edition is listed, opened and saved again.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A created edition is listed, opened, and saved again")
  void createOpenSaveTest() throws Exception {
    final UUID id = store.create(Fixtures.teamEdition());
    final Edition changed = Fixtures.scheduledTeamEdition().withName("Renamed");
    final Edition created = store.open(id);
    store.save(id, changed);
    assertAll(
        () -> assertEquals(Fixtures.teamEdition(), created),
        () -> assertEquals(changed, store.open(id)),
        () -> assertEquals(List.of(EditionHeader.of(id, changed)),
                           store.list().editions()),
        () -> assertEquals(List.of(id + EditionStore.EXTENSION), names(folder.resolve(EditionStore.EDITIONS_FOLDER))));
  }

  /**
   * The editions are listed the most recent first, then by name.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The editions are listed the most recent first, then by name")
  void listOrderTest() throws Exception {
    final Edition edition = Fixtures.teamEdition();
    store.create(edition.withName("B"));
    store.create(edition.withName("A"));
    store.create(edition.withName("C").withDate(Fixtures.DATE.plusYears(1)));
    store.create(edition.withName("D").withDate(Fixtures.DATE.minusYears(1)));
    assertEquals(List.of("C", "A", "B", "D"),
                 store.list().editions().stream().map(EditionHeader::name).toList());
  }

  /**
   * A damaged file, or a file whose identifier does not match its name, is reported as unreadable; the other files
   * of the folder are ignored.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Unreadable files are reported, other files are ignored")
  void unreadableTest() throws Exception {
    final Path editions = folder.resolve(EditionStore.EDITIONS_FOLDER);
    final UUID damaged = UUID.randomUUID();
    final UUID mismatch = UUID.randomUUID();
    Files.writeString(editions.resolve(damaged + EditionStore.EXTENSION), "damaged", StandardCharsets.UTF_8);
    Files.write(editions.resolve(mismatch + EditionStore.EXTENSION), Fixtures.file(Fixtures.teamEdition()));
    Files.writeString(editions.resolve("notes" + EditionStore.EXTENSION), "ignored", StandardCharsets.UTF_8);
    Files.writeString(editions.resolve(UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + EditionStore.EXTENSION),
                      "ignored", StandardCharsets.UTF_8);
    Files.writeString(editions.resolve(".x.tmp"), "ignored", StandardCharsets.UTF_8);
    final UUID valid = store.create(Fixtures.individualEdition());
    final EditionList list = store.list();
    assertAll(
        () -> assertEquals(List.of(valid), list.editions().stream().map(EditionHeader::id).toList()),
        () -> assertEquals(List.of(damaged, mismatch).stream().sorted().toList(), list.unreadable()),
        () -> assertEquals(FormatError.INVALID_CONTENT,
                           assertThrows(FileFormatException.class, () -> store.open(mismatch)).getError()),
        () -> assertThrows(NoSuchFileException.class, () -> store.open(UUID.randomUUID())));
  }

  /**
   * The identifier of a file is given by its name, a UUID in its canonical form followed by the extension.
   */
  @Test
  @DisplayName("The identifier of a file is the canonical UUID of its name")
  void idOfTest() {
    final UUID id = UUID.randomUUID();
    final Path upperCase = Path.of(id.toString().toUpperCase(Locale.ROOT) + EditionStore.EXTENSION);
    assertAll(
        () -> assertEquals(Optional.of(id), EditionStore.idOf(Path.of(id + EditionStore.EXTENSION))),
        () -> assertEquals(Optional.empty(), EditionStore.idOf(Path.of(id + ".txt"))),
        () -> assertEquals(Optional.empty(), EditionStore.idOf(Path.of("x"))),
        () -> assertEquals(Optional.empty(), EditionStore.idOf(upperCase)));
  }

  /**
   * A deleted edition goes to the trash, from which it can be restored or deleted for good.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A deleted edition goes to the trash, from which it is restored or emptied")
  void trashTest() throws Exception {
    new DirectoryStore(folder).save(Fixtures.directory());
    final UUID kept = store.create(Fixtures.teamEdition());
    final UUID restored = store.create(Fixtures.individualEdition());
    store.delete(kept);
    store.delete(restored);
    final List<UUID> trashed = ids(store.listTrash());
    store.restore(restored);
    final List<UUID> afterRestore = ids(store.list());
    store.emptyTrash();
    assertAll(
        () -> assertEquals(List.of(restored, kept), trashed),
        () -> assertEquals(List.of(restored), afterRestore),
        () -> assertEquals(Fixtures.individualEdition(), store.open(restored)),
        () -> assertEquals(List.of(), ids(store.listTrash())),
        () -> assertThrows(NoSuchFileException.class, () -> store.delete(kept)),
        () -> assertThrows(NoSuchFileException.class, () -> store.restore(kept)));
  }

  /**
   * Deleting an edition replaces a trashed edition with the same identifier; restoring an edition never replaces an
   * edition with the same identifier.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The trash keeps the last deleted copy; a restore never replaces an edition")
  void trashConflictTest() throws Exception {
    final UUID id = store.create(Fixtures.teamEdition());
    store.delete(id);
    store.save(id, Fixtures.individualEdition());
    store.delete(id);
    store.save(id, Fixtures.teamEdition());
    assertAll(
        () -> assertEquals(Fixtures.individualEdition().name(), store.listTrash().editions().getFirst().name()),
        () -> assertThrows(FileAlreadyExistsException.class, () -> store.restore(id)),
        () -> assertEquals(Fixtures.teamEdition(), store.open(id)));
  }

  /**
   * An exported edition can be imported: with its identifier in another data folder, with a new identifier when its
   * identifier is used by an edition or by the trash.
   *
   * @param other another temporary data folder.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("An exported edition is imported, with a new identifier if its identifier is used")
  void exportImportTest(@TempDir final Path other) throws Exception {
    final UUID id = store.create(Fixtures.scheduledIndividualEdition());
    final Path exported = other.resolve("export.jass");
    store.exportTo(id, exported);
    final EditionStore otherStore = new EditionStore(other.resolve("data"));
    final UUID sameId = otherStore.importFrom(exported);
    final UUID usedByEdition = store.importFrom(exported);
    store.delete(id);
    final UUID usedByTrash = store.importFrom(exported);
    assertAll(
        () -> assertEquals(id, sameId),
        () -> assertEquals(Fixtures.scheduledIndividualEdition(), otherStore.open(sameId)),
        () -> assertNotEquals(id, usedByEdition),
        () -> assertNotEquals(id, usedByTrash),
        () -> assertEquals(Fixtures.scheduledIndividualEdition(), store.open(usedByEdition)),
        () -> assertEquals(List.of("data", "export.jass"), names(other)));
  }

  /**
   * An invalid file is not imported.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("An invalid file is not imported")
  void importInvalidTest() throws IOException {
    final Path file = folder.resolve("invalid.jass");
    Files.writeString(file, "invalid", StandardCharsets.UTF_8);
    assertAll(
        () -> assertEquals(FormatError.MISSING_EDITION,
                           assertThrows(FileFormatException.class, () -> store.importFrom(file)).getError()),
        () -> assertEquals(List.of(), store.list().editions()));
  }

  /**
   * A failed write keeps the previous file and leaves no temporary file.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A failed write keeps the previous file and leaves no temporary file")
  void failedWriteTest() throws IOException {
    final Path target = folder.resolve("target");
    Files.createDirectories(target.resolve("not-empty"));
    final UUID id = UUID.randomUUID();
    final UUID created = store.create(Fixtures.teamEdition());
    assertAll(
        () -> assertThrows(NoSuchFileException.class, () -> store.exportTo(id, folder.resolve("x.jass"))),
        () -> assertThrows(IOException.class, () -> store.exportTo(created, target)),
        () -> assertTrue(Files.isDirectory(target.resolve("not-empty"))),
        () -> assertEquals(List.of(EditionStore.EDITIONS_FOLDER, "target", EditionStore.TRASH_FOLDER), names(folder)));
  }

  /**
   * The backup is a ZIP archive of the directory, the editions and the trash, without the temporary files.
   *
   * @param other another temporary folder, for the backup.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The backup holds the directory, the editions and the trash, without the temporary files")
  void backUpEverythingTest(@TempDir final Path other) throws Exception {
    new DirectoryStore(folder).save(Fixtures.directory());
    final UUID kept = store.create(Fixtures.teamEdition());
    final UUID trashed = store.create(Fixtures.individualEdition());
    store.delete(trashed);
    final Path temporary = folder.resolve(EditionStore.EDITIONS_FOLDER).resolve(".x.tmp");
    Files.writeString(temporary, "ignored", StandardCharsets.UTF_8);
    final Path backup = other.resolve("backup.zip");
    store.backUpEverything(backup);
    final List<String> entries = new ArrayList<>();
    try (InputStream in = Files.newInputStream(backup); ZipInputStream zip = new ZipInputStream(in)) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.add(entry.getName());
      }
    }
    assertEquals(List.of(DirectoryStore.FILE_NAME, EditionStore.EDITIONS_FOLDER + "/" + kept + EditionStore.EXTENSION,
                         EditionStore.TRASH_FOLDER + "/" + trashed + EditionStore.EXTENSION), entries);
  }

  /**
   * Returns the identifiers of the editions of a list.
   *
   * @param list the list.
   *
   * @return the identifiers, in the order of the list.
   */
  private static List<UUID> ids(final EditionList list) {
    return list.editions().stream().map(EditionHeader::id).toList();
  }

  /**
   * Returns the names of the files of a folder, sorted.
   *
   * @param directory the folder.
   *
   * @return the names of its files and folders.
   *
   * @throws IOException if the folder cannot be read.
   */
  private static List<String> names(final Path directory) throws IOException {
    try (var files = Files.list(directory)) {
      return files.map(file -> file.getFileName().toString()).sorted().toList();
    }
  }

  /**
   * A document is checked, stored at once in the edition file, kept by the saves while an entry refers to it, and
   * dropped by the next save once no entry refers to it.
   *
   * @param other another temporary folder, for the documents.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A document is stored at once, kept while referenced, dropped afterwards")
  void attachmentTest(@TempDir final Path other) throws Exception {
    final UUID id = store.create(Fixtures.teamEdition());
    final Attachment pdf = store.addAttachment(id, Files.write(other.resolve("facture.pdf"), Fixtures.PDF));
    final Attachment jpeg = store.addAttachment(id, Files.write(other.resolve("ticket.jpeg"), Fixtures.JPEG));
    final Attachment again = store.addAttachment(id, Files.write(other.resolve("copie.pdf"), Fixtures.PDF));
    final List<String> stored = attachmentEntries(id);
    final LedgerEntry entry = LedgerEntry.create(Fixtures.DATE, EntryType.EXPENSE, "Prix", "", Money.of("10"))
                                         .withAttachment(pdf);
    final Edition edition = Fixtures.teamEdition().withLedger(Ledger.EMPTY.add(entry));
    store.save(id, edition);
    final List<String> kept = attachmentEntries(id);
    final Edition opened = store.open(id);
    store.save(id, Fixtures.teamEdition());
    assertAll(
        () -> assertEquals(AttachmentType.PDF, pdf.type()),
        () -> assertEquals("facture.pdf", pdf.name()),
        () -> assertEquals(Fixtures.PDF.length, pdf.size()),
        () -> assertEquals(AttachmentType.JPEG, jpeg.type()),
        () -> assertEquals(pdf.sha256(), again.sha256()),
        () -> assertEquals(List.of(jpeg.fileName(), pdf.fileName()).stream().sorted().toList(), stored),
        () -> assertEquals(List.of(pdf.fileName()), kept),
        () -> assertEquals(edition, opened),
        () -> assertEquals(List.of(), attachmentEntries(id)));
  }

  /**
   * A document that is empty, not a PDF, PNG or JPEG document, or too large is refused; the edition must exist.
   *
   * @param other another temporary folder, for the documents.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A document of another kind, empty or too large is refused")
  void unsupportedAttachmentTest(@TempDir final Path other) throws IOException {
    final UUID id = store.create(Fixtures.teamEdition());
    final Path text = Files.writeString(other.resolve("facture.pdf"), "not a PDF", StandardCharsets.UTF_8);
    final Path empty = Files.write(other.resolve("empty.pdf"), new byte[0]);
    final byte[] huge = Arrays.copyOf(Fixtures.PDF, Attachment.MAX_SIZE + 1);
    final Path large = Files.write(other.resolve("large.pdf"), huge);
    assertAll(
        () -> assertEquals(FormatError.UNSUPPORTED_ATTACHMENT,
                           assertThrows(FileFormatException.class, () -> store.addAttachment(id, text)).getError()),
        () -> assertEquals(FormatError.UNSUPPORTED_ATTACHMENT,
                           assertThrows(FileFormatException.class, () -> store.addAttachment(id, empty)).getError()),
        () -> assertEquals(FormatError.TOO_LARGE,
                           assertThrows(FileFormatException.class, () -> store.addAttachment(id, large)).getError()),
        () -> assertThrows(NoSuchFileException.class,
                           () -> store.addAttachment(UUID.randomUUID(), Files.write(other.resolve("a.pdf"),
                                                                                    Fixtures.PDF))),
        () -> assertEquals(List.of(), attachmentEntries(id)));
  }

  /**
   * A document is copied out of the edition file after checking its digest; a damaged or missing document is
   * reported.
   *
   * @param other another temporary folder, for the documents.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A document is exported after checking its digest")
  void exportAttachmentTest(@TempDir final Path other) throws Exception {
    final UUID id = store.create(Fixtures.teamEdition());
    final Attachment pdf = store.addAttachment(id, Files.write(other.resolve("facture.pdf"), Fixtures.PDF));
    final Attachment missing = Fixtures.attachment(Fixtures.JPEG, AttachmentType.JPEG, "ticket.jpg");
    final Path target = other.resolve("copy.pdf");
    store.exportAttachment(id, pdf, target);
    final byte[] exported = Files.readAllBytes(target);
    final Path file = folder.resolve(EditionStore.EDITIONS_FOLDER).resolve(id + EditionStore.EXTENSION);
    Files.write(file, damaged(id, pdf));
    assertAll(
        () -> assertArrayEquals(Fixtures.PDF, exported),
        () -> assertThrows(NoSuchFileException.class, () -> store.exportAttachment(id, missing, target)),
        () -> assertEquals(FormatError.DAMAGED_ATTACHMENT,
                           assertThrows(FileFormatException.class, () -> store.exportAttachment(id, pdf, target))
                               .getError()),
        () -> assertArrayEquals(Fixtures.PDF, Files.readAllBytes(target)));
  }

  /**
   * An exported edition is imported with its documents.
   *
   * @param other another temporary folder.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("An edition is imported with its documents")
  void importAttachmentsTest(@TempDir final Path other) throws Exception {
    final Path source = Files.write(other.resolve("edition.jass"),
                                    Fixtures.fileWithAttachments(Fixtures.ledgerEdition()));
    final UUID id = store.importFrom(source);
    final Attachment jpeg = store.open(id).ledger().attachments().stream()
                                 .filter(attachment -> attachment.type() == AttachmentType.JPEG).findFirst()
                                 .orElseThrow();
    store.exportAttachment(id, jpeg, other.resolve("ticket.jpg"));
    assertAll(
        () -> assertEquals(Fixtures.ledgerEdition(), store.open(id)),
        () -> assertArrayEquals(Fixtures.JPEG, Files.readAllBytes(other.resolve("ticket.jpg"))));
  }

  /**
   * An edition referring to a document that is not stored cannot be created nor saved.
   */
  @Test
  @DisplayName("An edition referring to a document that is not stored is refused")
  void missingAttachmentTest() {
    final Edition edition = Fixtures.ledgerEdition();
    assertAll(
        () -> assertThrows(NoSuchFileException.class, () -> store.create(edition)),
        () -> assertThrows(NoSuchFileException.class, () -> store.save(UUID.randomUUID(), edition)),
        () -> assertThrows(NoSuchFileException.class,
                           () -> store.save(store.create(Fixtures.teamEdition()), edition)));
  }

  /**
   * Returns the names of the documents stored in an edition file.
   *
   * @param id the identifier of the edition.
   *
   * @return the names, sorted.
   *
   * @throws IOException if the file cannot be read.
   */
  private List<String> attachmentEntries(final UUID id) throws IOException {
    final Path file = folder.resolve(EditionStore.EDITIONS_FOLDER).resolve(id + EditionStore.EXTENSION);
    try (ZipFile zip = new ZipFile(file.toFile())) {
      return zip.stream().map(ZipEntry::getName)
                .filter(name -> name.startsWith(EditionXml.ATTACHMENTS))
                .map(name -> name.substring(EditionXml.ATTACHMENTS.length()))
                .sorted()
                .toList();
    }
  }

  /**
   * Returns an edition file whose document has another content than its digest says.
   *
   * @param id the identifier of the edition.
   * @param attachment the document.
   *
   * @return the bytes of the damaged file.
   *
   * @throws Exception never.
   */
  private byte[] damaged(final UUID id, final Attachment attachment) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    EditionWriter.write(new StoredEdition(id, store.open(id)), out, List.of(attachment.fileName()),
                        (name, entry) -> entry.write(Fixtures.JPEG));
    return out.toByteArray();
  }

}
