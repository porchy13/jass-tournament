package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.persistence.EditionStore;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.nio.file.Path;
import java.util.List;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the content of the games of an open edition (F15): the plan of the deals is changed while the
 * registration is open, saved at once with the registrations kept, and read-only once the schedule is computed; in
 * French, on a temporary data folder with fictitious teams ({@link ScoreFixtures}).
 */
@ExtendWith(ApplicationExtension.class)
final class DealsScreenTest {

  /** The edition whose registration is open. */
  private static final String OPEN = "Contenu ouvert";
  /** The edition whose schedule is computed. */
  private static final String SCHEDULED = "Contenu calculé";

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();

  /**
   * Fills the data folder with the editions of the scenarios, then starts the application in French.
   *
   * @param stage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage stage) throws Exception {
    final EditionStore store = new EditionStore(folder);
    store.create(ScoreFixtures.registered(OPEN));
    store.create(ScoreFixtures.scheduled(SCHEDULED));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                       Solving::of, false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Opens the content of the games of an edition.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   *
   * @throws Exception if the edition is not opened in time.
   */
  private static void open(final FxRobot robot, final String name) throws Exception {
    ScoreFixtures.open(robot, name, "dealsNav");
    await(() -> shown(robot, "dealList"));
  }

  /**
   * Returns the lists of the types of the deals shown.
   *
   * @param robot the TestFX robot.
   *
   * @return the lists, in the order of the deals.
   */
  @SuppressWarnings("unchecked")
  private static List<ComboBox<DealType>> types(final FxRobot robot) {
    return robot.lookup("#dealList .combo-box").queryAllAs(ComboBox.class).stream()
                .map(combo -> (ComboBox<DealType>) combo).toList();
  }

  /**
   * Reads the plan of an edition back from the data folder.
   *
   * @param name the name of the edition.
   *
   * @return the plan as saved.
   *
   * @throws Exception if the edition cannot be read.
   */
  private DealPlan saved(final String name) throws Exception {
    return ScoreFixtures.stored(folder, name).deals();
  }

  /**
   * F15: while the registration is open, a deal changes its type, a block is added then removed, and the plan of the
   * regulation is restored; every change is saved at once, the settings follow it and the registrations are kept.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F15: the content of the games is changed while the registration is open, saved at once")
  void openTest(final FxRobot robot) throws Exception {
    open(robot, OPEN);
    final boolean selected = robot.lookup("#dealsNav").queryAs(ToggleButton.class).isSelected();
    final boolean hint = robot.lookup("#closed").query().isVisible();
    final ComboBox<DealType> first = types(robot).getFirst();
    robot.interact(() -> first.setValue(DealType.SCHIEBER));
    await(() -> saved(OPEN).deals().getFirst() == DealType.SCHIEBER);
    press(robot, "addBlockButton");
    await(() -> saved(OPEN).size() == Regulation.DEALS_PER_GAME + Regulation.DEALS_PER_BLOCK);
    final String longer = text(robot, "summary");
    final String settings = text(robot, "settings");
    press(robot, "removeBlockButton");
    await(() -> saved(OPEN).size() == Regulation.DEALS_PER_GAME);
    press(robot, "regulationButton");
    await(() -> saved(OPEN).equals(DealPlan.REGULATION));
    assertAll(
        () -> assertTrue(selected),
        () -> assertFalse(hint),
        () -> assertEquals("20 donnes, 5 blocs de 4, 3 140 points par partie", longer),
        () -> assertTrue(settings.startsWith("Équipes, 2 parties, 20 donnes par partie")),
        () -> assertEquals(DealType.IMPOSED_HEARTS, types(robot).getFirst().getValue()),
        () -> assertEquals(ScoreFixtures.TEAMS, ScoreFixtures.stored(folder, OPEN).registrations().size()));
  }

  /**
   * F15: once the schedule is computed, the content of the games is shown read-only, with the note telling to reopen
   * the registration.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F15: once the schedule is computed, the content of the games is read-only")
  void scheduledTest(final FxRobot robot) throws Exception {
    open(robot, SCHEDULED);
    assertAll(
        () -> assertTrue(robot.lookup("#closed").query().isVisible()),
        () -> assertEquals("Les rencontres sont calculées : rouvrez les inscriptions pour modifier le contenu des "
                           + "parties.", text(robot, "closed")),
        () -> assertTrue(types(robot).getFirst().isDisabled()),
        () -> assertTrue(disabled(robot, "addBlockButton")),
        () -> assertTrue(disabled(robot, "regulationButton")),
        () -> assertEquals(DealPlan.REGULATION, saved(SCHEDULED)));
  }

}
