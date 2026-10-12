package ch.varani.jass.persistence;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * A forward cursor over the elements of an XML document of the application ({@code edition.xml}, or the content of
 * the directory), built on a StAX reader with secure settings, which turns
 * every unexpected event into a {@link FileFormatException}.
 *
 * <p><b>Security</b> ({@code AGENTS.md} §5): the reader is the one of the JDK, without DTD support and without
 * external entities, so that no file or network resource is ever loaded (XXE). A document type declaration is never
 * skipped: it is an unexpected event, rejected as {@link FormatError#INVALID_XML}. The content must be encoded in
 * UTF-8; a document declaring another encoding is rejected.</p>
 *
 * <p>The cursor moves from element to element ({@link XMLStreamReader#nextTag()}), skipping white space, comments and
 * processing instructions. Every element must be in the namespace of the document, given at creation.</p>
 *
 * <p>Not thread-safe: an instance reads one document, from one thread.</p>
 */
final class XmlCursor {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The factory of the StAX readers, of the JDK, without DTD support nor external entities. */
  private static final XMLInputFactory FACTORY = createFactory();

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The StAX reader of the document. */
  private final XMLStreamReader reader;
  /** The XML namespace of every element of the document. */
  private final String namespace;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a cursor at the start of a document.
   *
   * @param reader the StAX reader of the document.
   * @param namespace the XML namespace of every element of the document.
   */
  private XmlCursor(final XMLStreamReader reader, final String namespace) {
    this.reader = reader;
    this.namespace = namespace;
  }

  /**
   * Creates a cursor at the start of an edition document ({@code edition.xml}) encoded in UTF-8.
   *
   * @param xml the bytes of the document.
   *
   * @return the cursor, before the root element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the XML declaration is malformed or
   *                             declares another encoding than UTF-8.
   */
  static XmlCursor of(final byte[] xml) throws FileFormatException {
    return of(xml, EditionXml.NAMESPACE);
  }

  /**
   * Creates a cursor at the start of a document encoded in UTF-8.
   *
   * @param xml the bytes of the document.
   * @param namespace the XML namespace of every element of the document.
   *
   * @return the cursor, before the root element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the XML declaration is malformed or
   *                             declares another encoding than UTF-8.
   */
  static XmlCursor of(final byte[] xml, final String namespace) throws FileFormatException {
    final XMLStreamReader reader;
    try {
      reader = FACTORY.createXMLStreamReader(new ByteArrayInputStream(xml), XmlOutput.ENCODING);
    } catch (XMLStreamException _) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
    final String declared = reader.getCharacterEncodingScheme();
    if (declared != null && !XmlOutput.ENCODING.equalsIgnoreCase(declared)) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
    return new XmlCursor(reader, namespace);
  }

  /**
   * Creates the factory of the StAX readers: the implementation of the JDK, namespace aware, without DTD support and
   * without external entities.
   *
   * @return the factory.
   */
  private static XMLInputFactory createFactory() {
    final XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    return factory;
  }

  // *******************************************************************************************************************
  // Navigation
  // *******************************************************************************************************************

  /**
   * Moves to the start of the next element, which must have the given name.
   *
   * @param name the local name of the expected element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the next event is not the start of this
   *                             element.
   */
  void open(final String name) throws FileFormatException {
    next();
    requireStart(name);
  }

  /**
   * Moves to the next event, which must be the start of an element with the given name or the end of the current
   * element; used to read a list of elements.
   *
   * @param name the local name of the element of the list.
   *
   * @return {@code true} at the start of an element of the list, {@code false} at the end of the current element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the next event is the start of another
   *                             element.
   */
  boolean openOrClose(final String name) throws FileFormatException {
    next();
    if (reader.isEndElement()) {
      return false;
    }
    requireStart(name);
    return true;
  }

  /**
   * Moves to the next event, which must be the start of an element (whatever its name, in the namespace of the
   * document) or the end of the current element; used where the schema allows several elements or optional ones.
   *
   * @return the local name of the element started, or {@code null} at the end of the current element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the element is in another namespace.
   */
  String nextSection() throws FileFormatException {
    next();
    if (reader.isEndElement()) {
      return null;
    }
    final String name = reader.getLocalName();
    requireStart(name);
    return name;
  }

  /**
   * Moves to the end of the current element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the next event is the start of an element.
   */
  void close() throws FileFormatException {
    next();
    if (!reader.isEndElement()) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

  /**
   * Reads an element that contains only text, and moves to its end.
   *
   * @param name the local name of the expected element.
   *
   * @return the text of the element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the next event is not the start of this
   *                             element, or if the element contains another element.
   */
  String text(final String name) throws FileFormatException {
    open(name);
    try {
      return reader.getElementText();
    } catch (XMLStreamException _) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

  /**
   * Reads an optional element that contains only text, the last one of its parent: moves to its end, or to the end
   * of the parent when it is absent.
   *
   * @param name the local name of the optional element.
   *
   * @return the text of the element, or {@code null} at the end of the parent.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the next event is the start of another
   *                             element, or if the element contains another element.
   */
  String optionalText(final String name) throws FileFormatException {
    if (!openOrClose(name)) {
      return null;
    }
    try {
      return reader.getElementText();
    } catch (XMLStreamException _) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

  /**
   * Returns an attribute of the current element, which must be present.
   *
   * @param name the local name of the attribute, without namespace.
   *
   * @return the value of the attribute.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the attribute is missing.
   */
  String attribute(final String name) throws FileFormatException {
    final String value = reader.getAttributeValue(null, name);
    if (value == null) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
    return value;
  }

  /**
   * Returns an attribute of the current element, which may be absent.
   *
   * @param name the local name of the attribute, without namespace.
   *
   * @return the value of the attribute, or {@code null} if it is absent.
   */
  String optionalAttribute(final String name) {
    return reader.getAttributeValue(null, name);
  }

  /**
   * Moves to the next start or end of an element, skipping white space, comments and processing instructions.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the document is not well-formed, or if the
   *                             next event is another one (text, document type declaration, end of document…).
   */
  private void next() throws FileFormatException {
    try {
      reader.nextTag();
    } catch (XMLStreamException _) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

  /**
   * Checks that the current event is the start of an element with the given name, in the namespace of the document.
   *
   * @param name the local name of the expected element.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if it is the end of an element, or the start
   *                             of an element with another name or namespace.
   */
  private void requireStart(final String name) throws FileFormatException {
    if (!reader.isStartElement() || !name.equals(reader.getLocalName())
        || !namespace.equals(reader.getNamespaceURI())) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

}
