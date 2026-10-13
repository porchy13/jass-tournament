package ch.varani.jass.app;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shows a text written in Markdown (the notes of a release, a section of the changelog) as JavaFX nodes styled by the
 * style sheets of the application, never as HTML: the text is parsed by commonmark-java (CommonMark), then each block
 * becomes a node.
 *
 * <p><b>Blocks</b>: a heading or a paragraph becomes a {@link TextFlow} ({@code md-heading}, {@code md-paragraph}); a
 * list a {@link VBox} of items ({@code md-list}, {@code md-item}), each with its marker (a bullet, or its number and
 * delimiter) beside its blocks; a quote its blocks, indented ({@code md-quote}); a block of code, or of HTML, its text
 * as typed ({@code md-code-block}); a thematic break a {@link Separator}; a link reference definition nothing.</p>
 *
 * <p><b>Inline elements</b>: a text, a line break, an inline code ({@code md-code}) or inline HTML become a
 * {@link Text} ({@code md-text}); an emphasis ({@code md-emphasis}) or a strong emphasis ({@code md-strong}) add their
 * style class to the texts they contain; a link or an image is replaced by its text: nothing is opened nor loaded,
 * the application stays offline.</p>
 *
 * <p><b>Security</b>: the text may come from the network; it is shown, never interpreted (HTML included), and the
 * nesting of its blocks and of its inline elements is capped at {@link #MAX_NESTING}, beyond which the Markdown stays
 * text, so that a text cannot exhaust the stack.</p>
 *
 * <p>Stateless; the nodes are created on the calling thread and must be shown on the JavaFX Application Thread.</p>
 */
final class Markdown {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The deepest nesting of blocks and of inline elements read; deeper, the Markdown stays text. */
  static final int MAX_NESTING = 16;
  /** The marker of an item of a bullet list. */
  private static final String BULLET = "•";
  /** The number of the first item of an ordered list that does not give it. */
  private static final int FIRST_NUMBER = 1;
  /** The delimiter after the number of an item of an ordered list that does not give it. */
  private static final String DELIMITER = ".";
  /** The parser of CommonMark, thread-safe. */
  private static final Parser PARSER = Parser.builder().maxOpenBlockParsers(MAX_NESTING)
                                             .maxInlineNesting(MAX_NESTING).build();

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Not instantiable.
   */
  private Markdown() {
  }

  // *******************************************************************************************************************
  // Rendering
  // *******************************************************************************************************************

  /**
   * Shows a text written in Markdown.
   *
   * @param markdown the text, in Markdown.
   *
   * @return its blocks, one under the other ({@code markdown}).
   */
  static VBox render(final String markdown) {
    final VBox box = blocks(PARSER.parse(markdown));
    box.getStyleClass().add("markdown");
    return box;
  }

  /**
   * Shows the blocks of a node of the document (the document, an item of a list, a quote), one under the other.
   *
   * @param parent the node of the document.
   *
   * @return its blocks ({@code md-blocks}).
   */
  private static VBox blocks(final org.commonmark.node.Node parent) {
    final VBox box = new VBox();
    box.getStyleClass().add("md-blocks");
    for (final org.commonmark.node.Node child : children(parent)) {
      final Node block = block(child);
      if (block != null) {
        box.getChildren().add(block);
      }
    }
    return box;
  }

  /**
   * Shows a block according to its kind.
   *
   * @param node the block.
   *
   * @return its node, or {@code null} for a block shown by nothing (a link reference definition).
   */
  private static Node block(final org.commonmark.node.Node node) {
    return switch (node) {
      case Heading heading -> flow(heading, "md-heading");
      case Paragraph paragraph -> flow(paragraph, "md-paragraph");
      case ListBlock list -> list(list);
      case BlockQuote quote -> styled(blocks(quote), "md-quote");
      case FencedCodeBlock code -> code(code.getLiteral());
      case IndentedCodeBlock code -> code(code.getLiteral());
      case HtmlBlock html -> code(html.getLiteral());
      case ThematicBreak _ -> new Separator();
      default -> null;
    };
  }

  /**
   * Shows a list: each item with its marker beside its blocks.
   *
   * @param list the list, bullet or ordered.
   *
   * @return the items, one under the other ({@code md-list}).
   */
  private static VBox list(final ListBlock list) {
    final VBox box = new VBox();
    box.getStyleClass().add("md-list");
    final List<org.commonmark.node.Node> items = children(list);
    for (int index = 0; index < items.size(); index++) {
      final Label marker = new Label(marker(list, index));
      marker.getStyleClass().add("md-marker");
      marker.setMinWidth(Region.USE_PREF_SIZE);
      final VBox content = blocks(items.get(index));
      HBox.setHgrow(content, Priority.ALWAYS);
      box.getChildren().add(styled(new HBox(marker, content), "md-item"));
    }
    return box;
  }

  /**
   * Returns the marker of an item of a list: a bullet, or the number of the item followed by the delimiter of the
   * list.
   *
   * @param list the list.
   * @param index the index of the item in the list, from 0.
   *
   * @return the marker.
   */
  private static String marker(final ListBlock list, final int index) {
    if (list instanceof final OrderedList ordered) {
      return (Objects.requireNonNullElse(ordered.getMarkerStartNumber(), FIRST_NUMBER) + index)
             + Objects.requireNonNullElse(ordered.getMarkerDelimiter(), DELIMITER);
    }
    return BULLET;
  }

  /**
   * Shows a block of code, or of HTML, as typed, without its last line break.
   *
   * @param literal the text of the block.
   *
   * @return the text ({@code md-code-block}).
   */
  private static TextFlow code(final String literal) {
    final TextFlow flow = new TextFlow(text(literal.stripTrailing(), List.of("md-code")));
    flow.getStyleClass().add("md-code-block");
    return flow;
  }

  /**
   * Shows the inline elements of a block (a heading, a paragraph) as a flow of texts.
   *
   * @param block the block.
   * @param style the style class of the flow.
   *
   * @return the flow of its texts.
   */
  private static TextFlow flow(final org.commonmark.node.Node block, final String style) {
    final TextFlow flow = new TextFlow();
    inlines(block, List.of(), flow.getChildren());
    flow.getStyleClass().add(style);
    return flow;
  }

  /**
   * Adds the texts of the inline elements of a node.
   *
   * @param parent the node.
   * @param styles the style classes of the elements that contain the node (emphasis).
   * @param into the texts, to which the texts of the node are added.
   */
  private static void inlines(final org.commonmark.node.Node parent, final List<String> styles,
                              final List<Node> into) {
    for (final org.commonmark.node.Node child : children(parent)) {
      inline(child, styles, into);
    }
  }

  /**
   * Adds the texts of an inline element according to its kind: its text, or the texts of its content with the style
   * class of an emphasis; a link or an image gives the texts of its content.
   *
   * @param node the inline element.
   * @param styles the style classes of the elements that contain it.
   * @param into the texts, to which its texts are added.
   */
  private static void inline(final org.commonmark.node.Node node, final List<String> styles, final List<Node> into) {
    switch (node) {
      case org.commonmark.node.Text text -> into.add(text(text.getLiteral(), styles));
      case Code code -> into.add(text(code.getLiteral(), with(styles, "md-code")));
      case HtmlInline html -> into.add(text(html.getLiteral(), styles));
      case SoftLineBreak _ -> into.add(text(" ", styles));
      case HardLineBreak _ -> into.add(text("\n", styles));
      case Emphasis emphasis -> inlines(emphasis, with(styles, "md-emphasis"), into);
      case StrongEmphasis strong -> inlines(strong, with(styles, "md-strong"), into);
      default -> inlines(node, styles, into);
    }
  }

  /**
   * Creates a text with its style classes.
   *
   * @param content the content of the text.
   * @param styles the style classes added to {@code md-text}.
   *
   * @return the text.
   */
  private static Text text(final String content, final List<String> styles) {
    final Text text = new Text(content);
    text.getStyleClass().add("md-text");
    text.getStyleClass().addAll(styles);
    return text;
  }

  /**
   * Returns style classes with one more.
   *
   * @param styles the style classes.
   * @param style the style class added.
   *
   * @return a new list: the style classes then the one added.
   */
  private static List<String> with(final List<String> styles, final String style) {
    final List<String> all = new ArrayList<>(styles);
    all.add(style);
    return List.copyOf(all);
  }

  /**
   * Adds a style class to a node.
   *
   * @param <N> the type of the node.
   * @param node the node.
   * @param style the style class.
   *
   * @return the node.
   */
  private static <N extends Node> N styled(final N node, final String style) {
    node.getStyleClass().add(style);
    return node;
  }

  /**
   * Returns the children of a node of the document, in their order.
   *
   * @param parent the node.
   *
   * @return its children.
   */
  private static List<org.commonmark.node.Node> children(final org.commonmark.node.Node parent) {
    final List<org.commonmark.node.Node> children = new ArrayList<>();
    org.commonmark.node.Node child = parent.getFirstChild();
    while (child != null) {
      children.add(child);
      child = child.getNext();
    }
    return children;
  }

}
