# Jass Tournament

Desktop application organising a Swiss _Jass_ (_Chibre_) card tournament — the _match aux cartes des vétérans du
FC Bevaix_: editions, registrations, confrontation table computed with the CP-SAT solver of Google OR-Tools (who
plays whom in each round, and which team deals first), scores and ranking, accounting and printed documents.

The rules of the tournament are those of the regulation of the match (2025 edition); the rules that the application
applies are described below (domain model, solver, scoring).

The user guide, step by step through a tournament, is in [`guide/`](guide/README.md), in every language of the
application: [français](guide/fr.md), [Deutsch](guide/de.md), [italiano](guide/it.md), [English](guide/en.md).

Licensed under the [Apache License, Version 2.0](LICENSE) (see also [`NOTICE`](NOTICE)).

## Requirements

- JDK 25
- Apache Maven 3.9.0 or later

## Run

```bash
mvn compile javafx:run
```

The application opens on the list of the editions of the data folder of the user. It speaks the language of the
system: French (formats of `fr-CH`), German (`de-CH`), Italian (`it-CH`), or English (`en-GB`) for any other language.
Its interface follows [`DESIGN.md`](DESIGN.md): native menus (the global menu bar on macOS), light or dark theme
following the system, a side navigation, the list of the view in the centre, the selected element in a detail panel at
the right (floating when the window is narrower than 1100 pixels), and a status bar showing whether everything is
saved, the selection and the notices.

