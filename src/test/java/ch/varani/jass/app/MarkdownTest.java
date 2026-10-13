package ch.varani.jass.app;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Markdown}: the blocks and the inline elements of a text in Markdown become styled JavaFX nodes,
 * nothing is interpreted, and the nesting is capped. The toolkit is started by TestFX, the nodes are never shown.
 */
@ExtendWith(ApplicationExtension.class)
final class MarkdownTest {

  /** The number of blocks of {@link #NOTES}: two headings, each followed by a list. */
  private static final int NOTES_BLOCKS = 4;
  /** The number of blocks of the test of the other blocks, the link reference definition showing nothing. */
  private static final int OTHER_BLOCKS = 5;
  /** The index of the block of HTML in the test of the other blocks. */
  private static final int HTML_BLOCK = 3;

  /** The notes of a release, as cut from the changelog. */
  private static final String NOTES = """
      ### Added
      - **Update**: Help › Check for updates looks for a newer version,
        and says when the application is *up to date*.
      - The `.jass` files are converted.

      ### Fixed
      - A fix.
      """;

  /**
   * Returns the blocks of a rendered text.
   *
   * @param markdown the text, in Markdown.
   *
   * @return the nodes of its blocks.
   */
  private static List<Node> blocks(final String markdown) {
    final VBox box = Markdown.render(markdown);
    return box.getChildren();
  }

  /**
   * Returns the text shown by a node: its texts and labels, in their order.
   *
   * @param node the node.
   *
   * @return the text.
   */
  private static String text(final Node node) {
    return switch (node) {
      case Text text -> text.getText();
      case Label label -> label.getText();
      case Parent parent -> parent.getChildrenUnmodifiable().stream().map(MarkdownTest::text)
                                  .collect(Collectors.joining());
      default -> "";
    };
  }

  /**
   * Returns the texts of a flow.
   *
   * @param node the flow.
   *
   * @return its texts.
   */
  private static List<Text> texts(final Node node) {
    return ((TextFlow) node).getChildren().stream().map(Text.class::cast).toList();
  }

  /**
   * Returns the depth of a tree of nodes.
   *
   * @param node the root.
   *
   * @return 1 for a node without children, else 1 + the depth of its deepest child.
   */
  private static int depth(final Node node) {
    if (node instanceof final Parent parent) {
      return 1 + parent.getChildrenUnmodifiable().stream().mapToInt(MarkdownTest::depth).max().orElse(0);
    }
    return 1;
  }

  /**
   * F17: the notes of a release show their headings, their bullet lists, the strong and emphasized texts and the
   * code, styled, the lines of a paragraph joined.
   */
  @Test
  @DisplayName("F17: the notes of a release show their headings, lists and emphasis")
  void notesTest() {
    final VBox box = Markdown.render(NOTES);
    final List<Node> blocks = box.getChildren();
    final VBox list = (VBox) blocks.get(1);
    final HBox item = (HBox) list.getChildren().getFirst();
    final List<Text> first = texts(((VBox) item.getChildren().get(1)).getChildren().getFirst());
    final List<Text> second = texts(((VBox) ((HBox) list.getChildren().get(1)).getChildren().get(1))
                                        .getChildren().getFirst());
    assertAll(
        () -> assertTrue(box.getStyleClass().contains("markdown")),
        () -> assertEquals(NOTES_BLOCKS, blocks.size()),
        () -> assertTrue(blocks.getFirst().getStyleClass().contains("md-heading")),
        () -> assertEquals("Added", text(blocks.getFirst())),
        () -> assertTrue(list.getStyleClass().contains("md-list")),
        () -> assertEquals(2, list.getChildren().size()),
        () -> assertTrue(item.getStyleClass().contains("md-item")),
        () -> assertEquals("•", ((Label) item.getChildren().getFirst()).getText()),
        () -> assertTrue(item.getChildren().getFirst().getStyleClass().contains("md-marker")),
        () -> assertEquals("Update", first.getFirst().getText()),
        () -> assertEquals(List.of("md-text", "md-strong"), first.getFirst().getStyleClass()),
        () -> assertEquals(": Help › Check for updates looks for a newer version, and says when the application is "
                           + "up to date.", first.stream().skip(1).map(Text::getText).collect(Collectors.joining())),
        () -> assertTrue(first.stream().anyMatch(text -> "up to date".equals(text.getText())
                                                         && text.getStyleClass().contains("md-emphasis"))),
        () -> assertTrue(second.stream().anyMatch(text -> ".jass".equals(text.getText())
                                                          && text.getStyleClass().contains("md-code"))),
        () -> assertEquals("Fixed", text(blocks.get(2))),
        () -> assertEquals("•A fix.", text(blocks.getLast())));
  }

