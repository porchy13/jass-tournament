package ch.varani.jass.app;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Money;
import ch.varani.jass.persistence.FileFormatException;
import ch.varani.jass.update.UpdateException;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The texts of the user interface ({@code ch/varani/jass/app/messages*.properties}) in the language of the
 * application, the format of its dates, and the translation of the errors into messages for the user.
 *
 * <p><b>Errors</b> ({@link #error(Throwable)}): a value rejected by the domain is explained by the key
 * {@code error.validation.<code>}, a rejected file by {@code error.format.<code>}, any other input or output problem
 * by {@code error.io}; anything else by {@code error.unexpected}. The messages never quote the content of the files,
 * which may hold names.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
final class Messages {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The base name of the bundle of the texts of the user interface. */
  static final String BUNDLE_NAME = "ch.varani.jass.app.messages";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The locale of the application. */
  private final Locale locale;
  /** The texts. */
  private final ResourceBundle bundle;
  /** The format of the dates. */
  private final DateTimeFormatter dates;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Loads the texts of a locale.
   *
   * @param locale the locale of the application ({@link Languages#resolve(Locale)}).
   */
  Messages(final Locale locale) {
    this.locale = locale;
    this.bundle = Languages.bundle(BUNDLE_NAME, locale);
    this.dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the locale of the application.
   *
   * @return the locale.
   */
  Locale locale() {
    return locale;
  }

  /**
   * Returns the bundle of the texts, for the FXML views.
   *
   * @return the bundle.
   */
  ResourceBundle bundle() {
    return bundle;
  }

  /**
   * Returns a text, its arguments inserted.
   *
   * @param key the key of the text.
   * @param arguments the values of the placeholders {@code {0}}, {@code {1}}…
   *
   * @return the text.
   */
  String get(final String key, final Object... arguments) {
    return new MessageFormat(bundle.getString(key), locale).format(arguments);
  }

  /**
   * Formats a date in the format of the locale, e.g. {@code 20 nov. 2026}.
   *
   * @param date the date.
   *
   * @return the formatted date.
   */
  String date(final LocalDate date) {
    return dates.format(date);
  }

  /**
   * Returns the format of the dates of the locale, for the date pickers.
   *
   * @return the formatter.
   */
  DateTimeFormatter dateFormat() {
    return dates;
  }

  /**
   * Formats a whole number in the locale of the application, e.g. {@code 2 512} in French.
   *
   * @param value the number.
   *
   * @return its text.
   */
  String number(final long value) {
    return NumberFormat.getIntegerInstance(locale).format(value);
  }

  /**
   * Formats an amount of money in the locale of the application, with two decimals, e.g. {@code 1 234,50} in French.
   *
   * @param amount the amount, in CHF.
   *
   * @return its text, without the currency.
   */
  String money(final BigDecimal amount) {
    final NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setMinimumFractionDigits(Money.DECIMALS);
    format.setMaximumFractionDigits(Money.DECIMALS);
    return format.format(amount);
  }

  /**
   * Explains an error to the user.
   *
   * @param error the error.
   *
   * @return the localized message.
   */
  String error(final Throwable error) {
    return switch (error) {
      case final DomainException rejected -> get("error.validation." + rejected.getError().name());
      case final FileFormatException rejected -> get("error.format." + rejected.getError().name());
      case final UpdateException failed -> get("error.update." + failed.getError().name());
      case IOException _ -> get("error.io");
      default -> get("error.unexpected");
    };
  }

}
