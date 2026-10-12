package ch.varani.jass.persistence;

/**
 * The names of the format of the directory file ({@code directory.jassdir}), shared by {@link DirectoryWriter} and
 * {@link DirectoryReader}: the XML namespace, the format version, the elements and the attributes.
 *
 * <p>The structure of the document is defined by the XML schema {@code directory.xsd}.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class DirectoryXml {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The XML namespace of the elements of the directory. */
  static final String NAMESPACE = "urn:ch:varani:jass:directory";
  /** The current version of the format, written in {@link #FORMAT_VERSION_ATTRIBUTE}. */
  static final String FORMAT_VERSION = "1";

  /** The root element. */
  static final String DIRECTORY = "directory";
  /** The attribute of the root element giving the version of the format. */
  static final String FORMAT_VERSION_ATTRIBUTE = "formatVersion";
  /** The element of a person. */
  static final String PERSON = "person";
  /** The attribute of the identifier of a person. */
  static final String ID = "id";
  /** The attribute of the first name of a person. */
  static final String FIRST_NAME = "firstName";
  /** The attribute of the last name of a person. */
  static final String LAST_NAME = "lastName";
  /** The optional attribute of the e-mail address of a person. */
  static final String EMAIL = "email";
  /** The optional attribute of the phone number of a person, in the format E.164. */
  static final String PHONE = "phone";
  /** The optional attribute of the notes of a person. */
  static final String NOTES = "notes";
  /** The attribute of the contact status of a person. */
  static final String STATUS = "status";
  /** The optional attribute of the date of the last change of the contact status. */
  static final String STATUS_DATE = "statusDate";
  /** The element of a participation of a person. */
  static final String PARTICIPATION = "participation";
  /** The attribute of the identifier of the edition of a participation. */
  static final String EDITION = "edition";
  /** The attribute of the name of the edition of a participation. */
  static final String NAME = "name";
  /** The attribute of the date of the edition of a participation. */
  static final String DATE = "date";
  /** The optional attribute of the identifier of the team mate of a participation. */
  static final String TEAM_MATE = "teamMate";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DirectoryXml() {
  }

}
