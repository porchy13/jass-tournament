package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Ledger} and {@link LedgerEntry}: fee, entries, categories and supporting documents.
 */
final class LedgerTest {

  /** The date of the fictitious entries. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** A fictitious document. */
  private static final Attachment INVOICE = new Attachment(AttachmentTest.DIGEST, AttachmentType.PDF, "invoice.pdf",
                                                           1L);
  /** An amount of 1 franc, created once: the assertions on the entries do not depend on {@link Money#of(String)}. */
  private static final Money ONE = Money.of("1");
  /** Another fictitious document. */
  private static final Attachment RECEIPT = new Attachment("f".repeat(64), AttachmentType.JPEG, "receipt.jpg", 2L);

  /**
   * Creates an expense of a category.
   *
   * @param category the category.
   * @param amount the amount.
   *
   * @return the entry.
   */
  private static LedgerEntry expense(final String category, final String amount) {
    return LedgerEntry.create(DATE, EntryType.EXPENSE, category, "", Money.of(amount));
  }

  /**
   * An entry has stripped texts, a mandatory category, a positive amount, and each document once.
   */
  @Test
  @DisplayName("An entry has a category, a positive amount and each document once")
  void entryTest() {
    final LedgerEntry entry = LedgerEntry.create(DATE, EntryType.INCOME, " Sponsoring ", " Garage ", Money.of("50"));
    final UUID id = entry.id();
    final List<Attachment> twice = List.of(INVOICE, INVOICE);
    final List<Attachment> none = List.of();
    assertAll(
        () -> assertEquals("Sponsoring", entry.category()),
        () -> assertEquals("Garage", entry.description()),
        () -> assertNotEquals(entry.id(), LedgerEntry.create(DATE, EntryType.INCOME, "S", "", ONE).id()),
        () -> assertEquals(ValidationError.BLANK_CATEGORY,
                           assertThrows(DomainException.class, () -> expense(" ", "1")).getError()),
        () -> assertEquals(ValidationError.INVALID_AMOUNT,
                           assertThrows(DomainException.class, () -> expense("Prix", "0")).getError()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class, () -> LedgerEntry.create(
                               DATE, EntryType.INCOME, "S", "a\u0007", ONE)).getError()),
        () -> assertEquals(ValidationError.INVALID_ATTACHMENT,
                           assertThrows(DomainException.class, () -> new LedgerEntry(
                               id, DATE, EntryType.INCOME, "S", "", ONE, twice))
                               .getError()),
        () -> assertThrows(NullPointerException.class, () -> new LedgerEntry(
            null, DATE, EntryType.INCOME, "S", "", ONE, none)),
        () -> assertThrows(NullPointerException.class, () -> LedgerEntry.create(
            null, EntryType.INCOME, "S", "", ONE)),
        () -> assertThrows(NullPointerException.class, () -> LedgerEntry.create(DATE, null, "S", "", ONE)),
        () -> assertThrows(NullPointerException.class, () -> LedgerEntry.create(
            DATE, EntryType.INCOME, null, "", ONE)),
        () -> assertThrows(NullPointerException.class, () -> LedgerEntry.create(
            DATE, EntryType.INCOME, "S", null, ONE)),
        () -> assertThrows(NullPointerException.class, () -> LedgerEntry.create(
            DATE, EntryType.INCOME, "S", "", null)));
  }

  /**
   * A document is attached once and can be removed.
   */
  @Test
  @DisplayName("A document is attached once and can be removed")
  void attachmentsTest() {
    final LedgerEntry entry = expense("Prix", "120").withAttachment(INVOICE);
    assertAll(
        () -> assertEquals(List.of(INVOICE), entry.attachments()),
        () -> assertSame(entry, entry.withAttachment(INVOICE)),
        () -> assertEquals(List.of(INVOICE, RECEIPT), entry.withAttachment(RECEIPT).attachments()),
        () -> assertEquals(List.of(), entry.withoutAttachment(INVOICE.sha256()).attachments()),
        () -> assertEquals(List.of(INVOICE), entry.withoutAttachment(RECEIPT.sha256()).attachments()));
  }

  /**
   * The entries are added, replaced and removed by identifier; the fee can be changed.
   */
  @Test
  @DisplayName("Entries are added, replaced and removed; the fee can be changed")
  void entriesTest() {
    final LedgerEntry prizes = expense("Prix", "120");
    final Ledger ledger = Ledger.EMPTY.add(prizes).withFee(Money.of("30"));
    final LedgerEntry changed = new LedgerEntry(prizes.id(), DATE, EntryType.EXPENSE, "Prix", "Vin", Money.of("130"),
                                                List.of());
    final UUID unknown = UUID.randomUUID();
    final LedgerEntry other = expense("Salle", "50");
    final LedgerEntry absent = expense("Prix", "1");
    assertAll(
        () -> assertTrue(Ledger.EMPTY.isEmpty()),
        () -> assertFalse(Ledger.EMPTY.withFee(Money.of("30")).isEmpty()),
        () -> assertFalse(Ledger.EMPTY.add(prizes).isEmpty()),
        () -> assertEquals(Money.of("30"), ledger.fee()),
        () -> assertEquals(prizes, ledger.get(prizes.id())),
        () -> assertEquals(changed, ledger.replace(changed).get(prizes.id())),
        () -> assertEquals(List.of(), ledger.remove(prizes.id()).entries()),
        () -> assertEquals(List.of(other), ledger.add(other).remove(prizes.id()).entries()),
        () -> assertEquals(List.of(changed, other), ledger.add(other).replace(changed).entries()),
        () -> assertEquals(ValidationError.UNKNOWN_ENTRY,
                           assertThrows(DomainException.class, () -> ledger.get(unknown)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_ENTRY,
                           assertThrows(DomainException.class, () -> ledger.remove(unknown)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_ENTRY,
                           assertThrows(DomainException.class, () -> ledger.replace(absent)).getError()),
        () -> assertEquals(ValidationError.DUPLICATE_ENTRY,
                           assertThrows(DomainException.class, () -> ledger.add(prizes)).getError()),
        () -> assertThrows(NullPointerException.class, () -> new Ledger(null, List.of())));
  }

  /**
   * The categories are proposed once each, accents, case and spaces ignored, sorted; the documents are listed once
   * each, sorted by digest.
   */
  @Test
  @DisplayName("The categories and the documents are listed once each")
  void categoriesTest() {
    final Ledger ledger = Ledger.EMPTY.add(expense("Prix", "1").withAttachment(RECEIPT))
                                      .add(expense("Boissons", "2").withAttachment(INVOICE).withAttachment(RECEIPT))
                                      .add(expense("prix", "3"))
                                      .add(expense("Électricité", "4"));
    assertAll(
        () -> assertEquals(List.of("Boissons", "Électricité", "Prix"), ledger.categories()),
        () -> assertEquals(List.of(INVOICE, RECEIPT), ledger.attachments()));
  }

}
