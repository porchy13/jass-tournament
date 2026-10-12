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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ScheduleDocument}: title, edition, rounds, tables, places, sides and participants sitting out.
 */
final class ScheduleDocumentTest {

  /** A number of teams that needs several pages. */
  private static final int MANY_TEAMS = 80;
  /** The number of the catch-up round of the large schedule. */
  private static final int CATCH_UP = 3;
  /** The number of tables of the catch-up round of the large schedule. */
  private static final int CATCH_UP_TABLES = 5;

  /**
   * Rule 6: the schedule lists each round with its tables, places, the side dealing first and the side receiving,
   * and the teams sitting out; it is titled with the edition.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 6: the schedule shows the tables, places, giver, receiver and teams sitting out")
  void teamTest() throws Exception {
    final byte[] pdf = Samples.pdf(out -> ScheduleDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    final String text = Samples.text(pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertAll(
          () -> assertTrue(text.contains("Tableau des rencontres"), text),
          () -> assertTrue(text.contains(Samples.EDITION_LINE), text),
          () -> assertTrue(text.contains("Partie 1"), text),
          () -> assertTrue(text.contains("Partie 2"), text),
          () -> assertTrue(text.contains("Donne en premier"), text),
          () -> assertTrue(text.contains("1 – 2"), text),
          () -> assertTrue(text.contains("Équipe 1 : Anne1 Fontaine & Bruno1 Rochat"), text),
          () -> assertTrue(text.contains("Au repos : Équipe 5"), text),
          () -> assertTrue(text.contains("Au repos : Équipe 1, Équipe 3, Équipe 4"), text),
          () -> assertTrue(text.contains("Page 1 / 2"), text),
          () -> assertTrue(text.contains("Au repos : Équipe 5\nPage 1 / 2\n"), text),
          () -> assertEquals(2, document.getNumberOfPages()),
          () -> assertTrue(document.getPage(0).getMediaBox().getWidth()
                           > document.getPage(0).getMediaBox().getHeight()),
          () -> assertEquals("Tableau des rencontres – Match aux cartes 2026",
                             document.getDocumentInformation().getTitle()));
    }
  }

  /**
   * In individual mode, each side shows its two partners with their numbers; a round where everyone plays has no
   * line of participants sitting out; the texts follow the language.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Individual mode and English texts")
  void individualTest() throws Exception {
    final String text = Samples.text(Samples.pdf(out -> ScheduleDocument.write(
        Samples.individualEdition(), new DocumentOptions(Locale.ENGLISH, null), out)));
    assertAll(
        () -> assertTrue(text.contains("Schedule"), text),
        () -> assertTrue(text.contains("Match individuel – 20 November 2026"), text),
        () -> assertTrue(text.contains("Round 1"), text),
        () -> assertTrue(text.contains("Zoé1 Müller (1) & Zoé2 Müller (2)"), text),
        () -> assertTrue(!text.contains("Sitting out"), text));
  }

  /**
   * A round whose tables do not fit on a page is split, and every round is split the same way: the same tables on
   * each page, the parts of even size; a shorter round (here a catch-up round of 5 tables) keeps the same ranges; the
   * pages are numbered.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A large schedule splits every round the same way, one part per landscape page")
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
                                   .withSchedule(new TeamSchedule(MANY_TEAMS, 1, List.of(
                                       new TeamRound(1, games), new TeamRound(2, games),
                                       new TeamRound(CATCH_UP, games.subList(0, CATCH_UP_TABLES)))));
    final byte[] pdf = Samples.pdf(out -> ScheduleDocument.write(edition, Samples.OPTIONS, out));
    try (PDDocument document = Loader.loadPDF(pdf)) {
      final int pages = document.getNumberOfPages();
      final List<String> texts = new ArrayList<>();
      for (int page = 1; page <= pages; page++) {
        final PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        texts.add(stripper.getText(document));
      }
      final int parts = (pages - 1) / 2;
      final int perPage = Math.ceilDiv(MANY_TEAMS / 2, parts);
      assertAll(
          () -> assertTrue(parts > 1, Integer.toString(pages)),
          () -> assertEquals(0, (pages - 1) % 2, Integer.toString(pages)),
          () -> assertTrue(texts.getFirst().contains("Partie 1 – tables 1 à " + perPage), texts.getFirst()),
          () -> assertTrue(texts.get(parts).contains("Partie 2 – tables 1 à " + perPage), texts.get(parts)),
          () -> assertTrue(texts.get(1).contains("Partie 1 – tables " + (perPage + 1) + " à "), texts.get(1)),
          () -> assertTrue(texts.get(parts + 1).contains("Partie 2 – tables " + (perPage + 1) + " à "),
                           texts.get(parts + 1)),
          () -> assertTrue(texts.getLast().contains("Partie 3 – tables 1 à " + CATCH_UP_TABLES), texts.getLast()),
          () -> assertTrue(texts.get(parts - 1).contains("Partie 1 – tables " + (perPage * (parts - 1) + 1)
                                                         + " à " + MANY_TEAMS / 2), texts.get(parts - 1)),
          () -> assertTrue(texts.get(1).contains("Page 2 / " + pages), texts.get(1)));
    }
  }

  /**
   * The schedule needs the schedule of the edition.
   */
  @Test
  @DisplayName("An edition without schedule has no schedule document")
  void noScheduleTest() {
    final Edition edition = Samples.teamEdition().withoutSchedule();
    assertThrows(IllegalStateException.class,
                 () -> ScheduleDocument.write(edition, Samples.OPTIONS, new ByteArrayOutputStream()));
  }

}
