package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link PlacesDocument}: the place of each participant in each round, to be posted.
 */
final class PlacesDocumentTest {

  /** A number of teams that needs several pages. */
  private static final int MANY_TEAMS = 80;

  /**
   * Rules 1 and 6: each team has its place in each round (2k − 1 for the giver of table k, 2k for the receiver), a
   * dash for a round sat out.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rules 1 and 6: each team has its place in each round, a dash when it sits out")
  void placesTest() throws Exception {
    final String text = Samples.text(Samples.pdf(out -> PlacesDocument.write(Samples.teamEdition(), Samples.OPTIONS,
                                                                             out)));
    assertAll(
        () -> assertTrue(text.contains("Places des participants"), text),
        () -> assertTrue(text.contains(Samples.EDITION_LINE), text),
        () -> assertTrue(text.contains("N° Noms Partie 1 Partie 2"), text),
        () -> assertTrue(text.contains("1 Anne1 Fontaine & Bruno1 Rochat 1 –"), text),
        () -> assertTrue(text.contains("2 Anne2 Fontaine & Bruno2 Rochat 2 2"), text),
        () -> assertTrue(text.contains("4 Anne4 Fontaine & Bruno4 Rochat 4 –"), text),
        () -> assertTrue(text.contains("5 Anne5 Fontaine & Bruno5 Rochat – 1"), text));
  }

  /**
   * In individual mode, each player has its place.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("In individual mode, each player has its place")
  void individualTest() throws Exception {
    final String text = Samples.text(Samples.pdf(out -> PlacesDocument.write(Samples.individualEdition(),
                                                                             Samples.OPTIONS, out)));
    assertAll(
        () -> assertTrue(text.contains("2 Zoé2 Müller 1"), text),
        () -> assertTrue(text.contains("3 Zoé3 Müller 2"), text));
  }

  /**
   * The places need the schedule of the edition.
   */
  @Test
  @DisplayName("An edition without schedule has no places")
  void noScheduleTest() {
    final Edition edition = Samples.teamEdition().withoutSchedule();
    assertThrows(IllegalStateException.class,
                 () -> PlacesDocument.write(edition, Samples.OPTIONS, new ByteArrayOutputStream()));
  }

  /**
   * A long list of participants continues on the next pages, each with the column titles, and the pages are
   * numbered.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A long list of places spreads over numbered pages, with the column titles on each")
  void pagesTest() throws Exception {
    TeamRegistrations teams = TeamRegistrations.empty();
    final List<TeamGame> games = new ArrayList<>();
    for (int i = 1; i <= MANY_TEAMS; i++) {
      teams = teams.add(new Team(new Player("A" + i, ""), new Player("B" + i, "")));
      if (i % 2 == 0) {
        games.add(new TeamGame(i / 2, i - 1, i));
      }
    }
    final Edition edition = Edition.create("Grand match", Samples.DATE, TournamentMode.TEAM)
                                   .withGames(1)
                                   .withRegistrations(teams)
                                   .withSchedule(new TeamSchedule(MANY_TEAMS, 1, List.of(new TeamRound(1, games),
                                                                                       new TeamRound(2, games))));
    final byte[] pdf = Samples.pdf(out -> PlacesDocument.write(edition, Samples.OPTIONS, out));
    try (PDDocument document = Loader.loadPDF(pdf)) {
      final int pages = document.getNumberOfPages();
      final PDFTextStripper stripper = Samples.stripper();
      stripper.setStartPage(2);
      stripper.setEndPage(2);
      final String second = stripper.getText(document);
      assertAll(
          () -> assertTrue(pages > 1, Integer.toString(pages)),
          () -> assertTrue(second.contains("N° Noms Partie 1 Partie 2"), second),
          () -> assertTrue(second.contains("Page 2 / " + pages), second));
    }
  }

}
