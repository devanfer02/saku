## ADDED Requirements

### Requirement: Four-destination bottom navigation
The app SHALL present four bottom navigation destinations: Trans., Stats, Accounts and More. Trans. SHALL be the
start destination. Each destination SHALL retain its own scroll position and selected sub-tab across navigation.

#### Scenario: Launching the app
- **WHEN** the app is launched
- **THEN** the Trans. destination is shown with Daily selected and the current month loaded

#### Scenario: Returning to a destination
- **WHEN** the user leaves Stats with the Annually mode selected and later returns to it
- **THEN** Annually is still selected

#### Scenario: What More holds
- **WHEN** the user opens More
- **THEN** it lists category management, import from Money Manager, import Excel, export Excel, import history, and appearance settings

### Requirement: Ship both a light and a dark theme
The app SHALL provide a light and a dark theme, each defined as a complete token set rather than one derived by
inverting the other. The theme SHALL follow the system setting by default and SHALL be overridable to always-light
or always-dark from appearance settings.

#### Scenario: Following the system
- **WHEN** the device switches to dark mode and the app setting is "Follow system"
- **THEN** the app adopts the dark token set without restarting

#### Scenario: Overriding the system
- **WHEN** the user selects always-light while the device is in dark mode
- **THEN** the app renders light

#### Scenario: Every surface is painted
- **WHEN** any screen is rendered in either theme
- **THEN** its background, text and icon colours all come from that theme's token set, with no colour defined for only one theme

### Requirement: Blue accent, separate semantic colours for money direction
The accent colour SHALL be blue in both themes and SHALL carry brand and interactive state only. Income and expense
SHALL each have their own semantic colour, distinct from the accent, so that a figure's direction never depends on
chrome. Transfers and adjustments SHALL render in a neutral ink.

#### Scenario: Rendering amounts
- **WHEN** a list shows an income, an expense and a transfer
- **THEN** the income uses the income colour, the expense the expense colour, and the transfer a neutral ink, none of which is the accent

#### Scenario: Accent usage
- **WHEN** the active navigation item, the active tab, the primary button and the focused field are rendered
- **THEN** all four use the accent colour

### Requirement: An eight-slot categorical chart palette, validated per theme
The app SHALL define eight categorical colour slots per theme, in a shared slot order so that an entity keeps its
identity across themes. Each theme's set MUST satisfy a lightness band, a chroma floor, an adjacent-pair
colour-vision separation threshold, and a contrast floor against that theme's chart surface. Charts MUST NOT convey
identity by colour alone.

#### Scenario: An entity keeps its colour across themes
- **WHEN** an account is assigned slot 1 and the user switches from light to dark
- **THEN** the account renders in the dark set's slot 1, which is the same hue re-stepped rather than a different colour

#### Scenario: Secondary encoding is always present
- **WHEN** any donut is rendered
- **THEN** its segments are separated by a visible gap and a labelled list naming every segment is present alongside it

#### Scenario: Assigning a slot to a new entity
- **WHEN** a category or account is created
- **THEN** it receives the next slot in creation order, wrapping after the eighth, and that slot is pinned to it thereafter

### Requirement: Format currency in Indonesian rupiah convention
Amounts SHALL be stored as whole rupiah integers and displayed with a full stop as the thousands separator and no
decimal component. The `Rp` prefix SHALL be shown in summary and balance figures and MAY be omitted in dense list
rows where the currency is unambiguous.

#### Scenario: Formatting an amount
- **WHEN** the amount 5061400 is displayed as a summary figure
- **THEN** it reads `Rp 5.061.400`

#### Scenario: No floating point
- **WHEN** amounts are summed, differenced or aggregated anywhere in the app
- **THEN** the arithmetic is performed on integers, so no rounding drift can accumulate

### Requirement: Declare no network access
The application manifest MUST NOT declare the `INTERNET` permission or any other network permission, and the app
MUST contain no advertising, analytics or telemetry dependency.

#### Scenario: Inspecting the manifest
- **WHEN** the merged manifest is inspected
- **THEN** it declares no `INTERNET` permission

#### Scenario: Attempting a network call
- **WHEN** any code path attempts to open a network connection
- **THEN** the platform denies it, because the permission was never granted
