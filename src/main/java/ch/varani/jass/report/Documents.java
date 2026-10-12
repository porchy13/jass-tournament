package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;

import java.util.List;
import java.util.Optional;

/**
 * Helpers shared by the documents.
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class Documents {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Documents() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the games of the schedule of an edition.
   *
   * @param edition the edition.
   *
   * @return the games, sorted by round then table.
   *
   * @throws IllegalStateException if the edition has no schedule (the documents of the games are generated once the
   *                               registration is closed).
   */
  static List<Pairing> pairings(final Edition edition) {
    if (!edition.isScheduled()) {
      throw new IllegalStateException("The edition has no schedule.");
    }
    return edition.schedule().pairings();
  }

  /**
   * Finds the place of a participant in a round.
   *
   * @param pairings the games of the schedule.
   * @param round the number of the round.
   * @param participant the number of the participant.
   *
   * @return {@code 2k − 1} on the giver side of table {@code k}, {@code 2k} on its receiver side (rule 6), or nothing
   *         if the participant sits out the round.
   */
  static Optional<Integer> place(final List<Pairing> pairings, final int round, final int participant) {
    for (final Pairing pairing : pairings) {
      if (pairing.round() == round && pairing.giver().contains(participant)) {
        return Optional.of(2 * pairing.table() - 1);
      }
      if (pairing.round() == round && pairing.receiver().contains(participant)) {
        return Optional.of(2 * pairing.table());
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the title of a document in its properties: the title followed by the name of the edition.
   *
   * @param title the title of the document.
   * @param edition the edition.
   *
   * @return e.g. {@code Tableau des rencontres – Match aux cartes 2026}.
   */
  static String title(final String title, final Edition edition) {
    return title + " – " + edition.name();
  }

}
