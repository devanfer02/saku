## MODIFIED Requirements

### Requirement: No categories on first run
First run SHALL create no categories. Categories come from the entry sheet's "New category" action, from the
management screen, from an import, which creates every category its file uses, or from the categories step of
first-run setup, which creates only the ones the user ticks or types (see `first-run-setup`).

#### Scenario: First run
- **WHEN** the app is launched for the first time
- **THEN** both category tabs are empty, and the entry sheet's category picker offers "New category"

#### Scenario: Categories chosen during first-run setup
- **WHEN** the user ticks "Food" in first-run setup and taps Done
- **THEN** Food exists as an expense category with the next colour slot, and no other category was created