| View | Content |
|------|---------|
| Side navigation | Editions, Directory and Trash, with their counts; while an edition is open, its name and its parts: Registration, Schedule, Scores, Ranking, Display and Accounting. |
| Editions (start view) | The editions, the most recent first, unreadable files reported; new edition (primary action), open (button, double click or Enter), duplicate (same mode, games, plan of the deals and fee), import, move to the trash (button or Delete). Without edition, the view says how to create one. |
| New or duplicated edition | Name, date, mode (teams or individual), games per participant, content of the games deal by deal (♥ ♣ ♦ ♠ imposed trumps, _Schieber_, _Kreuz_, forced trump), plan of the regulation, blocks of 4 deals added or removed; the reason why the edition cannot be created yet is shown. |
| Registration | Name, date and settings of the edition; the registered participants, with their count; in the detail panel, the players typed or chosen among the persons of the directory (up to 8 proposals, never the deceased nor the persons already registered in the edition), the previous team mate proposed, new persons added to the directory; Shortcut+S registers the entry (corrects the selected registration, or adds a new one); correction and removal (button or Delete) of a registration; the registrations whose players were corrected in the directory since are marked (warning colour and icon), and Correct takes the corrected names. Closing the registration (primary action) computes the schedule in the background, with a progress bar (time spent out of the time limit of the solver, updated each second) and cancellation, or tells why no schedule exists; the registration can be reopened while no score is entered. |
| Schedule | Each round with its tables, places, giver and receiver, and the participants sitting out; export of the schedule, the places, the score cards and the score sheets as PDF. Before the closing of the registration, the view says so and leads to the registration. |
| Scores | The games of the schedule with their sides and results, and the count of the games with a result; in the detail panel, the score sheet of the selected game as the paper sheet: column A (the side at the odd place, which deals first) and column B, one line per deal of the plan with its type, a subtotal per block of 4 deals, the totals, the rule 16 deduction and the points counted in the ranking. Column A is typed and column B derived (`157 − A`, shown in grey) until it is typed; a value is taken on Enter (which moves to the next deal) or when the field is left, and saved at once; Up and Down move between the deals; a value that is not a number between 0 and 157 is rejected; the deals whose columns do not make 157 points are highlighted and listed, and their game is marked in the list (warning colour and icon). A side can be marked absent (rule 19: forfeit). The sheet is cleared after confirmation (button or Delete); Enter in the list moves to the sheet; the tool of the toolbar goes to the next game without result. |
| Ranking | Computed from the results each time it is shown: provisional (with the number of games with a result), ties to break, or final; rank, number, participant, points of every game, total and number of games. Drawing of lots of the ties (primary action, rule 15) once every game has a result, which closes the match: the score sheets and the exclusions can no longer be changed until the draw is cancelled (after confirmation); exclusion of the selected participant for cheating (rule 17) after confirmation, and its cancellation. |
| Display | Control of the external display (F18), a second window to move to a projector or a television: open and close it (also from the menu View), the screen and the full screen; the round shown (the round in progress by default), the time of a game (60 minutes by default, saved with the edition), the start signal of the countdown and its cancellation; once every game has a result and the ties are broken, the final ranking revealed one line at a time from the last, hidden again, or back to the round. The display window shows the logo of the documents, the edition, the round, the countdown (time remaining, "time is over" at zero) and the place and table of every participant, or the final ranking with the points of every round; the lines that do not fit alternate by pages every 10 seconds. It shows no contact detail. |
| Accounting | The summary (registration fees paid, incomes and expenses per category, totals, balance in red when negative, fees outstanding), the entries by date, the fee per player (CHF) and a box per player telling whether it paid, saved at once. In the detail panel, the selected entry or a new one (primary action, Shortcut+N): date, income or expense, category (proposed from those already used), description, amount typed with a dot or a comma; saved by its button or Shortcut+S, deleted after confirmation (button or Delete). The supporting documents of a saved entry (PDF, PNG or JPEG, 20 MB at most each) are added by the file chooser or dropped from the file manager, stored in the edition file, opened with the application of the system through a temporary copy deleted when the application quits, and removed after confirmation. |
| Directory | Search (Shortcut+F), filter by edition, the persons with their count; in the detail panel, the form of a person (names, e-mail, phone, notes, contact status, participations); new person (primary action, Shortcut+N; possible duplicates confirmed), saved by its button or Shortcut+S, so that persons are typed one after the other from the keyboard; deletion, erasure of the contact details, merge of two persons; copy of the e-mail addresses or phone numbers of the selection without the persons not to be contacted; import and export as CSV. |
| Import of persons | CSV file or rows pasted from a spreadsheet; separator and columns guessed, then chosen by the user; preview with the invalid values and the possible duplicates; duplicates imported only when asked. |
| Trash | Deleted editions: restore, empty after confirmation. |
| Settings | "Settings…" in the File menu on macOS, "Options…" in the Tools menu elsewhere (Shortcut+,): theme (as the system, light, dark), accent colour of the system, reduced animations, and the logo of the documents — a PNG or JPEG image of at most 2 MB, printed at the top left of every document, shown in the panel. |
| Update | At start-up, when a newer release is published on GitHub: its version and notes, Download and install or Not now; the download with its progress and Cancel; then the installer is opened and the application quits (see Automatic update). |
| Menus | File: new element of the view (Shortcut+N), save of the form of the view in the registration, the accounting and the directory (Shortcut+S), import (Shortcut+O), export (Shortcut+E), move the edition to the trash, back up everything, quit (the application menu on macOS). View: editions, directory, trash (Shortcut+1, 2, 3), search (Shortcut+F), side bar (Shortcut+B), external display. F6 and Shift+F6 move the focus from a panel to the next. |

The destructive actions are confirmed in a message box that names the action; the information that needs no decision
is shown in the status bar. The settings, the size and position of the window and the widths of its panels are saved
in the data folder (`settings.properties`).

The operations on the files run in the background, one at a time; the pending ones are completed when the application
quits. The data folder can be replaced by setting the system property `ch.varani.jass.dataFolder` (the tests use it).

## Build and test

```bash
mvn clean verify
```

This runs the whole quality gate: Checkstyle ([`jass_java_checks.xml`](jass_java_checks.xml)), the tests (JUnit 6),
the coverage check (Jacoco, 100 % on every counter) and the Javadoc generation (private members included, warnings
are errors). Tests tagged `slow` are excluded by default; run them with `-Dtest.excludedGroups=none`.

