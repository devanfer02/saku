---
name: compose-ui
description: Best practices for building UI with Jetpack Compose, focusing on state hoisting, detailed performance optimizations, and theming. Use this when writing or refactoring Composable functions.
---

> **Saku install note (not upstream).** In Saku, shared components live in `ui/shared/`; search there before writing one (for example `MoneyText` for every amount), and do not create a `DesignSystem.kt` or `Components.kt`. Colour literals may appear only in `ui/theme/`, and `NoHardCodedColorsTest` fails the build otherwise. Every feature has a stateless overload taking a plain state object and a stateful one that builds its ViewModel. Saku builds on Kotlin 2.0.21 with the Compose BOM 2024.10.01, where strong skipping is on by default, so method references and remembered lambdas matter less than this skill suggests. The root `CLAUDE.md` rules outrank anything here. Saku's versions are listed in the root `CLAUDE.md` and win over any version shown below.

# Jetpack Compose Best Practices

## Instructions

Follow these guidelines to create performant, reusable, and testable Composables.

### 1. State Hoisting (Unidirectional Data Flow)
Make Composables **stateless** whenever possible by moving state to the caller.

*   **Pattern**: Function signature should usually look like:
    ```kotlin
    @Composable
    fun MyComponent(
        value: String,              // State flows down
        onValueChange: (String) -> Unit, // Events flow up
        modifier: Modifier = Modifier // Standard modifier parameter
    )
    ```
*   **Benefit**: Decouples the UI from simple state storage, making it easier to preview and test.
*   **ViewModel Integration**: The screen-level Composable retrieves state from the ViewModel (`viewModel.uiState.collectAsStateWithLifecycle()`) and passes it down.

### 2. Modifiers
*   **Default Parameter**: Always provide a `modifier: Modifier = Modifier` as the first optional parameter.
*   **Application**: Apply this `modifier` to the *root* layout element of your Composable.
*   **Ordering matters**: `padding().clickable()` is different from `clickable().padding()`. Generally apply layout-affecting modifiers (like padding) *after* click listeners if you want the padding to be clickable.

### 3. Performance Optimization
*   **`remember`**: Use `remember { ... }` to cache expensive calculations across recompositions.
*   **`derivedStateOf`**: Use `derivedStateOf { ... }` when a state changes frequently (like scroll position) but the UI only needs to react to a threshold or summary (e.g., show "Jump to Top" button). This prevents unnecessary recompositions.
    ```kotlin
    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    ```
*   **Lambda Stability**: Prefer method references (e.g., `viewModel::onEvent`) or remembered lambdas to prevent unstable types from triggering recomposition of children.

### 4. Theming and Resources
*   Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` instead of hardcoded colors or text styles.
*   Organize simple UI components into specific files (e.g., `DesignSystem.kt` or `Components.kt`) if they are shared across features.

### 5. Previews
*   Create a private preview function for every public Composable.
*   Use `@Preview(showBackground = true)` and include Light/Dark mode previews if applicable.
*   Pass dummy data (static) to the stateless Composable for the preview.
