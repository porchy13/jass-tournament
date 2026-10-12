package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.TournamentMode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The labels and names of the participants of an edition in the documents: teams (number and two names) in team mode,
 * players (number and name) in individual mode.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
final class Participants {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The separator of the names of two partners. */
  private static final String AND = " & ";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The edition. */
  private final Edition edition;
  /** The texts of the documents. */
  private final Texts texts;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the participants of an edition.
   *
   * @param edition the edition.
   * @param texts the texts of the documents.
   */
  Participants(final Edition edition, final Texts texts) {
    this.edition = edition;
    this.texts = texts;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the number of participants.
   *
   * @return the number of teams, or of players in individual mode.
   */
  int count() {
    return edition.registrations().size();
  }

  /**
   * Tells whether the participants are teams.
   *
   * @return {@code true} in team mode.
   */
  boolean areTeams() {
    return edition.mode() == TournamentMode.TEAM;
  }

  /**
   * Returns the label of a participant: {@code Équipe 3} or {@code Joueur 3}.
   *
   * @param number the number of the participant.
   *
   * @return the label.
   */
  String label(final int number) {
    return texts.get(areTeams() ? "team" : "player", number);
  }

  /**
   * Returns the names of the players of a participant, one per player.
   *
   * @param number the number of the participant.
   *
   * @return the two names of a team, or the name of a player.
   */
  List<String> names(final int number) {
    final List<Player> players = areTeams() ? edition.teamRegistrations().get(number).players()
                                            : List.of(edition.individualRegistrations().get(number));
    return players.stream().map(Player::fullName).toList();
  }

  /**
   * Returns the names of a participant on one line, e.g. {@code Anne Fontaine & Bruno Rochat}.
   *
   * @param number the number of the participant.
   *
   * @return the names joined.
   */
  String joinedNames(final int number) {
    return String.join(AND, names(number));
  }

  /**
   * Returns the lines describing a side of a game: in team mode, the label of the team then its two names; in
   * individual mode, each partner with its number.
   *
   * @param side the participants of the side.
   *
   * @return the lines.
   */
  List<String> sideLines(final List<Integer> side) {
    if (areTeams()) {
      final int team = side.getFirst();
      return List.of(label(team), joinedNames(team));
    }
    return side.stream().map(player -> joinedNames(player) + " (" + player + ")").toList();
  }

  /**
   * Returns a side of a game on one line: {@code Équipe 3 : Anne Fontaine & Bruno Rochat} in team mode,
   * {@code Anne Fontaine (12) & Zoé Müller (5)} in individual mode.
   *
   * @param side the participants of the side.
   *
   * @return the line.
   */
  String sideLine(final List<Integer> side) {
    if (areTeams()) {
      return texts.get("team.names", label(side.getFirst()), joinedNames(side.getFirst()));
    }
    return sideLines(side).stream().collect(Collectors.joining(AND));
  }

}
