package ch.varani.jass.app;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * The detail panel of a view (DESIGN.md §6): at the right of the list, in a split pane whose divider is remembered;
 * when the window is narrower than {@value #NARROW} pixels, the panel leaves the split pane and floats over the list,
 * shown when the user opens an element ({@link #open()}) and hidden by its close button or the Escape key.
 *
 * <p><b>Algorithm</b>: once the view has its width, the divider is placed where it was, or so that the panel is
 * {@value #WIDTH} pixels wide, then its moves are remembered. The width of the scene is followed; when it crosses
 * {@value #NARROW}, the panel moves between
 * the split pane and the stack pane of the view (style class {@code floating}, close button shown). Opening the
 * floating panel slides it in ({@link Motion#slideIn(Region, boolean)}), unless the animations are reduced.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DetailLayout {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The width of the window below which the detail panel floats, in pixels. */
  static final double NARROW = 1100;
  /** The width of the docked panel when no position is remembered, in pixels (DESIGN.md §5). */
  static final double WIDTH = 340;
  /** The style class of the floating panel. */
  private static final String FLOATING_STYLE = "floating";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The stack pane of the view, holding the split pane and the floating panel. */
  private final StackPane body;
  /** The split pane of the list and of the docked panel. */
  private final SplitPane split;
  /** The detail panel. */
  private final Region detail;
  /** The button closing the floating panel. */
  private final Button close;
  /** Whether the panel floats. */
  private boolean floating;
  /** Whether the divider has been placed, once the view has its width. */
  private boolean placed;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Lays out the detail panel of a view, docked in the split pane, and follows the width of the window.
   *
   * @param main the main window, which keeps the position of the divider.
   * @param body the stack pane of the view, holding the split pane.
   * @param split the split pane, holding the list then the detail panel.
   * @param detail the detail panel.
   * @param close the button closing the floating panel.
   */
  DetailLayout(final MainController main, final StackPane body, final SplitPane split, final Region detail,
               final Button close) {
    this.main = main;
    this.body = body;
    this.split = split;
    this.detail = detail;
    this.close = close;
    SplitPane.setResizableWithParent(detail, false);
    StackPane.setAlignment(detail, Pos.TOP_RIGHT);
    close.setOnAction(event -> hide());
    detail.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE && floating) {
        hide();
      }
    });
    close.setVisible(false);
    close.setManaged(false);
    body.widthProperty().addListener((property, before, after) -> update(body.getScene().getWidth()));
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Docks the panel in the split pane, or makes it float, after the width of the window.
   *
   * @param width the width of the window.
   */
  void update(final double width) {
    if (!placed) {
      placed = true;
      dock();
      split.getDividers().getFirst().positionProperty().addListener(
          (property, before, after) -> main.setDetailDivider(after.doubleValue()));
    }
    final boolean narrow = width < NARROW;
    if (narrow == floating) {
      return;
    }
    floating = narrow;
    close.setVisible(narrow);
    close.setManaged(narrow);
    if (narrow) {
      split.getItems().remove(detail);
      detail.getStyleClass().add(FLOATING_STYLE);
      detail.setVisible(false);
      body.getChildren().add(detail);
      return;
    }
    body.getChildren().remove(detail);
    detail.getStyleClass().remove(FLOATING_STYLE);
    detail.setVisible(true);
    split.getItems().add(detail);
    dock();
  }

  /**
   * Places the divider of the docked panel where it was, or so that the panel is {@value #WIDTH} pixels wide.
   */
  private void dock() {
    final double position = main.settings().detail();
    split.setDividerPositions(Double.isNaN(position) ? 1 - WIDTH / body.getWidth() : position);
  }

  /**
   * Shows the panel when it floats, sliding it in; nothing changes when it is docked.
   */
  void open() {
    if (floating && !detail.isVisible()) {
      detail.setVisible(true);
      Motion.slideIn(detail, main.reducedMotion());
    }
  }

  /**
   * Hides the floating panel.
   */
  void hide() {
    detail.setVisible(false);
  }

}
