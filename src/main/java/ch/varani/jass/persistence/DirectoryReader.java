package ch.varani.jass.persistence;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import ch.varani.jass.domain.DomainException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/**
 * Reads the directory file ({@code directory.jassdir}) written by {@link DirectoryWriter}.
 *
 * <p>Steps:</p>
 * <ol>
 *   <li>decompression: the content must be GZIP-compressed, at most {@link #MAX_DIRECTORY_SIZE} bytes once
 *       decompressed, counted while reading ({@link FormatError#TOO_LARGE}); a file that is not GZIP, damaged or
 *       truncated gives {@link FormatError#INVALID_ARCHIVE};</li>
 *   <li>root element: UTF-8 only, no document type declaration ({@link XmlCursor}), format version
 *       {@link DirectoryXml#FORMAT_VERSION} ({@link FormatError#UNSUPPORTED_VERSION}), before the schema, so that a
 *       newer file is reported as such;</li>
 *   <li>validation against the schema {@code directory.xsd} ({@link XmlSchema#DIRECTORY},
 *       {@link FormatError#INVALID_XML}): identifiers, statuses and dates are then well-formed;</li>
 *   <li>conversion into the directory, which checks the values (names, e-mail addresses, phone numbers, distinct
 *       identifiers, one participation per edition…; {@link FormatError#INVALID_CONTENT}).</li>
 * </ol>
 *
 * <p>The stream is not closed. Utility class without state, safe for concurrent use.</p>
 */
public final class DirectoryReader {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /**
   * The maximum size of the directory once decompressed, 16 MiB: a person takes a few hundred bytes, and the cap
   * protects against decompression bombs.
   */
  public static final int MAX_DIRECTORY_SIZE = 16 * 1024 * 1024;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DirectoryReader() {
  }

  // *******************************************************************************************************************
  // Reading
  // *******************************************************************************************************************

  /**
   * Reads a directory file.
   *
   * @param in the stream of the compressed file, not closed.
   *
   * @return the directory.
   *
   * @throws FileFormatException if the file is not a valid directory file, with the reason.
   * @throws IOException if the stream cannot be read.
   */
  public static Directory read(final InputStream in) throws FileFormatException, IOException {
    final byte[] xml = decompress(in);
    readRoot(XmlCursor.of(xml, DirectoryXml.NAMESPACE));
    XmlSchema.DIRECTORY.validate(xml);
    final XmlCursor cursor = XmlCursor.of(xml, DirectoryXml.NAMESPACE);
    readRoot(cursor);
    try {
      final List<Person> persons = new ArrayList<>();
      while (cursor.openOrClose(DirectoryXml.PERSON)) {
        persons.add(readPerson(cursor));
      }
      return new Directory(persons);
    } catch (DomainException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
  }

  /**
   * Decompresses the content of the file into memory.
   *
   * @param in the stream of the compressed file.
   *
   * @return the bytes of the XML document.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_ARCHIVE} or {@link FormatError#TOO_LARGE}.
   * @throws IOException if the stream cannot be read.
   */
  private static byte[] decompress(final InputStream in) throws FileFormatException, IOException {
    try {
      final byte[] xml = new GZIPInputStream(in).readNBytes(MAX_DIRECTORY_SIZE + 1);
      if (xml.length > MAX_DIRECTORY_SIZE) {
        throw new FileFormatException(FormatError.TOO_LARGE);
      }
      return xml;
    } catch (ZipException | EOFException _) {
      throw new FileFormatException(FormatError.INVALID_ARCHIVE);
    }
  }

  /**
   * Reads the root element and checks the format version.
   *
   * @param cursor the cursor, before the root element.
   *
   * @throws FileFormatException with {@link FormatError#UNSUPPORTED_VERSION} for another format version, or with
   *                             {@link FormatError#INVALID_XML}.
   */
  private static void readRoot(final XmlCursor cursor) throws FileFormatException {
    cursor.open(DirectoryXml.DIRECTORY);
    if (!DirectoryXml.FORMAT_VERSION.equals(cursor.attribute(DirectoryXml.FORMAT_VERSION_ATTRIBUTE))) {
      throw new FileFormatException(FormatError.UNSUPPORTED_VERSION);
    }
  }

  /**
   * Reads a person and its participations, up to the end of its element. The document follows the schema: the
   * identifiers, the status and the dates are well-formed.
   *
   * @param cursor the cursor, at the start of the person.
   *
   * @return the person.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain rejects a value.
   */
  private static Person readPerson(final XmlCursor cursor) throws FileFormatException {
    final UUID id = UUID.fromString(cursor.attribute(DirectoryXml.ID));
    final String firstName = cursor.attribute(DirectoryXml.FIRST_NAME);
    final String lastName = cursor.attribute(DirectoryXml.LAST_NAME);
    final EmailAddress email = optional(cursor, DirectoryXml.EMAIL, EmailAddress::new);
    final PhoneNumber phone = optional(cursor, DirectoryXml.PHONE, PhoneNumber::new);
    final String notes = optional(cursor, DirectoryXml.NOTES, Function.identity());
    final ContactStatus status = ContactStatus.valueOf(cursor.attribute(DirectoryXml.STATUS));
    final LocalDate statusDate = optional(cursor, DirectoryXml.STATUS_DATE, LocalDate::parse);
    final List<Participation> participations = new ArrayList<>();
    while (cursor.openOrClose(DirectoryXml.PARTICIPATION)) {
      participations.add(new Participation(UUID.fromString(cursor.attribute(DirectoryXml.EDITION)),
                                           cursor.attribute(DirectoryXml.NAME),
                                           LocalDate.parse(cursor.attribute(DirectoryXml.DATE)),
                                           optional(cursor, DirectoryXml.TEAM_MATE, UUID::fromString)));
      cursor.close();
    }
    return new Person(id, firstName, lastName, email, phone, notes == null ? "" : notes, status, statusDate,
                      participations);
  }

  /**
   * Reads an optional attribute of the current element.
   *
   * @param <T> the type of the value.
   * @param cursor the cursor, at the start of the element.
   * @param name the local name of the attribute.
   * @param conversion the conversion of the text of the attribute.
   *
   * @return the value, or {@code null} if the attribute is absent.
   *
   * @throws DomainException if the domain rejects the value.
   */
  private static <T> T optional(final XmlCursor cursor, final String name, final Function<String, T> conversion) {
    final String value = cursor.optionalAttribute(name);
    return value == null ? null : conversion.apply(value);
  }

}
