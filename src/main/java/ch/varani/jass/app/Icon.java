package ch.varani.jass.app;

import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * The icons of the user interface (DESIGN.md §8): one family of line icons drawn in a square of {@value #SIZE}
 * pixels, with strokes of 1.6 pixels and round ends (style class {@code icon}), in the colour of the text, or of the
 * accent for the active element.
 *
 * <p>An icon is never shown alone: an icon button gets a tooltip and an accessible text
 * ({@link #decorate(Labeled, String)}).</p>
 *
 * <p>Immutable, safe for concurrent use; the nodes it creates are confined to the JavaFX Application Thread.</p>
 */
enum Icon {

  /** A plus: create. */
  PLUS("M8 3v10M3 8h10"),
  /** A magnifying glass: search. */
  SEARCH("M7 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10ZM10.6 10.6l3.4 3.4"),
  /** A calendar: the editions. */
  CALENDAR("M2.5 4h11v9.5h-11zM2.5 7h11M5.5 2v3M10.5 2v3"),
  /** Two persons: the directory. */
  PEOPLE("M6 7a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM1.5 14c0-2.5 2-4.5 4.5-4.5s4.5 2 4.5 4.5M10.5 2.2a2.5 2.5 0 0 1"
         + " 0 4.6M12 9.8c1.5.6 2.5 2.2 2.5 4.2"),
  /** A bin: the trash, a deletion. */
  TRASH("M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.8 6.5v4.5M9.2 6.5v4.5"),
  /** A list: the registration. */
  LIST("M5.5 4h8M5.5 8h8M5.5 12h8M2.5 4h.1M2.5 8h.1M2.5 12h.1"),
  /** A grid: the schedule. */
  GRID("M2.5 2.5h4.5v4.5h-4.5zM9 2.5h4.5v4.5H9zM2.5 9h4.5v4.5h-4.5zM9 9h4.5v4.5H9z"),
  /** A pencil: the entry of the scores. */
  PENCIL("M10.5 2.5l3 3-8 8h-3v-3zM9 4l3 3"),
  /** A podium: the ranking. */
  PODIUM("M1.5 13.5h13M5.5 13.5V6.5h5v7M1.5 13.5V9.5h4M10.5 13.5V9h4v4.5"),
  /** A screen on its foot: the external display. */
  SCREEN("M2 2.5h12v8.5H2zM5.5 14h5M8 11v3"),
  /** A wallet: the accounting. */
  WALLET("M2 4.5h11.5v9H2zM2 4.5l9-2.5v2.5M10.5 8h3v2.5h-3z"),
  /** An open folder: open. */
  OPEN("M2 4.5V3h4l1.5 1.5H14v8.5H2zM2 7h12"),
  /** Two sheets: duplicate, copy. */
  COPY("M5.5 5.5h8v8h-8zM10.5 5.5V2.5h-8v8h3"),
  /** An arrow into a tray: import. */
  IMPORT("M8 2v8M5 7l3 3 3-3M2.5 10.5v3h11v-3"),
  /** An arrow out of a tray: export. */
  EXPORT("M8 10V2M5 5l3-3 3 3M2.5 10.5v3h11v-3"),
  /** An arrow turning back: restore. */
  RESTORE("M3 8a5 5 0 1 0 1.5-3.5M3 2.5v3h3"),
  /** Two arrows joining: merge. */
  MERGE("M3 2.5v3c0 2 1.5 3 5 3s5 1 5 3v2M13 2.5v3c0 1.4-.8 2.3-2.4 2.7M8 8.5v5"),
  /** An envelope: e-mail addresses. */
  MAIL("M2 3.5h12v9H2zM2 4l6 5 6-5"),
  /** A phone: phone numbers. */
  PHONE("M4.5 1.5h7v13h-7zM7 12.5h2"),
  /** A sheet: a document. */
  DOCUMENT("M4 1.5h5.5L12.5 4.5v10H4zM9.5 1.5v3h3M6 8h4.5M6 10.5h4.5"),
  /** A cross: close. */
  CLOSE("M4 4l8 8M12 4l-8 8"),
  /** Something to check: a warning triangle. */
  WARNING("M8 2.5l6 10.5H2zM8 6.5v3M8 11.2h.1");

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The size of the square of an icon, in pixels. */
  static final double SIZE = 16;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The drawing of the icon, as an SVG path in the square. */
  private final String path;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an icon.
   *
   * @param path the drawing of the icon, as an SVG path in a square of {@value #SIZE} pixels.
   */
  Icon(final String path) {
    this.path = path;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Creates the node of the icon: the drawing centred in its square.
   *
   * @return the node.
   */
  Node graphic() {
    final SVGPath drawing = new SVGPath();
    drawing.setContent(path);
    drawing.getStyleClass().add("icon");
    final StackPane box = new StackPane(drawing);
    box.setMinSize(SIZE, SIZE);
    box.setPrefSize(SIZE, SIZE);
    box.setMaxSize(SIZE, SIZE);
    return box;
  }

  /**
   * Shows the icon in a button or a label, with a tooltip and an accessible text.
   *
   * @param control the button or label.
   * @param description what the control does, shown in the tooltip and read by the screen readers.
   */
  void decorate(final Labeled control, final String description) {
    control.setGraphic(graphic());
    control.setTooltip(new Tooltip(description));
    control.setAccessibleText(description);
  }

}