The screenshots of the user guide are generated, in every language, by

```bash
mvn -P guide-screenshots clean verify
```

which runs the whole quality gate, then starts the application headless on fictitious data, shows each screen
described by the guide and writes it, with the first page of each PDF document, into `guide/images/<language>/`. The
clock, the solver and the points are fixed, so that the same application always gives the same images.

On Apple Silicon Macs, the Maven profile `macos-aarch64-native` unpacks the OR-Tools native libraries once into
`.native/` (kept by `mvn clean`, git-ignored) and the tests load them from there: without it, macOS scans the
libraries extracted by OR-Tools at every run, which takes about 15 seconds. Only the very first build pays this
price. The JavaFX tests run headless (TestFX and Monocle), each test
class in its own JVM.

## Package

```bash
mvn -P installer clean verify
```

The profile `installer` builds, after the quality gate, the installer of the system it runs on (`jpackage` cannot
cross-compile): `.dmg` on macOS (Apple Silicon or Intel), `.msi` on Windows (needs WiX 5:
`dotnet tool install --global wix --version 5.0.2` and its extensions `WixToolset.Util.wixext` and
`WixToolset.UI.wixext`), `.deb` on Linux (needs `fakeroot`), into `target/installer/dist/`. The steps are:

1. the application jar and its automatic modules (OR-Tools, protobuf, JNA, PDFBox, libphonenumber) go to
   `target/installer/mods`;
2. the native libraries of OR-Tools and of JavaFX of the platform are unpacked into `target/installer/native`; the
   application ships them and loads them through `java.library.path`, never from a temporary directory (on Windows,
   the libraries of OR-Tools are loaded one by one, in the order of their dependencies);
3. `jlink` links the runtime from the JDK and the JavaFX modules (`target/installer/runtime`);
4. `jpackage` makes the application (`target/installer/image`), with its icon, then the installer; its options are in
   `src/packaging/*.args`, the icons in `src/packaging/icons/`.

The packaged application is checked by its smoke test, which computes a small schedule, writes a PDF and starts
JavaFX without window, then quits:

```bash
"target/installer/image/Jass Tournament.app/Contents/MacOS/Jass Tournament" --smoke-test   # macOS
"target/installer/image/Jass Tournament/bin/Jass Tournament" --smoke-test                    # Linux
"target\installer\image\Jass Tournament\Jass Tournament.exe" --smoke-test                  # Windows
```

The installers are not signed yet: macOS asks to confirm the opening of the application (right click, Open), Windows
SmartScreen warns before the installation.

## Domain model

The package `ch.varani.jass.domain` holds the immutable model of an edition:

```java
Edition edition = Edition.create("Match aux cartes 2026", LocalDate.of(2026, 11, 20), TournamentMode.TEAM);
TeamRegistrations teams = edition.teamRegistrations()
    .add(new Team(new Player("Anne", "Fontaine"), new Player("Bruno", "Rochat")));
edition = edition.withRegistrations(teams);   // every modification returns a new edition
boolean ready = edition.hasEnoughRegistrations();
Edition next = edition.duplicate("Match aux cartes 2027", LocalDate.of(2027, 11, 19));   // same settings, no teams
```

