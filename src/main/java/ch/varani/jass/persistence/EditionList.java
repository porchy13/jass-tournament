package ch.varani.jass.persistence;

import java.util.List;
import java.util.UUID;

/**
 * The editions found in a folder of the data folder ({@link EditionStore#list()}, {@link EditionStore#listTrash()}):
 * the headers of the readable files, and the identifiers of the files that could not be read.
 *
 * <p>An unreadable file is reported rather than hidden, so that the user knows that it exists.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param editions the headers of the readable editions, the most recent first (by date, then by name).
 * @param unreadable the identifiers of the files that could not be read, sorted.
 */
public record EditionList(List<EditionHeader> editions, List<UUID> unreadable) {

  /**
   * Creates a list, copying both lists.
   *
   * @param editions the headers of the readable editions.
   * @param unreadable the identifiers of the unreadable files.
   *
   * @throws NullPointerException if a list or one of its elements is {@code null}.
   */
  public EditionList {
    editions = List.copyOf(editions);
    unreadable = List.copyOf(unreadable);
  }

}
