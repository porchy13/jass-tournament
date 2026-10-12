package ch.varani.jass.persistence;

import ch.varani.jass.directory.Directory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DirectoryStore}: directory of the data folder, text files of the import and export.
 */
final class DirectoryStoreTest {

  /** The size of the byte order mark in UTF-8. */
  private static final int BYTE_ORDER_MARK_SIZE = 3;

  /** The temporary data folder. */
  @TempDir
  private Path folder;

  /**
   * The directory is empty before its first save, then read back as saved; a person removed is gone from the file.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The directory is empty at first, then read back as saved")
  void loadSaveTest() throws Exception {
    final DirectoryStore store = new DirectoryStore(folder.resolve("data"));
    final Directory directory = Fixtures.directory();
    final Directory empty = store.load();
    store.save(directory);
    final Directory saved = store.load();
    final Directory removed = directory.remove(directory.persons().get(0).id());
    store.save(removed);
    final byte[] file = Files.readAllBytes(folder.resolve("data").resolve(DirectoryStore.FILE_NAME));
    assertAll(
        () -> assertEquals(Directory.empty(), empty),
        () -> assertEquals(directory, saved),
        () -> assertEquals(removed, store.load()),
        () -> assertFalse(Fixtures.directoryXml(removed).contains("anne@example.ch")),
        () -> assertArrayEquals(Fixtures.directoryFile(removed), file));
  }

  /**
   * A damaged directory file is reported, not replaced by an empty directory.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A damaged directory file is reported")
  void damagedTest() throws IOException {
    final DirectoryStore store = new DirectoryStore(folder);
    Files.writeString(folder.resolve(DirectoryStore.FILE_NAME), "damaged", StandardCharsets.UTF_8);
    assertEquals(FormatError.INVALID_ARCHIVE, assertThrows(FileFormatException.class, store::load).getError());
  }

  /**
   * A text file to import is read in UTF-8; a larger file or another encoding is rejected.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A text file to import must be small and in UTF-8")
  void readTextTest() throws IOException {
    final Path text = Files.writeString(folder.resolve("ok.csv"), "Zoé;Müller", StandardCharsets.UTF_8);
    final Path latin = Files.writeString(folder.resolve("latin.csv"), "Zoé;Müller", StandardCharsets.ISO_8859_1);
    final byte[] huge = new byte[DirectoryStore.MAX_IMPORT_SIZE + 1];
    Arrays.fill(huge, (byte) 'a');
    final Path large = Files.write(folder.resolve("large.csv"), huge);
    final Path limit = Files.write(folder.resolve("limit.csv"), Arrays.copyOf(huge, DirectoryStore.MAX_IMPORT_SIZE));
    assertAll(
        () -> assertEquals("Zoé;Müller", DirectoryStore.readText(text)),
        () -> assertEquals(DirectoryStore.MAX_IMPORT_SIZE, DirectoryStore.readText(limit).length()),
        () -> assertEquals(FormatError.INVALID_ENCODING,
                           assertThrows(FileFormatException.class, () -> DirectoryStore.readText(latin)).getError()),
        () -> assertEquals(FormatError.TOO_LARGE,
                           assertThrows(FileFormatException.class, () -> DirectoryStore.readText(large)).getError()));
  }

  /**
   * An export is written in UTF-8 with a byte order mark, and read back by the import without it.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("An export is written in UTF-8 with a byte order mark")
  void writeTextTest() throws Exception {
    final Path target = folder.resolve("export.csv");
    DirectoryStore.writeText(target, "Zoé;Müller\r\n");
    final byte[] bytes = Files.readAllBytes(target);
    assertAll(
        () -> assertEquals("\uFEFF", new String(bytes, 0, BYTE_ORDER_MARK_SIZE, StandardCharsets.UTF_8)),
        () -> assertTrue(DirectoryStore.readText(target).endsWith("Zoé;Müller\r\n")),
        () -> assertEquals("﻿Zoé;Müller\r\n", DirectoryStore.readText(target)));
  }

}