| Type | Role |
|------|------|
| `Regulation` | The constants of the regulation (4 games, 16 deals, 157 / 628 / 2'512 / 1'256 points…), each citing its rule. |
| `Edition` | Name, date, number of games per participant (4 by default), plan of the deals, registrations, schedule, time of a game (60 minutes by default, 1 to 300); `create`, `duplicate` (new edition with the mode, the games, the deals and the time of a game, without registration), `withName`, `withDate`, `withGames`, `withGameMinutes`, `withDeals`, `withRegistrations`, `withSchedule`, `withoutSchedule`, `isScheduled`, `mode`, `hasEnoughRegistrations`, `teamRegistrations`, `individualRegistrations`. A schedule must match the mode, the number of participants and of games: adding or removing a participant, or changing the games, requires `withoutSchedule()` first (renaming a player keeps the schedule). |
| `DealPlan`, `DealType`, `TrumpMode` | The content of the games: the type of each deal (imposed trump ♥ ♣ ♦ ♠, _Schieber_, _Kreuz_, forced trump), the same for every game. `DealPlan.REGULATION` is the plan of rules 3 and 8 to 11; any other sequence is allowed, provided the number of deals is a positive multiple of 4, so that each player at the table starts the same number of deals. `blocks()` gives the number of blocks of 4 deals (one subtotal each, for the display and the documents), `pointsPerGame()` and `forfeitPoints()` follow the number of deals. |
| `Schedule` | Sealed: `TeamSchedule` or `IndividualSchedule`; `mode`, `participants`, `games`. |
| `TeamSchedule`, `TeamRound`, `TeamGame` | The schedule of a team tournament: rounds of games, each game at a table `k` between a giver (place `2k − 1`, deals first, rule 6) and a receiver (place `2k`), identified by their team numbers. |
| `IndividualSchedule`, `IndividualRound`, `IndividualGame`, `Partners` | The schedule of an individual tournament: the same tables and places, each side being a team of two `Partners` formed by the solver, identified by their player numbers. |
| `Registrations` | Sealed: `TeamRegistrations` (teams) or `IndividualRegistrations` (players), numbered from 1 by order of registration; `add`, `replace`, `remove`, `get`, `size`, `allPlayers`, `minimumSize`. |
| `Team`, `Player` | A team of two different players; a player with a mandatory first name and an optional last name. Names with control characters, lone surrogates or unassigned code points are rejected. |
| `TournamentMode` | `TEAM` or `INDIVIDUAL`. |
| `DomainException`, `ValidationError` | Rejection of an invalid value (blank name, number of deals not a multiple of 4, same person registered twice, unknown number, wrong mode, schedule not matching the registrations…). |

## Solver

The package `ch.varani.jass.solver` computes the schedule with CP-SAT, in team mode:

```java
TeamRegistrations teams = edition.teamRegistrations();
TeamScheduleSolver solver = new TeamScheduleSolver(teams.size(), edition.games(), SolverOptions.DEFAULT);
switch (solver.solve()) {   // solver.cancel() stops the search from another thread
  case SolverResult.Solved<TeamSchedule> solved -> edition = edition.withSchedule(solved.schedule());
  case SolverResult.Unsolved<TeamSchedule> unsolved -> report(unsolved.reason());
}
```

and in individual mode:

```java
IndividualScheduleSolver solver = new IndividualScheduleSolver(edition.registrations().size(), edition.games(),
                                                               SolverOptions.PARALLEL);
if (solver.solve() instanceof SolverResult.Solved<IndividualSchedule>(IndividualSchedule schedule)) {
  for (IndividualRound round : schedule.rounds()) {
    for (IndividualGame game : round.games()) {
      Partners giver = game.giver();   // two player numbers, deal first (rule 6), at place game.giverPlace()
    }
  }
}
```

| Type | Role |
|------|------|
| `ScheduleSolver` | Sealed: `TeamScheduleSolver` or `IndividualScheduleSolver`. `solve()` returns the schedule or the reason why there is none; `cancel()` stops the search from any thread. |
| `SolverOptions` | Random seed, number of workers, time limit. `DEFAULT`: seed 0, one worker (the same input always gives the same schedule), 60 s, for the team mode. `PARALLEL`: the same with 8 workers, for the individual mode. |
| `SolverResult` | Sealed: `Solved(schedule)` or `Unsolved(reason)`, with `Reason` `ODD_GAMES`, `NOT_ENOUGH_TEAMS`, `NOT_ENOUGH_PLAYERS`, `NO_CATCH_UP_ROUND`, `INFEASIBLE`, `TIME_LIMIT` or `CANCELLED`. |

