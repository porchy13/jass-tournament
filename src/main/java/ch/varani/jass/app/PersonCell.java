package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Person;
import javafx.scene.control.ListCell;

/**
 * A person of the directory in a list: its full name, followed by its contact status when it is not contactable.
 * The contact details are never shown in the lists.
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class PersonCell extends ListCell<Person> {

  /** The texts of the user interface. */
  private final Messages messages;

  /**
   * Creates a line of a list of persons.
   *
   * @param messages the texts of the user interface.
   */
  PersonCell(final Messages messages) {
    this.messages = messages;
  }

  /**
   * Shows a person, or nothing for an empty line.
   *
   * @param person the person.
   * @param empty whether the line is empty.
   */
  @Override
  protected void updateItem(final Person person, final boolean empty) {
    super.updateItem(person, empty);
    if (empty) {
      setText(null);
      return;
    }
    setText(person.status() == ContactStatus.CONTACTABLE ? person.fullName()
            : person.fullName() + " (" + messages.get("status." + person.status().name()) + ")");
  }

}
