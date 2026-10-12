/**
 * The Jass Tournament application: confrontation table, scores, ranking and accounting of an edition of the
 * <i>match aux cartes</i>.
 *
 * <p>The packages {@code ch.varani.jass.domain} (domain model), {@code ch.varani.jass.directory} (directory of the
 * persons), {@code ch.varani.jass.solver} (CP-SAT solver), {@code ch.varani.jass.scoring} (totals and ranking),
 * {@code ch.varani.jass.accounting} (financial overview), {@code ch.varani.jass.persistence} (edition and directory
 * files, data folder), {@code ch.varani.jass.report} (PDF documents), {@code ch.varani.jass.service} (use cases of
 * the data folder) and {@code ch.varani.jass.update} (automatic update from the releases on GitHub) hold the
 * business logic, without any dependency on JavaFX; the package {@code ch.varani.jass.app} holds the JavaFX user
 * interface, whose views are FXML files read by {@code javafx.fxml}.</p>
 */
module ch.varani.jass {
  requires java.logging;
  requires java.net.http;
  requires java.xml;
  requires org.apache.fontbox;
  requires org.apache.pdfbox;
  requires org.apache.pdfbox.io;
  requires com.google.i18n.phonenumbers.libphonenumber;
  requires javafx.controls;
  requires javafx.fxml;
  requires ortools.java;

  exports ch.varani.jass.accounting;
  exports ch.varani.jass.directory;
  exports ch.varani.jass.domain;
  exports ch.varani.jass.persistence;
  exports ch.varani.jass.report;
  exports ch.varani.jass.scoring;
  exports ch.varani.jass.service;
  exports ch.varani.jass.solver;
  exports ch.varani.jass.app to javafx.graphics;
  opens ch.varani.jass.app to javafx.fxml;
}
