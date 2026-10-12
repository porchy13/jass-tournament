package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.report.DocumentOptions;
import ch.varani.jass.report.ScheduleDocument;
import ch.varani.jass.solver.SolverOptions;
import ch.varani.jass.solver.SolverResult;
import ch.varani.jass.solver.TeamScheduleSolver;
import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * The smoke test of the packaged application (AGENTS.md §7), run by {@code Launcher --smoke-test} on each installer
 * built: it checks that the parts that need native code or resources of the package work, without showing a window.
 *
 * <p><b>Steps</b>: the native libraries of OR-Tools are loaded and the schedule of a small fictitious team tournament
 * is computed; its schedule is written as a PDF document (PDFBox and the embedded fonts), into no file; the JavaFX
 * toolkit is started (its native libraries), then stopped. A failing step throws an exception, so that the launcher
 * ends with an error; otherwise the launcher ends normally.</p>
 *
 * <p>Utility class without state; run once per process, as the JavaFX toolkit can start only once.</p>
 */
final class SmokeTest {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The argument of the launcher that runs the smoke test. */
  static final String ARGUMENT = "--smoke-test";
  /** The number of teams of the fictitious tournament: odd, so that the catch-up round is computed too. */
  static final int TEAMS = 5;
  /** The date of the fictitious tournament. */
  private static final LocalDate DATE = LocalDate.of(2026, 1, 1);

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private SmokeTest() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Runs the smoke test with a fictitious tournament of {@value #TEAMS} teams.
   *
   * @param teams the number of teams of the fictitious tournament, at least 1.
   *
   * @throws IllegalStateException if no schedule is found.
   * @throws IOException if the PDF document cannot be written.
   * @throws InterruptedException if the wait for the JavaFX toolkit is interrupted.
   */
  static void run(final int teams) throws IOException, InterruptedException {
    final SolverResult<TeamSchedule> result = new TeamScheduleSolver(teams, Regulation.GAMES, SolverOptions.DEFAULT)
        .solve();
    final TeamSchedule schedule = switch (result) {
      case SolverResult.Solved<TeamSchedule>(final TeamSchedule solved) -> solved;
      case SolverResult.Unsolved<TeamSchedule>(final SolverResult.Reason reason) ->
          throw new IllegalStateException("No schedule: " + reason);
    };
    TeamRegistrations registrations = TeamRegistrations.empty();
    for (int i = 1; i <= teams; i++) {
      registrations = registrations.add(new Team(new Player("Smoke" + i, "A"), new Player("Smoke" + i, "B")));
    }
    final Edition edition = Edition.create("Smoke test", DATE, TournamentMode.TEAM).withRegistrations(registrations)
                                   .withSchedule(schedule);
    ScheduleDocument.write(edition, new DocumentOptions(Locale.ROOT, null), OutputStream.nullOutputStream());
    final CountDownLatch started = new CountDownLatch(1);
    Platform.startup(started::countDown);
    started.await();
    Platform.exit();
  }

}
