package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The accounting of an edition (F8), as entered by the user: the registration fee of a player and the entries
 * (incomes and expenses) with their supporting documents.
 *
 * <p>The registration fee is a setting of the edition, paid per player (Q7, as in the former sheet "Paiements"); which
 * players have paid is recorded on the players of the registrations ({@link Player#paid()}). The totals are computed
 * by {@code ch.varani.jass.accounting}, never stored.</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param fee the registration fee of a player.
 * @param entries the entries, in the order of their entry, with distinct identifiers.
 */
public record Ledger(Money fee, List<LedgerEntry> entries) {

  /** The accounting of a new edition: no fee, no entry. */
  public static final Ledger EMPTY = new Ledger(Money.ZERO, List.of());

  /**
   * Creates the accounting, checking that the identifiers of the entries are distinct.
   *
   * @param fee the registration fee of a player.
   * @param entries the entries.
   *
   * @throws NullPointerException if a value is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_ENTRY} if two entries have the same identifier.
   */
  public Ledger {
    Objects.requireNonNull(fee, "fee");
    entries = List.copyOf(entries);
    final Set<UUID> ids = new HashSet<>();
    for (final LedgerEntry entry : entries) {
      if (!ids.add(entry.id())) {
        throw new DomainException(ValidationError.DUPLICATE_ENTRY);
      }
    }
  }

  /**
   * Tells whether nothing has been entered.
   *
   * @return {@code true} if the fee is zero and there is no entry.
   */
  public boolean isEmpty() {
    return fee.isZero() && entries.isEmpty();
  }

  /**
   * Returns a copy of this accounting with another registration fee.
   *
   * @param newFee the registration fee of a player.
   *
   * @return the new accounting.
   */
  public Ledger withFee(final Money newFee) {
    return new Ledger(newFee, entries);
  }

  /**
   * Returns an entry.
   *
   * @param id the identifier of the entry.
   *
   * @return the entry.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_ENTRY} if no entry has this identifier.
   */
  public LedgerEntry get(final UUID id) {
    return entries.stream().filter(entry -> entry.id().equals(id)).findFirst()
                  .orElseThrow(() -> new DomainException(ValidationError.UNKNOWN_ENTRY));
  }

  /**
   * Returns a copy of this accounting with one more entry.
   *
   * @param entry the new entry.
   *
   * @return the new accounting.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_ENTRY} if an entry has the same identifier.
   */
  public Ledger add(final LedgerEntry entry) {
    final List<LedgerEntry> changed = new ArrayList<>(entries);
    changed.add(entry);
    return new Ledger(fee, changed);
  }

  /**
   * Returns a copy of this accounting where an entry is replaced by its new version (same identifier).
   *
   * @param entry the new version of the entry.
   *
   * @return the new accounting.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_ENTRY} if no entry has its identifier.
   */
  public Ledger replace(final LedgerEntry entry) {
    get(entry.id());
    return new Ledger(fee, entries.stream().map(known -> known.id().equals(entry.id()) ? entry : known).toList());
  }

  /**
   * Returns a copy of this accounting without an entry; its documents are no longer referenced, and are dropped from
   * the edition file at its next save unless another entry refers to them.
   *
   * @param id the identifier of the entry.
   *
   * @return the new accounting.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_ENTRY} if no entry has this identifier.
   */
  public Ledger remove(final UUID id) {
    get(id);
    return new Ledger(fee, entries.stream().filter(entry -> !entry.id().equals(id)).toList());
  }

  /**
   * Returns the categories already used, to propose them when an entry is typed: one spelling per category, accents,
   * case and spaces ignored (the first spelling used), sorted.
   *
   * @return the categories.
   */
  public List<String> categories() {
    final Map<String, String> categories = new LinkedHashMap<>();
    for (final LedgerEntry entry : entries) {
      categories.putIfAbsent(Player.comparisonKey(entry.category()), entry.category());
    }
    return categories.entrySet().stream()
                     .sorted(Map.Entry.comparingByKey())
                     .map(Map.Entry::getValue)
                     .toList();
  }

  /**
   * Returns the documents referenced by the entries, each once, sorted by digest: the contents the edition file must
   * hold.
   *
   * @return the documents.
   */
  public List<Attachment> attachments() {
    final Map<String, Attachment> attachments = new LinkedHashMap<>();
    for (final LedgerEntry entry : entries) {
      for (final Attachment attachment : entry.attachments()) {
        attachments.putIfAbsent(attachment.sha256(), attachment);
      }
    }
    return attachments.values().stream().sorted(Comparator.comparing(Attachment::sha256)).toList();
  }

}
