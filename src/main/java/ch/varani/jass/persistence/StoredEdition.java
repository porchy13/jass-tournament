package ch.varani.jass.persistence;

import ch.varani.jass.domain.Edition;

import java.util.Objects;
import java.util.UUID;

/**
 * An edition with the identifier of its file: the content of a {@code .jass} file.
 *
 * <p>The identifier names the file in the data folder ({@code editions/<id>.jass}) independently of the name of the
 * edition, and is written inside the file, so that an imported file can be recognised.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param id the identifier of the edition file.
 * @param edition the edition.
 */
public record StoredEdition(UUID id, Edition edition) {

  /**
   * Creates a stored edition.
   *
   * @param id the identifier of the edition file.
   * @param edition the edition.
   *
   * @throws NullPointerException if a parameter is {@code null}.
   */
  public StoredEdition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(edition, "edition");
  }

}
