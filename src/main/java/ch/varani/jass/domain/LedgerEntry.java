package ch.varani.jass.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An entry of the accounting of an edition: an income or an expense, with its category, chosen freely by the user
 * (Q7), and its supporting documents.
 *
 * <p>The category and the description are stripped; the category is mandatory, the description may be empty. The
 * amount is positive (the type gives the direction). A document is attached at most once to an entry.</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param id the identifier of the entry, stable over time.
 * @param date the date of the income or expense.
 * @param type income or expense.
 * @param category the category, e.g. "Boissons", not blank.
 * @param description the description, possibly empty.
 * @param amount the amount, greater than zero.
 * @param attachments the supporting documents, each once.
 */
public record LedgerEntry(UUID id, LocalDate date, EntryType type, String category, String description, Money amount,
                          List<Attachment> attachments) {

  /**
   * Creates an entry, checking its values.
   *
   * @param id the identifier of the entry.
   * @param date the date.
   * @param type income or expense.
   * @param category the category.
   * @param description the description, possibly empty.
   * @param amount the amount.
   * @param attachments the supporting documents.
   *
   * @throws NullPointerException if a value is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_CATEGORY} if the category is blank, with
   *                         {@link ValidationError#INVALID_CHARACTER} if a text contains a character that is not
   *                         allowed, with {@link ValidationError#INVALID_AMOUNT} if the amount is zero, or with
   *                         {@link ValidationError#INVALID_ATTACHMENT} if a document is attached twice.
   */
  public LedgerEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(type, "type");
    category = TextChecks.checkCharacters(Objects.requireNonNull(category, "category").strip());
    description = TextChecks.checkCharacters(Objects.requireNonNull(description, "description").strip());
    if (category.isEmpty()) {
      throw new DomainException(ValidationError.BLANK_CATEGORY);
    }
    if (amount.isZero()) {
      throw new DomainException(ValidationError.INVALID_AMOUNT);
    }
    attachments = List.copyOf(attachments);
    final Set<String> digests = new HashSet<>();
    for (final Attachment attachment : attachments) {
      if (!digests.add(attachment.sha256())) {
        throw new DomainException(ValidationError.INVALID_ATTACHMENT);
      }
    }
  }

  /**
   * Creates a new entry with a new identifier and without supporting document.
   *
   * @param date the date.
   * @param type income or expense.
   * @param category the category.
   * @param description the description, possibly empty.
   * @param amount the amount.
   *
   * @return the new entry.
   *
   * @throws DomainException if a value is not valid (see the constructor).
   */
  public static LedgerEntry create(final LocalDate date, final EntryType type, final String category,
                                   final String description, final Money amount) {
    return new LedgerEntry(UUID.randomUUID(), date, type, category, description, amount, List.of());
  }

  /**
   * Returns a copy of this entry with another supporting document; a document already attached is not added again.
   *
   * @param attachment the document.
   *
   * @return the new entry.
   */
  public LedgerEntry withAttachment(final Attachment attachment) {
    if (attachments.stream().anyMatch(known -> known.sha256().equals(attachment.sha256()))) {
      return this;
    }
    final List<Attachment> changed = new ArrayList<>(attachments);
    changed.add(attachment);
    return new LedgerEntry(id, date, type, category, description, amount, changed);
  }

  /**
   * Returns a copy of this entry without a supporting document.
   *
   * @param sha256 the digest of the document.
   *
   * @return the new entry.
   */
  public LedgerEntry withoutAttachment(final String sha256) {
    return new LedgerEntry(id, date, type, category, description, amount,
                           attachments.stream().filter(known -> !known.sha256().equals(sha256)).toList());
  }

}
