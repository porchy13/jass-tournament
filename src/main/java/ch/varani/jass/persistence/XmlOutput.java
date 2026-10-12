package ch.varani.jass.persistence;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;

/**
 * Writes an indented XML document of the application ({@code edition.xml}, or the content of the directory), encoded
 * in UTF-8, every element in one namespace, on top of a StAX writer of the JDK.
 *
 * <p>Each element starts on a new line indented by its depth ({@link #INDENTATION} per level, line feeds whatever the
 * platform), so that the files are readable and stable from one save to the next. The StAX writer escapes the special
 * characters of XML; the text values are always valid XML characters, as the domain rejects the other ones.</p>
 *
 * <p>Not thread-safe: an instance writes one document, from one thread.</p>
 */
final class XmlOutput {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The factory of the StAX writers, of the JDK. */
  private static final XMLOutputFactory FACTORY = XMLOutputFactory.newDefaultFactory();
  /** The encoding of the documents of the application, written and read. */
  static final String ENCODING = "UTF-8";
  /** The depth of the root element. */
  private static final int ROOT = 0;
  /** The line break written before each element: always a line feed, whatever the platform. */
  private static final String LINE_BREAK = "\n";
  /** The indentation of one level of elements. */
  private static final String INDENTATION = "  ";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The StAX writer of the document. */
  private final XMLStreamWriter xml;
  /** The namespace of every element. */
  private final String namespace;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the writer of one document.
   *
   * @param xml the StAX writer of the document.
   * @param namespace the namespace of every element.
   */
  private XmlOutput(final XMLStreamWriter xml, final String namespace) {
    this.xml = xml;
    this.namespace = namespace;
  }

  /**
   * Starts a document: writes the XML declaration and opens the root element, with the namespace as default
   * namespace; the attributes of the root element follow.
   *
   * @param out the stream of the document, not closed.
   * @param namespace the namespace of every element.
   * @param root the local name of the root element.
   *
   * @return the writer of the document.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  static XmlOutput start(final OutputStream out, final String namespace, final String root)
      throws XMLStreamException {
    final XMLStreamWriter xml = FACTORY.createXMLStreamWriter(out, ENCODING);
    xml.writeStartDocument(ENCODING, "1.0");
    xml.setDefaultNamespace(namespace);
    final XmlOutput output = new XmlOutput(xml, namespace);
    output.open(ROOT, root);
    xml.writeDefaultNamespace(namespace);
    return output;
  }

  // *******************************************************************************************************************
  // Writing
  // *******************************************************************************************************************

  /**
   * Ends the document: closes the root element and flushes the writer; the stream is not closed.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void end() throws XMLStreamException {
    close(ROOT);
    xml.writeEndDocument();
    xml.close();
  }

  /**
   * Writes an attribute of the element just started.
   *
   * @param name the local name of the attribute, without namespace.
   * @param value the value of the attribute.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void attribute(final String name, final String value) throws XMLStreamException {
    xml.writeAttribute(name, value);
  }

  /**
   * Starts an element on a new, indented line; its attributes and content follow.
   *
   * @param depth the depth of the element.
   * @param name the local name of the element.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void open(final int depth, final String name) throws XMLStreamException {
    indent(depth);
    xml.writeStartElement(namespace, name);
  }

  /**
   * Writes an empty element on a new, indented line; its attributes follow.
   *
   * @param depth the depth of the element.
   * @param name the local name of the element.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void empty(final int depth, final String name) throws XMLStreamException {
    indent(depth);
    xml.writeEmptyElement(namespace, name);
  }

  /**
   * Writes an element containing only text, on a new, indented line.
   *
   * @param depth the depth of the element.
   * @param name the local name of the element.
   * @param value the text of the element.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void text(final int depth, final String name, final String value) throws XMLStreamException {
    open(depth, name);
    xml.writeCharacters(value);
    xml.writeEndElement();
  }

  /**
   * Ends the current element on a new, indented line.
   *
   * @param depth the depth of the element.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  void close(final int depth) throws XMLStreamException {
    indent(depth);
    xml.writeEndElement();
  }

  /**
   * Writes a line break and the indentation of an element.
   *
   * @param depth the depth of the element, 0 for the root.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void indent(final int depth) throws XMLStreamException {
    xml.writeCharacters(LINE_BREAK + INDENTATION.repeat(depth));
  }

}
