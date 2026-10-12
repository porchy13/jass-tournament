package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Csv}: reading of CSV files and pasted rows, writing with quotes and protection against formula
 * injection.
 */
final class CsvTest {

  /**
   * Cells are separated by the separator, rows by any line break; blank rows are left out; a byte order mark is
   * ignored.
   */
  @Test
  @DisplayName("Cells and rows are split, blank rows left out, the byte order mark ignored")
  void parseTest() {
    assertAll(
        () -> assertEquals(List.of(List.of("Anne", "Fontaine"), List.of("Zoé", "", "x")),
                           Csv.parse("﻿Anne;Fontaine\r\n;\n  ;  \rZoé;;x\n\n", Csv.SEMICOLON)),
        () -> assertEquals(List.of(List.of("Anne", "Fontaine;SA")),
                           Csv.parse("Anne\tFontaine;SA", Csv.TAB)),
        () -> assertEquals(List.of(), Csv.parse("", Csv.COMMA)),
        () -> assertEquals(List.of(List.of("a", "")), Csv.parse("a,", Csv.COMMA)),
        () -> assertEquals(List.of(List.of("a")), Csv.parse("a\r", Csv.COMMA)));
  }

  /**
   * A quoted cell may hold the separator, line breaks and doubled quotes; a quote inside an unquoted cell is kept.
   */
  @Test
  @DisplayName("A quoted cell may hold separators, line breaks and quotes")
  void quotesTest() {
    assertEquals(List.of(List.of("Fontaine, Anne", "line 1\r\nline 2", "say \"hi\"", "a\"b", "")),
                 Csv.parse("\"Fontaine, Anne\",\"line 1\r\nline 2\",\"say \"\"hi\"\"\",a\"b,\"\"", Csv.COMMA));
  }

  /**
   * A quote that is not closed makes the table invalid.
   */
  @Test
  @DisplayName("A quote that is not closed is rejected")
  void malformedTest() {
    assertEquals(ValidationError.MALFORMED_TABLE,
                 assertThrows(DomainException.class, () -> Csv.parse("a;\"b\nc", Csv.SEMICOLON)).getError());
  }

  /**
   * A table has at most {@link Csv#MAX_ROWS} rows.
   */
  @Test
  @DisplayName("A table has a maximum number of rows")
  void tooManyRowsTest() {
    final String allowed = String.join("\n", Collections.nCopies(Csv.MAX_ROWS, "a"));
    assertAll(
        () -> assertEquals(Csv.MAX_ROWS, Csv.parse(allowed, Csv.SEMICOLON).size()),
        () -> assertEquals(ValidationError.TOO_MANY_ROWS,
                           assertThrows(DomainException.class, () -> Csv.parse(allowed + "\nb", Csv.SEMICOLON))
                               .getError()),
        () -> assertEquals(ValidationError.TOO_MANY_ROWS,
                           assertThrows(DomainException.class, () -> Csv.parse(allowed + "\nb\n", Csv.SEMICOLON))
                               .getError()));
  }

  /**
   * The cells holding a separator, a quote or a line break are quoted; the rows end with CR LF.
   */
  @Test
  @DisplayName("The cells are quoted when needed, the rows end with CR LF")
  void formatTest() {
    assertEquals("Anne;\"Fontaine; SA\";\"say \"\"hi\"\"\"\r\n\"a\nb\";\"c\rd\";\r\n",
                 Csv.format(List.of(List.of("Anne", "Fontaine; SA", "say \"hi\""), List.of("a\nb", "c\rd", "")),
                            Csv.SEMICOLON));
  }

  /**
   * Formula injection: a cell starting with a formula character is prefixed with a single quote.
   */
  @Test
  @DisplayName("Formula injection: a cell starting with = + - @ is prefixed with a quote")
  void formulaInjectionTest() {
    assertEquals("\"'=HYPERLINK(\"\"x\"\")\";'+41790000001;'-1;'@SUM(A1);'\tx;\"'\rx\";a=b\r\n",
                 Csv.format(List.of(List.of("=HYPERLINK(\"x\")", "+41790000001", "-1", "@SUM(A1)", "\tx", "\rx",
                                            "a=b")), Csv.SEMICOLON));
  }

  /**
   * A written table is read back.
   */
  @Test
  @DisplayName("A written table is read back")
  void roundTripTest() {
    final List<List<String>> table = List.of(List.of("Zoé", "d'Été \"♥\"", "a;b"), List.of("x", "line\nbreak", "y"));
    assertEquals(table, Csv.parse(Csv.format(table, Csv.SEMICOLON), Csv.SEMICOLON));
  }

}
