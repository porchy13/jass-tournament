package ch.varani.jass.app;

import java.net.URI;
import java.util.Locale;

/**
 * The open-source libraries shipped with the application, listed by the window about the application
 * ({@link LibrariesController}): the runtime libraries of {@code pom.xml} (a library and the ones it brings without
 * being used directly by the application, e.g. FontBox for PDFBox, make one entry when they come from the same
 * project), the font of the documents and the Java runtime of the installers; the libraries of the tests and the
 * plugins of the build are not shipped, so not listed.
 *
 * <p>Each library has its name and its licence, as an SPDX expression, which are not translated, the address of its
 * site, opened in the browser only when the user clicks on its name, and the key of the text of its role in the
 * application ({@link #roleKey()}). A new runtime dependency of {@code pom.xml} gets its constant here.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
enum Library {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The solver of the schedule. */
  OR_TOOLS("Google OR-Tools", Licences.APACHE, "https://developers.google.com/optimization"),
  /** The serialization of the models of OR-Tools. */
  PROTOBUF("Protocol Buffers", "BSD-3-Clause", "https://protobuf.dev"),
  /** The native libraries of OR-Tools, with JNA Platform. */
  JNA("Java Native Access", "Apache-2.0 OR LGPL-2.1-or-later", "https://github.com/java-native-access/jna"),
  /** The user interface. */
  JAVAFX("OpenJFX", Licences.GPL_CLASSPATH, "https://openjfx.io"),
  /** The PDF documents, with FontBox and PDFBox-IO. */
  PDFBOX("Apache PDFBox", Licences.APACHE, "https://pdfbox.apache.org"),
  /** The log of PDFBox. */
  COMMONS_LOGGING("Apache Commons Logging", Licences.APACHE, "https://commons.apache.org/proper/commons-logging/"),
  /** The phone numbers of the directory. */
  LIBPHONENUMBER("libphonenumber", Licences.APACHE, "https://github.com/google/libphonenumber"),
  /** The notes of a release, in Markdown, shown by the offer of an update. */
  COMMONMARK("commonmark-java", "BSD-2-Clause", "https://github.com/commonmark/commonmark-java"),
  /** The font of the PDF documents. */
  DEJAVU("DejaVu Sans", "Bitstream-Vera", "https://dejavu-fonts.github.io"),
  /** The Java runtime of the installers, made by {@code jlink}. */
  OPENJDK("OpenJDK", Licences.GPL_CLASSPATH, "https://openjdk.org");

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The licences shared by several libraries, as SPDX expressions; a holder class, since the constants of an enum
   * cannot refer to its own static fields.
   *
   * <p>Utility class without state, safe for concurrent use.</p>
   */
  private static final class Licences {

    /** The Apache License 2.0. */
    static final String APACHE = "Apache-2.0";
    /** The GNU General Public License 2.0 with the Classpath Exception, of OpenJFX and OpenJDK. */
    static final String GPL_CLASSPATH = "GPL-2.0-only WITH Classpath-exception-2.0";

    /**
     * Prevents the instantiation of this holder class.
     */
    private Licences() {
    }

  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The name of the library. */
  private final String title;
  /** The licence of the library, as an SPDX expression. */
  private final String licence;
  /** The site of the library. */
  private final URI site;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a library.
   *
   * @param title the name of the library.
   * @param licence its licence, as an SPDX expression.
   * @param site the address of its site.
   */
  Library(final String title, final String licence, final String site) {
    this.title = title;
    this.licence = licence;
    this.site = URI.create(site);
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the name of the library.
   *
   * @return its name, not translated.
   */
  String title() {
    return title;
  }

  /**
   * Returns the licence of the library.
   *
   * @return its SPDX expression, not translated.
   */
  String licence() {
    return licence;
  }

  /**
   * Returns the site of the library.
   *
   * @return its address.
   */
  URI site() {
    return site;
  }

  /**
   * Returns the key of the text of the role of the library in the application.
   *
   * @return {@code libraries.role.} followed by the name of the constant in lower case.
   */
  String roleKey() {
    return "libraries.role." + name().toLowerCase(Locale.ROOT);
  }

}
