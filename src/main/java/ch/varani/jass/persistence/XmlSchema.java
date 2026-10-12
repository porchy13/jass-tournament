package ch.varani.jass.persistence;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

/**
 * The XML schemas of the files of the application ({@link #EDITION}: {@code edition.xsd}; {@link #DIRECTORY}:
 * {@code directory.xsd}, resources of this package), and the validation of a document against them.
 *
 * <p><b>Security</b> ({@code AGENTS.md} §5): the schema factory and the validators run with the secure processing
 * feature and forbid any access to an external DTD or schema. The documents are validated after
 * {@link XmlCursor} has rejected any document type declaration.</p>
 *
 * <p>The compiled schemas are immutable and shared; each validation uses its own validator. Safe for concurrent
 * use.</p>
 */
final class XmlSchema {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The schema of {@code edition.xml}, the main entry of an edition file. */
  static final XmlSchema EDITION = new XmlSchema(load(XmlSchema.class.getResource("edition.xsd")));
  /** The schema of the directory file. */
  static final XmlSchema DIRECTORY = new XmlSchema(load(XmlSchema.class.getResource("directory.xsd")));

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The compiled schema. */
  private final Schema schema;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the validation of a compiled schema.
   *
   * @param schema the compiled schema.
   */
  private XmlSchema(final Schema schema) {
    this.schema = schema;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Compiles a schema with the secure settings.
   *
   * @param url the location of the schema, a resource of the application.
   *
   * @return the compiled schema.
   *
   * @throws IllegalStateException if the schema cannot be compiled (a programming error: the schema is part of the
   *                               application).
   */
  static Schema load(final URL url) {
    try {
      final SchemaFactory factory = SchemaFactory.newDefaultInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newSchema(url);
    } catch (final SAXException e) {
      throw new IllegalStateException("A schema of the application cannot be compiled.", e);
    }
  }

  /**
   * Validates a document against this schema.
   *
   * @param xml the bytes of the document, without document type declaration.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if the document does not follow the schema.
   * @throws IOException if the document cannot be read (not expected from bytes in memory).
   */
  void validate(final byte[] xml) throws FileFormatException, IOException {
    try {
      final Validator validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
    } catch (SAXException _) {
      throw new FileFormatException(FormatError.INVALID_XML);
    }
  }

}
