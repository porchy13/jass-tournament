package ch.varani.jass.persistence;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.TournamentMode;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The header of an edition file: what the list of the editions shows, read without reading the schedule, the results
 * and the accounting ({@link EditionReader#readHeader(java.io.InputStream)}).
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param id the identifier of the edition file.
 * @param name the name of the edition.
 * @param date the date of the edition.
 * @param mode the tournament mode.
 * @param games the number of games of each participant.
 * @param maxTeams the maximum number of teams.
 * @param registrations the number of registered participants (teams, or players in individual mode), the waiting list
 *                      excluded.
 * @param waiting the number of participants on the waiting list (teams, or players in individual mode).
 */
public record EditionHeader(UUID id, String name, LocalDate date, TournamentMode mode, int games, int maxTeams,
                            int registrations, int waiting) {

  /**
   * Creates a header.
   *
   * @param id the identifier of the edition file.
   * @param name the name of the edition.
   * @param date the date of the edition.
   * @param mode the tournament mode.
   * @param games the number of games of each participant.
   * @param maxTeams the maximum number of teams.
   * @param registrations the number of registered participants.
   * @param waiting the number of participants on the waiting list.
   *
   * @throws NullPointerException if a parameter is {@code null}.
   */
  public EditionHeader {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(mode, "mode");
  }

  /**
   * Creates the header of an edition.
   *
   * @param id the identifier of the edition file.
   * @param edition the edition.
   *
   * @return the header: name, date, mode, games, maximum of teams, number of registered and of waiting participants.
   */
  public static EditionHeader of(final UUID id, final Edition edition) {
    return new EditionHeader(id, edition.name(), edition.date(), edition.mode(), edition.games(), edition.maxTeams(),
                             edition.registrations().size(), edition.registrations().waitingSize());
  }

}
