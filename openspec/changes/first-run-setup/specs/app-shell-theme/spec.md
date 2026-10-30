## MODIFIED Requirements

### Requirement: Four-destination bottom navigation
The app SHALL present four bottom navigation destinations: Trans., Stats, Accounts and More. Trans. SHALL be the
start destination. Each destination SHALL retain its own scroll position and selected sub-tab across navigation. On
the first launch of an unused install the first-run welcome screen (see `first-run-setup`) SHALL be shown instead of
the shell, with no bottom navigation, until it is completed or skipped.

#### Scenario: Launching the app
- **WHEN** the app is launched and first-run setup is not offered
- **THEN** the Trans. destination is shown with Daily selected and the current month loaded

#### Scenario: Launching a fresh install
- **WHEN** the app is launched for the first time on a new install
- **THEN** the first-run welcome screen is shown with no bottom navigation bar, and Trans. is shown once it is completed or skipped

#### Scenario: Returning to a destination
- **WHEN** the user leaves Stats with the Annually mode selected and later returns to it
- **THEN** Annually is still selected

#### Scenario: What More holds
- **WHEN** the user opens More
- **THEN** it lists category management, backup, restore, import database, import from Money Manager, import Excel, export Excel, import history, appearance settings and the logging reminder, grouped as the `buku-kas` "More is grouped" requirement sets out
