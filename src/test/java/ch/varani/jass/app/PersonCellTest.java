package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Person;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link PersonCell}: the full name, the status of a person who is not contactable, nothing for an empty
 * line; in French.
 */
@ExtendWith(ApplicationExtension.class)
final class PersonCellTest {

  /**
   * A line shows the full name, followed by the status when the person is not contactable, and nothing when empty.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("A person is shown with its status when it is not contactable")
  void updateItemTest(final FxRobot robot) {
    final List<String> texts = new ArrayList<>();
    robot.interact(() -> {
      final PersonCell cell = new PersonCell(new Messages(Languages.FRENCH));
      final Person anne = Person.create("Anne", "Fontaine");
      cell.updateItem(anne, false);
      texts.add(cell.getText());
      cell.updateItem(anne.withStatus(ContactStatus.DO_NOT_CONTACT, UiSupport.TODAY), false);
      texts.add(cell.getText());
      cell.updateItem(null, true);
      texts.add(cell.getText());
    });
    assertEquals(List.of("Anne Fontaine", "Anne Fontaine (Ne pas contacter)", "null"),
                 texts.stream().map(String::valueOf).toList());
  }

}
