package ch.varani.jass.persistence;

import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the directory file ({@code directory.jassdir}): an XML document (format version
 * {@link DirectoryXml#FORMAT_VERSION}, schema {@code directory.xsd}) compressed with GZIP.
 *
 * <p>The sequence is: start of the GZIP stream, then the XML document, encoded in UTF-8 and indented: the root element
 * with the format version, then one element per person, in the order of the directory, with its identifier, names,
 * optional e-mail address, phone number (E.164) and notes (written only when present), contact status and its date,
 * and its participations as child elements; then end of the GZIP stream.</p>
 *
 * <p>The writer writes to a stream; writing the file atomically is the job of {@link DirectoryStore}. The contact
 * details are written here and nowhere else ({@code AGENTS.md} §5).</p>
 *
 * <p>Instances are confined to one writing, from one thread; the static methods are safe for concurrent use.</p>
 */
public final class DirectoryWriter {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The depth of the persons. */
  private static final int PERSON = 1;
  /** The depth of the participations of a person. */
  private static final int PARTICIPATION = 2;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The writer of the indented document. */
  private final XmlOutput xml;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a writer of one document.
   *
   * @param xml the writer of the indented document.
   */
  private DirectoryWriter(final XmlOutput xml) {
    this.xml = xml;
  }

  // *******************************************************************************************************************
  // Writing
  // *******************************************************************************************************************

  /**
   * Writes the directory file into a stream. The stream is not closed.
   *
   * @param directory the directory.
   * @param out the stream to which the compressed document is written.
   *
   * @throws IOException if the stream cannot be written.
   */
  public static void write(final Directory directory, final OutputStream out) throws IOException {
    final GZIPOutputStream gzip = new GZIPOutputStream(out);
    writeXml(directory, gzip);
    gzip.finish();
  }

  /**
   * Writes the XML document of the directory, uncompressed, into a stream, which is not closed.
   *
   * @param directory the directory.
   * @param out the stream to which the document is written.
   *
   * @throws IOException if the stream cannot be written.
   */
  static void writeXml(final Directory directory, final OutputStream out) throws IOException {
    try {
      final XmlOutput xml = XmlOutput.start(out, DirectoryXml.NAMESPACE, DirectoryXml.DIRECTORY);
      xml.attribute(DirectoryXml.FORMAT_VERSION_ATTRIBUTE, DirectoryXml.FORMAT_VERSION);
      final DirectoryWriter writer = new DirectoryWriter(xml);
      for (final Person person : directory.persons()) {
        writer.writePerson(person);
      }
      xml.end();
    } catch (final XMLStreamException e) {
      throw new IOException("The directory cannot be written.", e);
    }
  }

  /**
   * Writes a person: its values as attributes, the optional ones only when present, and its participations.
   *
   * @param person the person.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writePerson(final Person person) throws XMLStreamException {
    xml.open(PERSON, DirectoryXml.PERSON);
    xml.attribute(DirectoryXml.ID, person.id().toString());
    xml.attribute(DirectoryXml.FIRST_NAME, person.firstName());
    xml.attribute(DirectoryXml.LAST_NAME, person.lastName());
    optional(DirectoryXml.EMAIL, person.email() == null ? null : person.email().value());
    optional(DirectoryXml.PHONE, person.phone() == null ? null : person.phone().value());
    optional(DirectoryXml.NOTES, person.notes().isEmpty() ? null : person.notes());
    xml.attribute(DirectoryXml.STATUS, person.status().name());
    optional(DirectoryXml.STATUS_DATE, person.statusDate() == null ? null : person.statusDate().toString());
    for (final Participation participation : person.participations()) {
      xml.empty(PARTICIPATION, DirectoryXml.PARTICIPATION);
      xml.attribute(DirectoryXml.EDITION, participation.edition().toString());
      xml.attribute(DirectoryXml.NAME, participation.editionName());
      xml.attribute(DirectoryXml.DATE, participation.editionDate().toString());
      optional(DirectoryXml.TEAM_MATE, participation.teamMate() == null ? null : participation.teamMate().toString());
    }
    xml.close(PERSON);
  }

  /**
   * Writes an optional attribute, only when it has a value.
   *
   * @param name the local name of the attribute.
   * @param value the value, or {@code null} to write nothing.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void optional(final String name, final String value) throws XMLStreamException {
    if (value != null) {
      xml.attribute(name, value);
    }
  }

}