**Team mode.** The solver guarantees that a team never plays itself, meets another team at most once, plays exactly
the number of games and at most once per round, and gives (deals first) in half of its games. With an odd number of
teams, exactly one team sits out each of the first rounds, and the catch-up round is played by these teams only
(rule 1). In each round, the tables are numbered from 1.

**Individual mode.** The players register alone and the solver forms the teams of each game. It guarantees that each
player plays exactly the number of games and at most once per round, gives in half of its games, and that two players
are partners at most once. When the number of players is not a multiple of 4, the surplus players (1 to 3) sit out
each of the first rounds, never the same twice, and play a catch-up round together, as rule 1 does for the teams
(e.g. 42 players: 4 rounds of 10 tables, then a catch-up round of 2 tables); some numbers of players cannot form a
catch-up round (6, 7 and 11 players with 4 games). The solver minimises the maximum number of times two players face
each other as opponents, then the total number of repeated meetings: from 10 players, no two players face each other
twice. 100 players are solved to optimality in about 20 seconds with `PARALLEL` (8 workers); with several workers,
the schedule may differ from one solving to the next, but its quality does not.

## Scoring

The results of an edition are entered as raw input (`Results`: score sheets, exclusions, drawing of lots); the
package `ch.varani.jass.scoring` computes everything else, never stored:

```java
ScoreSheet sheet = ScoreSheet.empty(1, 3, edition.deals())          // round 1, table 3
    .withDeal(1, DealScore.of(113))                                // column A (giver, odd place); B = 157 − 113
    .withDeal(2, new DealScore(76, 80));                           // B typed as on the paper: inconsistent, kept
edition = edition.withResults(edition.results().withSheet(sheet));
Scoring scoring = new Scoring(edition);
SheetTotals totals = scoring.totals(sheet);                        // subtotals per 4 deals, totals, rule 16
Ranking ranking = scoring.ranking();                               // rule 14, without the excluded teams
if (ranking.complete() && ranking.hasTies()) {
  edition = edition.withResults(edition.results().withTieBreak(scoring.drawLots()));   // rule 15, seed recorded
}
```

