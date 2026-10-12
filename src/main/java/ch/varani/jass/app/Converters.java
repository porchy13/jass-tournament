package ch.varani.jass.app;

import javafx.util.StringConverter;

import java.util.function.Function;

/**
 * The converters of the lists of choices of the screens: they show the values with a text, and never read texts
 * back (the lists are not editable).
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class Converters {

  /**
   * Prevents the instantiation of this utility class.
   */
  private Converters() {
  }

  /**
   * Creates a converter showing values with a text.
   *
   * @param <T> the type of the values.
   * @param text gives the text of a value.
   *
   * @return the converter.
   */
  static <T> StringConverter<T> showing(final Function<T, String> text) {
    return new StringConverter<>() {

      /**
       * Returns the text of a value.
       *
       * @param value the value.
       *
       * @return its text.
       */
      @Override
      public String toString(final T value) {
        return text.apply(value);
      }

      /**
       * Reads a value; the lists of choices never read texts back.
       *
       * @param string the text.
       *
       * @return always {@code null}.
       */
      @Override
      public T fromString(final String string) {
        return null;
      }

    };
  }

}
