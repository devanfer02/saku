---
name: android-data-layer
description: Guidance on implementing the Data Layer using Repository pattern, Room (Local), and Retrofit (Remote) with offline-first synchronization.
---

> **Saku install note (not upstream).** Saku is local-only with no INTERNET permission (a build gate enforces it), so the Retrofit, sync, stale-while-revalidate and WorkManager outbox sections do not apply: Room is the only source. Saku uses Room 2.6.1 with KSP; do not use the Room 2.7+ multiplatform APIs (`BundledSQLiteDriver`, `RoomDatabase.Builder.setDriver`, KMP source sets). There is no Hilt, so skip `@Inject` and `@Binds`; repositories are wired by hand in `AppContainer`. Entities become domain types in `data/` and never leak further. Destructive writes archive with a flag and never delete; no `deleteAll`, no unscoped `UPDATE`. The root `CLAUDE.md` rules outrank anything here. Saku's versions are listed in the root `CLAUDE.md` and win over any version shown below.

# Android Data Layer & Offline-First

## Instructions

The Data Layer coordinates data from multiple sources.

### 1. Repository Pattern
*   **Role**: Single Source of Truth (SSOT).
*   **Logic**: The repository decides whether to return cached data or fetch fresh data.
*   **Implementation**:
    ```kotlin
    class NewsRepository @Inject constructor(
        private val newsDao: NewsDao,
        private val newsApi: NewsApi
    ) {
        // Expose data from Local DB as the source of truth
        val newsStream: Flow<List<News>> = newsDao.getAllNews()

        // Sync operation
        suspend fun refreshNews() {
            val remoteNews = newsApi.fetchLatest()
            newsDao.insertAll(remoteNews)
        }
    }
    ```

### 2. Local Persistence (Room)
*   **Usage**: Primary cache and offline storage.
*   **Entities**: Define `@Entity` data classes.
*   **DAOs**: Return `Flow<T>` for observable data.

### 3. Remote Data (Retrofit)
*   **Usage**: Fetching data from backend.
*   **Response**: Use `suspend` functions in interfaces.
*   **Error Handling**: Wrap network calls in `try-catch` blocks or a `Result` wrapper to handle exceptions (NoInternet, 404, etc.) gracefully.

### 4. Synchronization
*   **Read**: "Stale-While-Revalidate". Show local data immediately, trigger a background refresh.
*   **Write**: "Outbox Pattern" (Advanced). Save local change immediately, mark as "unsynced", use `WorkManager` to push changes to server.

### 5. Dependency Injection
*   Bind Repository interfaces to implementations in a Hilt Module.
    ```kotlin
    @Binds
    abstract fun bindNewsRepository(impl: OfflineFirstNewsRepository): NewsRepository
    ```
