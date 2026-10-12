package ch.varani.jass.persistence;

import ch.varani.jass.domain.TournamentMode;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The header of an edition file: what the list of the editions shows, read without reading the registrations and the
 * schedule ({@link EditionReader#readHeader(java.io.InputStream)}).
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param id the identifier of the edition file.
 * @param name the name of the edition.
 * @param date the date of the edition.
 * @param mode the tournament mode.
 * @param games the number of games of each participant.
 */
public record EditionHeader(UUID id, String name, LocalDate date, TournamentMode mode, int games) {

  /**
   * Creates a header.
   *
   * @param id the identifier of the edition file.
   * @param name the name of the edition.
   * @param date the date of the edition.
   * @param mode the tournament mode.
   * @param games the number of games of each participant.
   *
   * @throws NullPointerException if a parameter is {@code null}.
   */
  public EditionHeader {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(mode, "mode");
  }

}
