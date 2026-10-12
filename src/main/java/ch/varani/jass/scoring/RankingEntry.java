package ch.varani.jass.scoring;

/**
 * A line of the ranking: a participant (team, or player in individual mode), its rank and its points.
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param rank the rank, from 1; participants tied without drawing of lots share the same rank.
 * @param participant the number of the participant.
 * @param points the total of its points over the games with a result.
 * @param games the number of its games with a result.
 */
public record RankingEntry(int rank, int participant, int points, int games) {
}