  /**
   * An ordered list numbers its items from its first number, with its delimiter; a list may hold another one, and an
   * emphasis a strong emphasis.
   */
  @Test
  @DisplayName("An ordered list numbers its items from its first number")
  void orderedTest() {
    final List<Node> blocks = blocks("3) First\n4) ***Second***\n   1. Inner\n");
    final VBox list = (VBox) blocks.getFirst();
    final HBox second = (HBox) list.getChildren().get(1);
    final VBox content = (VBox) second.getChildren().get(1);
    final Text strong = texts(content.getChildren().getFirst()).getFirst();
    assertAll(
        () -> assertEquals("3)", ((Label) ((HBox) list.getChildren().getFirst()).getChildren().getFirst()).getText()),
        () -> assertEquals("4)", ((Label) second.getChildren().getFirst()).getText()),
        () -> assertTrue(strong.getStyleClass().containsAll(List.of("md-emphasis", "md-strong"))),
        () -> assertEquals("1.Inner", text(content.getChildren().get(1))));
  }

  /**
   * The other blocks: a quote is indented, the blocks of code and of HTML are shown as typed, a thematic break is a
   * separator, a link reference definition shows nothing.
   */
  @Test
  @DisplayName("Quotes, code, HTML and breaks are shown, nothing is interpreted")
  void blocksTest() {
    final List<Node> blocks = blocks("""
        > Quoted

        ```
        fenced
        ```

            indented

        <div>block</div>

        ---

        [id]: https://example.org
        """);
    assertAll(
        () -> assertEquals(OTHER_BLOCKS, blocks.size()),
        () -> assertTrue(blocks.getFirst().getStyleClass().contains("md-quote")),
        () -> assertEquals("Quoted", text(blocks.getFirst())),
        () -> assertEquals("fenced", text(blocks.get(1))),
        () -> assertTrue(blocks.get(1).getStyleClass().contains("md-code-block")),
        () -> assertEquals("indented", text(blocks.get(2))),
        () -> assertEquals("<div>block</div>", text(blocks.get(HTML_BLOCK))),
        () -> assertInstanceOf(Separator.class, blocks.getLast()));
  }

  /**
   * A link or an image shows its text only, inline HTML is shown as typed, a hard line break breaks the line.
   */
  @Test
  @DisplayName("Links and images show their text, inline HTML is shown as typed")
  void inlinesTest() {
    final List<Node> blocks = blocks("A [link](https://example.org) and ![an image](https://example.org/a.png)  \n"
                                     + "<b>bold</b>");
    assertEquals("A link and an image\n<b>bold</b>", text(blocks.getFirst()));
  }

  /**
   * A text nested deeper than the cap stays text: it cannot exhaust the stack.
   */
  @Test
  @DisplayName("The nesting of the blocks and of the inline elements is capped")
  void nestingTest() {
    final int deep = 10_000;
    final VBox quotes = Markdown.render(">".repeat(deep) + " deep");
    final VBox emphasis = Markdown.render("*".repeat(deep) + "deep" + "*".repeat(deep));
    assertAll(
        () -> assertTrue(depth(quotes) < Markdown.MAX_NESTING * 2 + 2, String.valueOf(depth(quotes))),
        () -> assertTrue(text(quotes).endsWith("deep")),
        () -> assertTrue(text(emphasis).contains("deep")));
  }

}
