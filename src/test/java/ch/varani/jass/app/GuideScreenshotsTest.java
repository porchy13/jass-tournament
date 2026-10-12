package ch.varani.jass.app;

import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.LogoStore;
import ch.varani.jass.report.DocumentOptions;
import ch.varani.jass.report.PlacesDocument;
import ch.varani.jass.report.ScheduleDocument;
import ch.varani.jass.report.ScoreCardsDocument;
import ch.varani.jass.report.ScoreSheetsDocument;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.type;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator of the screenshots of the user guide ({@code guide/images/<language>/}): for each language of the
 * application, the application is started headless on the fictitious data of {@link GuideFixtures}, each screen
 * described by the guide is shown as a user would, and the window (or the external display, or the settings) is
 * written as a PNG image at twice its size; the offer of an update (a fictitious release) and the first page of each
 * PDF document are written as images too.
 *
 * <p>The guide shows every screen at the same scale, as on a computer: the main window takes {@value #SHOWN_WIDTH}
 * pixels, and a smaller window (about, update, settings) its share of them. The generator writes the width of each
 * screen into the attribute {@code width} of its {@code <img>} in the guide of the language.</p>
 *
 * <p>It is not a test of the application and does not run with the quality gate: it is tagged {@code screenshots}
 * (excluded by default) and runs only when the system property {@value #FOLDER} names the folder of the images, as
 * the Maven profile {@code guide-screenshots} does ({@code mvn -P guide-screenshots clean verify}, which runs the
 * whole quality gate too). The clock, the solver and the generator of the points are fixed, so that the same
 * application always gives the same images.</p>
 */
@ExtendWith(ApplicationExtension.class)
@Tag("screenshots")
@EnabledIfSystemProperty(named = GuideScreenshotsTest.FOLDER, matches = ".+")
final class GuideScreenshotsTest {

  /** The system property naming the folder of the images. */
  static final String FOLDER = "ch.varani.jass.screenshots";
  /** The width of the main window. */
  private static final int WIDTH = 1280;
  /** The height of the main window. */
  private static final int HEIGHT = 800;
  /** The scale of the images of the screens, for the screens of high density. */
  private static final double SCALE = 2;
  /** The width of the main window in the guide, in pixels: the screens are shown at this scale. */
  private static final int SHOWN_WIDTH = 800;
  /** The resolution of the images of the documents, in dots per inch. */
  private static final float DPI = 110;
  /** The number of ranks revealed on the external display: the podium stays hidden. */
  private static final int REVEALED = GuideFixtures.TEAMS - 3;
  /** The row of the score sheet shown, in the list of the games. */
  private static final int GAME_ROW = 5;
  /** The row of the entry shown, in the accounting. */
  private static final int ENTRY_ROW = 1;
  /** The last name of the person shown in the directory: a player of every edition. */
  private static final String SHOWN = "Fontaine";
  /** The image of the logo of the documents in the guide: the icon of the application. */
  private static final Path LOGO = Path.of("src", "packaging", "icons", "jass-tournament.png");
  /** The name typed in the registration, to show the proposals of the directory. */
  private static final String TYPED = "St";
  /** The last name of the person chosen among the proposals: a player of 2026 not registered yet. */
  private static final String CHOSEN = "Steiner";
  /** The names of the new edition typed in the form, per language. */
  private static final Map<String, String> NEW_NAMES = Map.of(
      "fr", "Tournoi d’été 2027", "de", "Sommerturnier 2027", "it", "Torneo estivo 2027",
      "en", "Summer tournament 2027");
  /** The time of the start signal of the round on the external display. */
  private static final LocalTime START = LocalTime.of(15, 30);
  /**
   * The time elapsed since the start signal when the external display is captured: a multiple of twice the time of a
   * page of the display plus 3 seconds, so that its first page is shown with the bar of the next page partly empty.
   */
  private static final Duration ELAPSED = Duration.parse("PT23M23S");
  /** The date of the new edition typed in the form. */
  private static final LocalDate NEW_DATE = LocalDate.of(2027, Month.JUNE, 19);

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** The primary stage, provided by TestFX. */
  private Stage stage;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The clock of the application, moved forward during the round shown on the external display. */
  private final MutableClock clock = new MutableClock(UiSupport.TODAY.atTime(START).toInstant(ZoneOffset.UTC));
  /** The folder of the images of the language. */
  private Path images;

  /**
   * Keeps the primary stage: the application is started by the test, in its language.
   *
   * @param primary the stage provided by TestFX.
   */
  @Start
  void start(final Stage primary) {
    stage = primary;
  }

  /**
   * Returns the languages of the application.
   *
   * @return the locales of the guides.
   */
  static Stream<Locale> languages() {
    return Stream.of(Languages.FRENCH, Languages.GERMAN, Languages.ITALIAN, Languages.ENGLISH);
  }

  /**
   * Shows each screen of the guide in a language and writes its image.
   *
   * @param locale the language.
   * @param robot the TestFX robot.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  @ParameterizedTest
  @MethodSource("languages")
  @DisplayName("Screenshots of the user guide")
  void screenshotsTest(final Locale locale, final FxRobot robot) throws Exception {
    images = Path.of(System.getProperty(FOLDER), locale.getLanguage());
    Files.createDirectories(images);
    GuideFixtures.fill(folder, locale, LOGO);
    final FakeUpdater updater = new FakeUpdater(FakeUpdater.release(), null, null);
    final JassApplication application = new JassApplication(new AppContext(folder, locale, natives.natives(),
                                                                           clock, Solving::of, false, updater));
    robot.interact(() -> {
      application.start(stage);
      stage.setWidth(WIDTH);
      stage.setHeight(HEIGHT);
    });
    update(robot);
    editions(robot, locale);
    registration(robot);
    edition(robot);
    display(robot);
    others(robot);
    documents(locale);
    application.stop();
    assertTrue(Files.exists(images.resolve("about.png")));
  }

  /**
   * Captures the list of the editions and the form of a new edition.
   *
   * @param robot the TestFX robot.
   * @param locale the language.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  @SuppressWarnings("unchecked")
  private void editions(final FxRobot robot, final Locale locale) throws Exception {
    await(() -> shown(robot, "openButton"));
    final TableView<EditionHeader> table = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> table.getSelectionModel().select(table.getItems().stream()
        .filter(header -> header.name().equals(GuideFixtures.EDITION)).findFirst().orElseThrow()));
    capture(robot, "editions");
    press(robot, "newButton");
    await(() -> shown(robot, "nameField"));
    type(robot, "nameField", NEW_NAMES.get(locale.getLanguage()));
    final DatePicker date = robot.lookup("#datePicker").queryAs(DatePicker.class);
    robot.interact(() -> date.setValue(NEW_DATE));
    capture(robot, "edition-form");
    press(robot, "cancelButton");
  }

  /**
   * Captures the offer of an update, shown at start-up and postponed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the offer is not shown in time or the image cannot be written.
   */
  private void update(final FxRobot robot) throws Exception {
    await(() -> !natives.questions().isEmpty());
    capture(robot, natives.questions().getFirst().getDialogPane(), "update");
  }

  /**
   * Captures the registration of the next edition: a name being typed with the proposals of the directory, then the
   * person chosen with its previous team mate proposed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  @SuppressWarnings("unchecked")
  private void registration(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, GuideFixtures.NEXT, "registrationNav");
    type(robot, "first1", TYPED);
    final ListView<Person> proposals = robot.lookup("#proposals1").queryAs(ListView.class);
    await(() -> proposals.isVisible() && !proposals.getItems().isEmpty());
    capture(robot, "registration");
    robot.interact(() -> proposals.getSelectionModel().select(proposals.getItems().stream()
        .filter(person -> CHOSEN.equals(person.lastName())).findFirst().orElseThrow()));
    await(() -> robot.lookup("#previousButton").query().isVisible());
    capture(robot, "registration-previous");
    navigate(robot, "editionsNav");
  }

  /**
   * Captures the schedule, the scores, the ranking and the accounting of the edition of the guide.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  private void edition(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, GuideFixtures.EDITION, "scheduleNav");
    await(() -> shown(robot, "sheetsButton"));
    capture(robot, "schedule");
    navigate(robot, "scoresNav");
    await(() -> shown(robot, "nextButton"));
    select(robot, "table", GAME_ROW);
    await(() -> robot.lookup("#sheet").query().isVisible());
    capture(robot, "scores");
    navigate(robot, "rankingNav");
    await(() -> shown(robot, "drawButton"));
    if (!disabled(robot, "drawButton")) {
      natives.answer(true);
      press(robot, "drawButton");
      await(() -> disabled(robot, "drawButton"));
    }
    capture(robot, "ranking");
    navigate(robot, "accountingNav");
    await(() -> shown(robot, "entries"));
    select(robot, "entries", ENTRY_ROW);
    await(() -> robot.lookup("#detail").query().isVisible());
    capture(robot, "accounting");
  }

  /**
   * Captures the control of the external display and the display itself: a round started, then the final ranking
   * partly revealed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  private void display(final FxRobot robot) throws Exception {
    navigate(robot, "displayNav");
    await(() -> shown(robot, "startButton"));
    press(robot, "openButton");
    await(() -> display() != null);
    press(robot, "startButton");
    clock.advance(ELAPSED);
    final Label timer = (Label) display().getScene().lookup("#timer");
    await(() -> !timer.getText().equals(robot.lookup("#minutes").queryAs(Spinner.class).getValue() + ":00"));
    capture(robot, "display-control");
    write(snapshot(robot, () -> display().getScene().getRoot()), "display-round");
    press(robot, "rankingButton");
    for (int i = 0; i < REVEALED; i++) {
      press(robot, "revealButton");
    }
    write(snapshot(robot, () -> display().getScene().getRoot()), "display-ranking");
    press(robot, "closeButton");
    await(() -> display() == null);
  }

  /**
   * Captures the directory, with a person selected, the settings, with the logo of the documents, and the window about
   * the application.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if a screen is not shown in time or an image cannot be written.
   */
  @SuppressWarnings("unchecked")
  private void others(final FxRobot robot) throws Exception {
    navigate(robot, "directoryNav");
    await(() -> shown(robot, "emailsButton"));
    final TableView<Person> persons = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> persons.getSelectionModel().select(persons.getItems().stream()
        .filter(person -> SHOWN.equals(person.lastName())).findFirst().orElseThrow()));
    await(() -> robot.lookup("#detail").query().isVisible());
    capture(robot, "directory");
    menu(robot, "optionsItem");
    final Alert alert = natives.questions().getLast();
    final DialogPane pane = alert.getDialogPane();
    await(() -> pane.lookup("#logoView") instanceof final ImageView view && view.getImage() != null);
    capture(robot, pane, "settings");
    menu(robot, "aboutItem");
    capture(robot, natives.questions().getLast().getDialogPane(), "about");
  }

  /**
   * Captures a dialog at its preferred size.
   *
   * @param robot the TestFX robot.
   * @param pane the content of the dialog.
   * @param name the name of the image.
   *
   * @throws IOException if the image cannot be written.
   */
  private void capture(final FxRobot robot, final DialogPane pane, final String name) throws IOException {
    write(snapshot(robot, () -> {
      pane.applyCss();
      pane.resize(pane.prefWidth(-1), pane.prefHeight(-1));
      pane.layout();
      return pane;
    }), name);
  }

  /**
   * Writes the first page of each PDF document of the edition of the guide as an image, with the logo.
   *
   * @param locale the language of the documents.
   *
   * @throws Exception if the edition cannot be read or an image cannot be written.
   */
  private void documents(final Locale locale) throws Exception {
    final Edition edition = ScoreFixtures.stored(folder, GuideFixtures.EDITION);
    final DocumentOptions options = new DocumentOptions(locale, new LogoStore(folder).load().orElseThrow());
    final Map<String, Document> documents = Map.of(
        "document-schedule", out -> ScheduleDocument.write(edition, options, out),
        "document-places", out -> PlacesDocument.write(edition, options, out),
        "document-cards", out -> ScoreCardsDocument.write(edition, options, out),
        "document-sheets", out -> ScoreSheetsDocument.write(edition, options, out));
    for (final Map.Entry<String, Document> document : documents.entrySet()) {
      final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
      document.getValue().write(pdf);
      try (PDDocument loaded = Loader.loadPDF(pdf.toByteArray())) {
        ImageIO.write(new PDFRenderer(loaded).renderImageWithDPI(0, DPI), "png",
                      images.resolve(document.getKey() + ".png").toFile());
      }
    }
  }

  /**
   * Shows a part of the side navigation.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item of the navigation.
   */
  private static void navigate(final FxRobot robot, final String id) {
    final ToggleButton item = robot.lookup("#" + id).queryAs(ToggleButton.class);
    robot.interact(item::fire);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Selects a row of a table.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the table.
   * @param row the index of the row.
   */
  private static void select(final FxRobot robot, final String id, final int row) {
    final TableView<?> table = robot.lookup("#" + id).queryAs(TableView.class);
    robot.interact(() -> table.getSelectionModel().select(row));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Returns the window of the external display, if it is shown.
   *
   * @return the window, or {@code null}.
   */
  private Stage display() {
    final List<Window> windows = Window.getWindows().stream()
                                       .filter(window -> window != stage && window instanceof Stage
                                                         && window.isShowing())
                                       .toList();
    return windows.isEmpty() ? null : (Stage) windows.getFirst();
  }

  /**
   * Captures the main window.
   *
   * @param robot the TestFX robot.
   * @param name the name of the image, without extension.
   *
   * @throws IOException if the image cannot be written.
   */
  private void capture(final FxRobot robot, final String name) throws IOException {
    write(snapshot(robot, () -> stage.getScene().getRoot()), name);
  }

  /**
   * Takes a snapshot of a node, at {@link #SCALE} times its size, once the events are processed.
   *
   * @param robot the TestFX robot.
   * @param node the node, computed on the JavaFX thread.
   *
   * @return the image.
   */
  private static WritableImage snapshot(final FxRobot robot, final NodeSource node) {
    WaitForAsyncUtils.waitForFxEvents();
    final AtomicReference<WritableImage> image = new AtomicReference<>();
    robot.interact(() -> {
      final SnapshotParameters parameters = new SnapshotParameters();
      parameters.setTransform(Transform.scale(SCALE, SCALE));
      image.set(node.get().snapshot(parameters, null));
    });
    return image.get();
  }

  /**
   * Writes an image as a PNG file in the folder of the language.
   *
   * @param image the image.
   * @param name the name of the image, without extension.
   *
   * @throws IOException if the file cannot be written.
   */
  private void write(final WritableImage image, final String name) throws IOException {
    final int width = (int) image.getWidth();
    final int height = (int) image.getHeight();
    final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final PixelReader reader = image.getPixelReader();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        out.setRGB(x, y, reader.getArgb(x, y));
      }
    }
    ImageIO.write(out, "png", images.resolve(name + ".png").toFile());
    showAtScale(name, width / SCALE);
  }

  /**
   * Writes the width of a screen into the guide of the language, so that every screen is shown at the same scale:
   * the attribute {@code width} of its {@code <img>}, the share of {@value #SHOWN_WIDTH} pixels its window takes of
   * the main window.
   *
   * @param name the name of the image, without extension.
   * @param width the width of the window, in pixels of the screen.
   *
   * @throws IOException if the guide cannot be read or written.
   */
  private void showAtScale(final String name, final double width) throws IOException {
    final String language = images.getFileName().toString();
    final Path guide = images.getParent().getParent().resolve(language + ".md");
    final String text = Files.readString(guide);
    final Pattern image = Pattern.compile("(<img src=\"images/" + language + "/" + name + "\\.png\"[^>]*width=\")"
                                          + "\\d+");
    final String shown = image.matcher(text).replaceAll("$1" + Math.round(width * SHOWN_WIDTH / WIDTH));
    Files.writeString(guide, shown);
  }

  /**
   * A node to capture, computed on the JavaFX thread.
   */
  @FunctionalInterface
  private interface NodeSource {

    /**
     * Returns the node.
     *
     * @return the node.
     */
    Node get();

  }

  /**
   * A PDF document written to a stream.
   */
  @FunctionalInterface
  private interface Document {

    /**
     * Writes the document.
     *
     * @param out the stream.
     *
     * @throws IOException if the document cannot be written.
     */
    void write(OutputStream out) throws IOException;

  }

}