| Type | Role |
|------|------|
| `DealScore` | The points of a deal as typed: column A (giver, the team at the odd place, which deals first) and column B, each between 0 and 157; a missing column is derived as `157 −` the other; no value: deal not played (rule 2); `isConsistent()` when both sum to 157. |
| `ScoreSheet`, `Absence` | The sheet of a game (round, table): one `DealScore` per deal of the plan, and the absent sides (rule 19). |
| `Results`, `TieBreak` | The sheets (one per game), the participants excluded for cheating (rule 17), the drawing of lots (seed and order, rule 15). `Edition` checks that they match its schedule, plan and registrations. |
| `SheetTotals` | Subtotals per block of 4 deals, totals, rule 16 deduction (half of the excess over the points of a game, rounded up), inconsistent deals. |
| `Scoring`, `GameResult`, `Ranking`, `RankingEntry` | Result of each game (sheet, or forfeit: the absent or excluded side 0, the other 1'256), ranking by total (excluded participants removed, ties sharing a rank until the draw), drawing of lots once every game has a result. In individual mode, each partner gets the points of its side. |

## Accounting

The accounting of an edition (`Ledger`) holds the registration fee of a player and the entries (incomes and
expenses) with their supporting documents; `ch.varani.jass.accounting` computes the overview:

```java
Ledger ledger = edition.ledger().withFee(Money.of("30"))                      // CHF, per player
    .add(LedgerEntry.create(date, EntryType.EXPENSE, "Prix", "Vin", Money.of("120.50")));
edition = edition.withLedger(ledger);                                        // Player.withPaid(true) per payment
Attachment invoice = store.addAttachment(id, chosenFile);                    // PDF, PNG or JPEG, 20 MiB at most
AccountingSummary summary = AccountingSummary.of(edition);                   // fees, per category, balance
```

| Type | Role |
|------|------|
| `Money` | An amount in CHF, `BigDecimal` with two decimals, between 0 and 999'999.99. |
| `Ledger`, `LedgerEntry`, `EntryType` | The fee of a player and the entries: date, income or expense, free category (`categories()` proposes those already used), description, amount, documents. |
| `Attachment`, `AttachmentType` | A supporting document, referenced by its SHA-256 digest, stored once in the `.jass` file as `attachments/<sha256>.<pdf\|png\|jpg>`. |
| `AccountingSummary`, `CategoryTotal` | Players and players who paid, fees paid and outstanding, incomes and expenses per category, total income (fees paid included), total expenses, balance. |

## Documents

The package `ch.varani.jass.report` generates the PDF documents of an edition, each with the logo of the application,
its title and the edition (name and date):

```java
DocumentOptions options = new DocumentOptions(Locale.forLanguageTag("fr-CH"),
                                              new LogoStore(DataFolder.ofCurrentUser()).load().orElse(null));
ScheduleDocument.write(edition, options, out);     // a landscape page per round (split the same way in every round
                                                   // when too large): tables, places, giver, receiver, sitting out
PlacesDocument.write(edition, options, out);       // place of each team in each round, to be posted
ScoreCardsDocument.write(edition, options, out);   // 4 cards per A4 page: round, place, points, subtotal; total in
                                                   // bold under a double line, place and points shaded
ScoreSheetsDocument.write(edition, options, out);  // 2 sheets per A4 landscape page, following the plan of the deals
```

The texts are in French, German, Italian or English (`ch/varani/jass/report/documents*.properties`). The font DejaVu Sans 2.37 (licence
in `ch/varani/jass/report/fonts/DejaVu-LICENSE.txt`) is embedded; it covers the accents and the suits ♥ ♣ ♦ ♠. The
logo is chosen once for the application (`LogoStore`: PNG or JPEG, 2 MiB at most, stored in the data folder).

## Storage

The package `ch.varani.jass.persistence` stores the editions in the data folder of the user, without ever asking
for a path:

| System | Data folder |
|--------|-------------|
| macOS | `~/Library/Application Support/Jass Tournament` |
| Windows | `%APPDATA%\Jass Tournament` |
| Linux | `$XDG_DATA_HOME/jass-tournament` (by default `~/.local/share/jass-tournament`) |

It holds `directory.jassdir` (the directory, see below), `editions/<id>.jass` and `trash/<id>.jass` (deleted editions);
the file name of an edition is an identifier, independent of the name of the edition. The installer of an update is
downloaded into `updates/`, emptied at the next start (see Automatic update).

```java
EditionStore store = new EditionStore(DataFolder.ofCurrentUser());   // from a background thread
UUID id = store.create(edition);
store.save(id, edition.withName("Match aux cartes 2026"));          // atomic: temporary file, then move
EditionList list = store.list();                                    // headers only, most recent first
Edition opened = store.open(id);                                    // complete reading, validated
store.delete(id);                                                   // to the trash: restore(id), emptyTrash()
store.exportTo(id, target);  store.importFrom(source);  store.backUpEverything(zip);
```

An edition file (`.jass`) is a ZIP archive whose only entry is `edition.xml`: UTF-8 XML, format version 1, validated
against the schema `ch/varani/jass/persistence/edition.xsd`, with the settings (name, date, mode, games, time of a game — optional, 60 minutes when absent) first, then
the plan of the deals, the registrations, the schedule, the results (typed values only) and the accounting; the
supporting documents follow as entries `attachments/<sha256>.<ext>` (checked by name, number and size when read, by
digest when exported: `addAttachment`, `exportAttachment`). Every change is saved at once; there are no versioned backups. The reader rejects
damaged archives, unexpected entries (path traversal), entries larger than 8 MiB once decompressed, document type
declarations (XXE), other encodings and newer format versions, with a `FileFormatException` whose `FormatError`
the user interface translates.

## Directory

The package `ch.varani.jass.directory` holds the directory (_annuaire_) of the persons who took part in the editions,
kept across the editions to register them again and to contact them:

```java
DirectoryStore store = new DirectoryStore(DataFolder.ofCurrentUser());   // directory.jassdir, from a background thread
Directory directory = store.load();                                     // empty before the first save
Person anne = Person.create("Anne", "Fontaine")
    .withEmail(new EmailAddress("anne@example.ch"))                     // optional, lower case
    .withPhone(new PhoneNumber("079 000 00 01"));                       // optional, stored as +41790000001
List<Person> duplicates = directory.duplicatesOf(anne);                 // same names, e-mail or phone
directory = directory.add(anne);
Team team = new Team(anne.asPlayer(), new Player("Bruno", "Rochat"));    // the edition keeps the names and the id
directory = directory.recordParticipations(editionId, edition);         // each time the edition is saved
List<String> bcc = Directory.emailsOf(directory.participantsOf(editionId));   // without "do not contact"
store.save(directory);                                                  // atomic
```

| Type | Role |
|------|------|
| `Directory` | The persons, with distinct identifiers: `add`, `addAll`, `replace`, `remove` (contact details gone for good), `get`, `find`, `search` (every word, accents and case ignored), `registrationCandidates` (without the deceased persons), `duplicatesOf`, `merge`, `recordParticipations`, `participantsOf`, `lastTeamMate`, `emailsOf`, `phonesOf`. |
| `Person` | Identifier, first name (mandatory), last name, optional e-mail address, phone number and notes, contact status and its date, participations (at most one per edition); `asPlayer` links a player of an edition to the person. |
| `EmailAddress`, `PhoneNumber` | Optional contact details, validated and normalised: e-mail in lower case; phone validated by libphonenumber, Switzerland by default, stored in the format E.164. |
| `ContactStatus` | `CONTACTABLE`, `DO_NOT_CONTACT`, `DECEASED`: the persons who are not contactable are left out of the contact lists; the deceased are no longer proposed during the registration. |
| `Participation` | Edition (identifier, copy of the name and date) and team mate in team mode. |
| `Csv`, `ColumnMapping`, `DirectoryImport`, `ImportRow` | Import of persons from a CSV file or rows pasted from a spreadsheet: the user maps the columns, the preview reports every invalid value and the possible duplicates, nothing is added before the confirmation. |
| `DirectoryExport` | Export of persons as a table (CSV), every value held about them, e.g. to answer a request for access; the cells are protected against formula injection. |

The directory file `directory.jassdir` (in the data folder) is a GZIP-compressed UTF-8 XML document, format version 1,
validated against `ch/varani/jass/persistence/directory.xsd`; it is read by `DirectoryReader` with the same security
settings as the edition files (size cap, no DTD, schema), and written atomically. `DirectoryStore.readText` reads a
CSV file to import (UTF-8 only, at most 4 MiB); `DirectoryStore.writeText` writes an export (UTF-8 with a byte order
mark). The contact details are stored in the directory only: never in the `.jass` files, never on a document, never
logged.

## Dependencies

The versions are those of `pom.xml` (its `<properties>`), kept up to date with `mvn versions:display-dependency-updates`.

| Dependency | Role | Scope |
|------------|------|-------|
| `com.google.ortools:ortools-java` | CP-SAT solver | compile |
| `org.openjfx:javafx-controls`, `javafx-fxml` | user interface | compile |
| `com.googlecode.libphonenumber:libphonenumber` | phone numbers of the directory | compile |
| `org.apache.pdfbox:pdfbox` (with `fontbox`, `pdfbox-io`, `commons-logging`) | PDF documents | compile |
| `org.junit.jupiter:junit-jupiter-api`, `-params`, `-engine` | unit tests | test |
| `org.junit.platform:junit-platform-commons` | unit tests | test |
| `org.testfx:testfx-junit5` | user interface tests | test |
| `org.testfx:openjfx-monocle` | headless user interface tests | test |

The profile `installer` uses the build plugins `org.codehaus.mojo:exec-maven-plugin` (runs `jlink` and `jpackage` of
the JDK), `org.codehaus.mojo:build-helper-maven-plugin` (version of the installer) and
`org.apache.maven.plugins:maven-dependency-plugin` (modules and native libraries); nothing of them is shipped.

## Automatic update

At each start, the application looks in the background for a newer release on GitHub (package
`ch.varani.jass.update`, `GitHubUpdater`); the start is never blocked, and without network or on any error nothing is
shown. It reads `SHA256SUMS` of the latest release
(`https://github.com/porchy13/jass-tournament/releases/latest/download/SHA256SUMS`), which names the installer of each platform with its version (`jass-tournament-<version>-<platform>.<extension>`), and,
if this version is newer, the notes of the release (`release-notes.md`, checked against their digest). The user is
offered to install it, with its notes, or to postpone it until the next start. Once accepted, the installer is
downloaded with its progress (cancellable) into `updates/` of the data folder, its SHA-256 digest checked against
`SHA256SUMS` (a damaged file is deleted and the failure explained); the pending saves are completed, the installer is
opened with the application of the system (the system then asks for the installation) and the application quits.

The requests use HTTPS only, with short time-outs, and are plain downloads of public files: they send no data of the
user. A development version (with a qualifier, e.g. `1.0.0-SNAPSHOT`, as when run by `mvn javafx:run`) never looks
for an update; the version of the application is the one of `pom.xml`, written into its resources by Maven
(`src/main/resources-filtered`). The installers are not signed yet, so only their digest is checked.

## Privacy

Real tournament data (participant names, results, payments, contact details) must never be committed, logged or sent
over the network; the tests use fictitious names only. The files used during the editions (`rounds.md`,
`positions_per_team.*`, `*.jass`, `*.jassdir`…) are git-ignored.

The application works offline. Its only network access is the check of the latest release at start-up and the
download of its installer (see Automatic update): no data of the user is sent, but GitHub sees the IP address of the
computer, as for any visit of its pages.

## Repositories and continuous integration

The reference repository is a private GitLab instance, which holds the private documents of the project (the
regulation, the development plan, the technical reports) and runs its own pipeline (quality gate, SAST, Secret
Detection). The public GitHub repository, [porchy13/jass-tournament](https://github.com/porchy13/jass-tournament),
receives from it, once its quality gate passes on `main`, a snapshot of the public files as a new commit: GitHub has
a history of its own, without the private documents. The contributions made on GitHub (issues, pull requests) are
integrated through the reference repository, then published with the next snapshot.

The GitHub Actions (`.github/workflows/`) run on GitHub only:

| Workflow | Runs | Content |
|----------|------|---------|
| `ci.yml` | every push and pull request on `main` | quality gate `mvn clean verify` on Temurin 25 (Checkstyle, tests, 100 % Jacoco, Javadoc), test and coverage reports kept as artifacts; the slow solver tests, whose performance targets are measured on the reference computer, are run locally before a release |
| `codeql.yml` | every push and pull request on `main`, weekly | CodeQL analysis of the Java code (security and quality queries) |
| `dependency-review.yml` | every pull request on `main` | vulnerabilities (moderate or higher) and licences of the dependencies changed |
| `release.yml` | a version tag `vX.Y.Z` (published from the reference repository) | checks that the version of `pom.xml` is the one of the tag and takes the notes of its section of `changelog.md`; builds, tests and smoke-tests the installers on macOS Apple Silicon and Intel (`.dmg`), Windows (`.msi`) and Linux (`.deb`); publishes them as a GitHub release with the notes (`release-notes.md`) and their SHA-256 checksums (`SHA256SUMS`), read by the automatic update |

A release is published only for a version tag: the tags `vX.Y.Z` of the reference repository are published on GitHub
with the snapshot they mark, and start `release.yml`.
