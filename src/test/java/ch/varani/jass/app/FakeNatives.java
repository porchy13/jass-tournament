package ch.varani.jass.app;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Windows of the system replaced for the tests of the user interface: the file choosers give the files queued by the
 * test (nothing when the queue is empty, as a cancelled dialog), the questions get the answers queued by the test
 * (the button of the action or of the cancellation; nothing when the queue is empty, as a closed box) and are
 * recorded, the message boxes shown without waiting and the documents opened are recorded, never shown.
 *
 * <p>Confined to the test and the JavaFX Application Thread; the notices may be read from the test thread.</p>
 */
final class FakeNatives {

  /** The files given by the next file choosers. */
  private final Deque<File> files = new ArrayDeque<>();
  /** The answers of the next questions: {@code true} for OK. */
  private final Deque<Boolean> answers = new ArrayDeque<>();
  /** The message boxes shown without waiting. */
  private final List<Alert> notices = new CopyOnWriteArrayList<>();
  /** The addresses of the documents opened with the applications of the system. */
  private final List<String> opened = new CopyOnWriteArrayList<>();
  /** The message boxes that waited for an answer. */
  private final List<Alert> questions = new CopyOnWriteArrayList<>();

  /**
   * Queues the file given by the next file chooser.
   *
   * @param file the file.
   *
   * @return these windows.
   */
  FakeNatives choose(final File file) {
    files.add(file);
    return this;
  }

  /**
   * Queues the answer of the next question.
   *
   * @param ok {@code true} for OK, {@code false} for Cancel.
   *
   * @return these windows.
   */
  FakeNatives answer(final boolean ok) {
    answers.add(ok);
    return this;
  }

  /**
   * Returns the message boxes shown without waiting.
   *
   * @return the boxes, in the order they were shown.
   */
  List<Alert> notices() {
    return notices;
  }

  /**
   * Returns the message boxes that waited for an answer.
   *
   * @return the boxes, in the order they were shown.
   */
  List<Alert> questions() {
    return questions;
  }

  /**
   * Returns the documents opened with the applications of the system.
   *
   * @return their addresses, in the order they were opened.
   */
  List<String> opened() {
    return opened;
  }

  /**
   * Returns the texts of the message boxes shown without waiting.
   *
   * @return the texts, in the order they were shown.
   */
  List<String> texts() {
    return notices.stream().map(Alert::getContentText).toList();
  }

  /**
   * Returns the windows of the system for the application.
   *
   * @return the replaced windows.
   */
  Dialogs.Natives natives() {
    return new Dialogs.Natives((chooser, owner) -> files.poll(), (chooser, owner) -> files.poll(), alert -> {
      questions.add(alert);
      if (answers.isEmpty()) {
        return Optional.empty();
      }
      final ButtonBar.ButtonData wanted = answers.poll() ? ButtonBar.ButtonData.OK_DONE
                                                         : ButtonBar.ButtonData.CANCEL_CLOSE;
      return alert.getButtonTypes().stream().filter(button -> button.getButtonData() == wanted).findFirst();
    }, notices::add, (services, uri) -> opened.add(uri));
  }

}
