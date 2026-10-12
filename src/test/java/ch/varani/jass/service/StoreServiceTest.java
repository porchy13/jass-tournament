package ch.varani.jass.service;

import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.AttachmentType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.DirectoryStore;
import ch.varani.jass.persistence.FileFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link StoreService}: editions, participations recorded in the directory, damaged directory, trash,
 * export, import, backup and logo, on a temporary data folder with fictitious persons.
 */
final class StoreServiceTest {

  /** The date of the fictitious editions. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The size of the generated logo, in pixels. */
  private static final int LOGO = 8;
  /** The beginning of a fictitious PDF document. */
  private static final byte[] PDF = "%PDF-1.7 fictitious invoice".getBytes(StandardCharsets.US_ASCII);
  /** The number of hexadecimal digits of a SHA-256 digest. */
  private static final int DIGEST = 64;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary folder, for the exported files. */
  @TempDir
  private Path other;

  /**
   * A created or saved edition is recorded in the participations of its players linked to the directory; the
   * directory is saved only when they change.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Saving an edition records the participations of its players in the directory")
  void participationsTest() throws Exception {
    final Person anne = Person.create("Anne", "Fontaine");
    final Person bruno = Person.create("Bruno", "Rochat");
    new DirectoryStore(folder).save(Directory.empty().add(anne).add(bruno));
    final StoreService service = new StoreService(folder);
    final Edition edition = Edition.create("Match", DATE, TournamentMode.TEAM);
    final UUID id = service.create(edition);
    final Directory unchanged = service.directory().orElseThrow();
    final Edition registered = edition.withRegistrations(
        TeamRegistrations.empty().add(new Team(anne.asPlayer(), bruno.asPlayer())));
    service.save(id, registered);
    service.save(id, registered);
    assertAll(
        () -> assertEquals(Optional.empty(), service.directoryError()),
        () -> assertEquals(List.of(), unchanged.get(anne.id()).participations()),
        () -> assertEquals(1, service.directory().orElseThrow().get(anne.id()).participations().size()),
        () -> assertEquals(service.directory().orElseThrow(), new DirectoryStore(folder).load()),
        () -> assertEquals(registered, service.open(id)));
  }

  /**
   * A damaged directory is reported and left untouched: the saves do not replace it by an empty directory.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A damaged directory is reported and never replaced")
  void damagedDirectoryTest() throws Exception {
    Files.writeString(folder.resolve("directory.jassdir"), "damaged");
    final StoreService service = new StoreService(folder);
    final UUID id = service.create(Edition.create("Match", DATE, TournamentMode.TEAM));
    service.save(id, service.open(id).withName("Match 2"));
    assertAll(
        () -> assertInstanceOf(FileFormatException.class, service.directoryError().orElseThrow()),
        () -> assertEquals(Optional.empty(), service.directory()),
        () -> assertThrows(IllegalStateException.class, () -> service.updateDirectory(directory -> directory)),
        () -> assertEquals(Map.of(), service.settings()),
        () -> assertEquals("damaged", Files.readString(folder.resolve("directory.jassdir"))),
        () -> assertEquals("Match 2", service.list().editions().getFirst().name()));
  }

  /**
   * The settings of the interface are saved and read back.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The settings of the interface are saved and read back")
  void settingsTest() throws Exception {
    final StoreService service = new StoreService(folder);
    service.saveSettings(Map.of("theme", "LIGHT"));
    assertEquals(Map.of("theme", "LIGHT"), service.settings());
  }

  /**
   * The editions go to the trash and back, are exported, imported and backed up.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Trash, export, import and backup of the editions")
  void filesTest() throws Exception {
    final StoreService service = new StoreService(folder);
    final UUID id = service.create(Edition.create("Match", DATE, TournamentMode.TEAM));
    final Path exported = other.resolve("match.jass");
    service.exportTo(id, exported);
    service.delete(id);
    final int trashed = service.listTrash().editions().size();
    service.restore(id);
    final UUID imported = service.importFrom(exported);
    service.delete(imported);
    service.emptyTrash();
    service.backUpEverything(other.resolve("backup.zip"));
    assertAll(
        () -> assertEquals(1, trashed),
        () -> assertEquals(List.of(), service.listTrash().editions()),
        () -> assertEquals(1, service.list().editions().size()),
        () -> assertTrue(Files.isRegularFile(other.resolve("backup.zip"))));
  }

  /**
   * The logo is chosen, read and removed.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The logo is chosen, read and removed")
  void logoTest() throws Exception {
    final StoreService service = new StoreService(folder);
    final Path png = other.resolve("logo.png");
    ImageIO.write(new BufferedImage(LOGO, LOGO, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
    service.setLogo(png);
    final byte[] logo = service.logo().orElseThrow();
    service.removeLogo();
    assertAll(
        () -> assertEquals(Files.size(png), logo.length),
        () -> assertEquals(Optional.empty(), service.logo()));
  }

  /**
   * F8: a supporting document is stored in the edition file, copied out to be opened by the system under its name
   * with the extension of its kind, and the copies are deleted at the end of the session.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F8: a document is stored, copied to be opened, and the copies deleted")
  void documentsTest() throws Exception {
    final StoreService service = new StoreService(folder);
    service.deleteCopies();
    final UUID id = service.create(Edition.create("Match", DATE, TournamentMode.TEAM));
    final Path source = other.resolve("facture.pdf");
    Files.write(source, PDF);
    final Attachment attachment = service.addAttachment(id, source);
    final Path copy = service.openCopy(id, attachment);
    final Path second = service.openCopy(id, attachment);
    final byte[] content = Files.readAllBytes(copy);
    service.deleteCopies();
    assertAll(
        () -> assertEquals(AttachmentType.PDF, attachment.type()),
        () -> assertEquals("facture.pdf", copy.getFileName().toString()),
        () -> assertEquals(copy, second),
        () -> assertEquals(List.of(PDF[0], PDF[1]), List.of(content[0], content[1])),
        () -> assertFalse(Files.exists(copy)),
        () -> assertFalse(Files.exists(copy.getParent().getParent())));
  }

  /**
   * The name of a copy replaces the characters that some systems forbid, never starts with a dot, and ends with the
   * extension of the kind of the document.
   */
  @Test
  @DisplayName("F8: the copy of a document is named safely, with the extension of its kind")
  void copyNameTest() {
    final String digest = "a".repeat(DIGEST);
    assertAll(
        () -> assertEquals("Facture.PDF", StoreService.copyName(new Attachment(digest, AttachmentType.PDF,
                                                                              "Facture.PDF", 1))),
        () -> assertEquals("re_u_2026.jpg", StoreService.copyName(new Attachment(digest, AttachmentType.JPEG,
                                                                                "re/u:2026", 1))),
        () -> assertEquals("_...png", StoreService.copyName(new Attachment(digest, AttachmentType.PNG, "..", 1))),
        () -> assertEquals("photo.jpeg.jpg", StoreService.copyName(new Attachment(digest, AttachmentType.JPEG,
                                                                                 "photo.jpeg", 1))));
  }

}
