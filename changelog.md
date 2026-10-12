# Changelog

All notable changes of the project are documented in this file.

## [Unreleased]

### Added
- **About** (Help › About): the version of the application, its author with a link to the site of the author, its
  licence, a link to its source code, and a button to make a donation.
- **External display**: when the lines do not fit, a bar next to the number of the page empties until the next page
  (by steps with "Reduce animations").

### Changed
- **External display**: while the final ranking is being revealed, the display stays on its first page, the rank
  revealed last at the top; once everything is revealed, the button of the reveal becomes "Turn the pages", which
  starts the rotation of the pages when the user wishes. "Hide everything" holds the first page again.
- **About**: Close is the default button, at the right; the donation is at the left.
- **Registration**: a person declared deceased in the directory can no longer be registered. Typing their names asks
  whether it is another person with the same names (then added to the directory); a registration whose player was
  declared deceased since is marked, and cannot be corrected until the person is replaced.
- **Tables**: the columns of numbers and dates are aligned to the right everywhere, their heads too (table and places
  of the schedule, round, table and result of the scores, number of the registrations, line of the import, date of the
  accounting).
- **Documents**: the same alignment in the PDF documents: the numbers and their column titles at the right (table and
  places of the schedule, number and places of the places to post, place, points and subtotal of the score cards),
  the texts at the left; the score sheets keep the numbers of the deals centred, as the paper sheet.
- **Ranking**: the column Participant shows the players only, the number being in the column No.
- **User guide**: the screenshots are shown at the same scale as on a computer, so that the small windows (about,
  update, settings) keep their real size compared with the main window.

## [1.0.0, 1.0.1, 1.0.2, 1.0.3, 1.0.4] – 2026-10-12

First release of Jass Tournament, the desktop application of the _match aux cartes des vétérans du FC Bevaix_.

### Added
- **Editions**: list of the editions, creation, duplication (same mode, games, plan of the deals and fee), import,
  export, trash with restore, backup of the whole data folder. An edition is played in teams or individually, with
  its number of games and the content of each deal (imposed trumps ♥ ♣ ♦ ♠, _Schieber_, _Kreuz_, forced trump; the
  plan of the regulation by default, in blocks of 4 deals).
- **Registration**: players typed or chosen from the directory (without the persons already registered), previous team
  mate proposed, new persons added to the directory, correction and removal; registrations whose players were
  corrected in the directory are marked.
- **Schedule** computed by a constraint solver (OR-Tools CP-SAT) in the background, with progress and cancellation:
  - team mode (rules 1 and 6): each team meets another one at most once, gives and receives equally; with an odd
    number of teams, one team sits out each round and the teams that sat out play a catch-up round; the same
    registrations always give the same schedule;
  - individual mode: the teams of each game are formed by the solver, two players are partners at most once, the
    repeated meetings of two players as opponents are minimised; surplus players sit out once and play a catch-up
    round.
- **Scores**: entry of the score sheets as the paper sheet (column A typed, column B derived as `157 − A`), subtotals
  per block of 4 deals, totals, rule 16 deduction, absent sides (rule 19); inconsistent deals highlighted and their
  games marked in the list.
- **Ranking** (rule 14), provisional or final, exclusion for cheating (rule 17), drawing of lots of the ties (rule 15)
  once every game has a result, which closes the score sheets until it is cancelled.
- **External display** for a projector: round with its countdown and the place of every participant, then the final
  ranking revealed line by line.
- **Accounting**: registration fee and players who paid, incomes and expenses by category, balance, supporting
  documents (PDF, PNG, JPEG) stored in the edition file.
- **Directory** of the persons: optional e-mail address and phone number, contact status, participations, search,
  duplicates, merge, deletion erasing the contact details, copy of the contact details, import (CSV or pasted rows,
  with preview) and CSV export.
- **PDF documents**, with the logo of the application: schedule (a landscape page per round), places of the
  participants, score cards (four per page) and score sheets (two per landscape page, following the plan of the
  deals, the subtotal lines shaded, the grand totals under a double line).
- **User interface** following the design guide (`DESIGN.md`): native menus and shortcuts (Shortcut+N new element,
  Shortcut+S save of the form, Shortcut+F search…), light and dark themes following the system, side navigation,
  detail panel, status bar, keyboard everywhere; window and settings restored at start-up.
- **Languages**: French, German, Italian and English, following the language of the system, for the interface and the
  documents.
- **Files**: editions saved at once in `.jass` files (ZIP holding a validated XML, format version 1), directory in
  `directory.jassdir` (format version 1), written atomically; damaged or unsafe files rejected with a clear message.
  The application works offline (its only network access is the check of the updates) and logs no personal data.
- **Quality**: `mvn clean verify` runs Checkstyle, the unit and headless UI tests (TestFX, Monocle), a 100 % Jacoco
  coverage check and the Javadoc; GitHub Actions run it on every push and pull request, with CodeQL and the review of
  the dependencies.
- **Installers** for macOS (Apple Silicon and Intel, `.dmg`), Windows (`.msi`) and Linux (`.deb`), with the icon of
  the application (♥ ♠ ♦ ♣), published as GitHub releases with their SHA-256 checksums; the application carries its
  own Java runtime and native libraries.
- **Automatic update**: at each start, the application looks for a newer release on GitHub in the background (nothing
  shown without network), offers it with its notes, downloads its installer with its progress (cancellable), checks
  its SHA-256 digest, completes the saves, opens the installer and quits. The releases publish their notes
  (`release-notes.md`) with the installers and `SHA256SUMS`.
- **Licence**: Apache-2.0 (`LICENSE`, `NOTICE`).
- **User guide** in `guide/`, in French, German, Italian and English: installation, logo of the documents, directory,
  editions, registration, schedule and documents, external display, scores, ranking, accounting, backup, shortcuts;
  illustrated by screenshots of a fictitious tournament in each language, generated by
  `mvn -P guide-screenshots clean verify`.

### Fixed
- **Windows**: a save no longer fails when another program (an antivirus, the indexing of the files) holds the file
  open for a moment; the file is replaced as soon as it is released.
