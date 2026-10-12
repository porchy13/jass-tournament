package ch.varani.jass.directory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The participation of a person of the directory in an edition, recorded from the registrations of the edition
 * ({@link Directory#recordParticipations(UUID, ch.varani.jass.domain.Edition)}).
 *
 * <p>The name and the date of the edition are copied, so that the participation stays readable when the edition is
 * deleted; they are updated each time the participations of the edition are recorded again.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param edition the identifier of the edition file.
 * @param editionName the name of the edition.
 * @param editionDate the date of the edition.
 * @param teamMate the identifier of the person with whom the person played in team mode, or {@code null} in
 *                 individual mode or when the team mate is not in the directory.
 */
public record Participation(UUID edition, String editionName, LocalDate editionDate, UUID teamMate) {

  /**
   * Creates a participation.
   *
   * @param edition the identifier of the edition file.
   * @param editionName the name of the edition.
   * @param editionDate the date of the edition.
   * @param teamMate the identifier of the team mate, or {@code null}.
   *
   * @throws NullPointerException if the edition, its name or its date is {@code null}.
   */
  public Participation {
    Objects.requireNonNull(edition, "edition");
    Objects.requireNonNull(editionName, "editionName");
    Objects.requireNonNull(editionDate, "editionDate");
  }

  /**
   * Returns a copy of this participation with another team mate.
   *
   * @param newTeamMate the identifier of the new team mate, or {@code null}.
   *
   * @return the new participation.
   */
  public Participation withTeamMate(final UUID newTeamMate) {
    return new Participation(edition, editionName, editionDate, newTeamMate);
  }

}
