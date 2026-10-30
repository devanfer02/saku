---
name: android-security-scan
description: Scan an Android project for security vulnerabilities, CVEs, and OWASP Mobile Top 10 issues. Use this whenever the user asks about security, CVEs, vulnerability scanning, hardening, penetration testing readiness, or security audit of their Android app. Also trigger when the user mentions dependency vulnerabilities, insecure storage, network security, or wants to prepare for a security review or app store submission.
---

> **Saku install note (not upstream).** Saku has no INTERNET permission, and a Gradle task fails the build if one reaches the merged manifest. Saku has no OkHttp, Retrofit, Hilt, Coil, Firebase or WebView. For sections 3, 5 and 8 (network, WebView, auth tokens), confirm the gate still holds instead of reporting a missing network security config, certificate pinning or token storage as findings. Saku keeps all data in a local Room database and exports files only when the user asks; judge storage findings against that. Check CVEs against the versions in `gradle/libs.versions.toml`.

# Android Security & CVE Scanner

## Purpose

Perform a structured security audit of an Android project covering dependency CVEs, code-level vulnerabilities, manifest misconfigurations, and OWASP Mobile Top 10 issues. The output is a prioritized findings report with severity ratings and actionable fixes.

## Audit Workflow

Run each section below in order. For each finding, assign a severity: **CRITICAL**, **HIGH**, **MEDIUM**, or **LOW**. Collect all findings into a final report at the end.

### 1. Dependency CVE Check

Scan `build.gradle.kts` (app and project level), `libs.versions.toml`, and any `buildSrc/` for declared dependencies. For each dependency:

1. Extract the group, artifact, and version
2. Search the web for known CVEs against that exact version using queries like `"<group>:<artifact>:<version>" CVE` or check the NVD/OSV databases
3. Flag any dependency that is significantly behind the latest stable release (major version behind = HIGH, minor = MEDIUM)
4. Pay special attention to networking libraries (OkHttp, Retrofit), serialization (Gson, Moshi, kotlinx.serialization), and image loading (Coil, Glide) — these are frequent CVE targets

**Common high-risk dependencies to check:**
- `com.squareup.okhttp3:okhttp` — TLS/HTTP vulnerabilities
- `com.squareup.retrofit2:retrofit` — deserialization issues
- `com.google.firebase:firebase-bom` — auth/storage misconfigurations
- `com.google.dagger:hilt-android` — code generation edge cases
- `io.coil-kt:coil-compose` — image parsing vulnerabilities
- Any WebView-related dependencies

### 2. AndroidManifest.xml Audit

Read `app/src/main/AndroidManifest.xml` and check:

- **Exported components**: Any `<activity>`, `<service>`, `<receiver>`, or `<provider>` with `android:exported="true"` (or implicitly exported via intent-filters on targetSdk < 31). Each exported component needs justification — flag unexplained exports as HIGH
- **Backup configuration**: `android:allowBackup="true"` without `android:fullBackupContent` restrictions exposes app data. Flag as MEDIUM
- **Debuggable**: `android:debuggable="true"` in release builds = CRITICAL
- **Cleartext traffic**: `android:usesCleartextTraffic="true"` = HIGH. Check for `network_security_config` reference
- **Permissions**: Flag dangerous permissions (`CAMERA`, `READ_CONTACTS`, `ACCESS_FINE_LOCATION`, `READ_EXTERNAL_STORAGE`, `RECORD_AUDIO`) — verify each is actually used in code. Unused dangerous permissions = MEDIUM
- **Deep links / intent filters**: Verify scheme handlers don't expose sensitive routes

### 3. Network Security

Search the codebase for network-related patterns:

- **Network Security Config**: Check `res/xml/network_security_config.xml`. Missing = MEDIUM. If present, check for `cleartextTrafficPermitted="true"` on any domain = HIGH
- **Certificate pinning**: Check OkHttp `CertificatePinner` usage. Absent for production API calls = MEDIUM (informational for educational apps)
- **TLS version**: Search for `SSLContext.getInstance` — anything below TLSv1.2 = HIGH
- **Custom TrustManagers**: Search for `X509TrustManager`, `TrustAllCerts`, `HostnameVerifier`. Custom trust managers that accept all certificates = CRITICAL
- **Hardcoded URLs**: Search for `http://` (not `https://`) in source code. Non-localhost HTTP = HIGH
- **API keys in source**: Search for `BuildConfig.API_KEY`, hardcoded API keys, or tokens in source files (not `BuildConfig`). Hardcoded secrets = CRITICAL. Keys loaded from `BuildConfig` but committed in `gradle.properties` or similar = HIGH

### 4. Data Storage Security

- **SharedPreferences / DataStore**: Search for `getSharedPreferences`, `dataStore`, `PreferencesDataStore`. Check if sensitive data (tokens, passwords, PII) is stored without encryption. Use `EncryptedSharedPreferences` for sensitive data = MEDIUM if missing
- **Internal storage**: Search for `openFileOutput`, `MODE_WORLD_READABLE`, `MODE_WORLD_WRITEABLE`. World-readable/writable files = CRITICAL
- **External storage**: Search for `getExternalFilesDir`, `Environment.getExternalStorageDirectory`. Sensitive data on external storage = HIGH
- **SQL injection**: Search for raw SQL queries with string concatenation. Use parameterized queries = HIGH
- **Room database**: Check for unencrypted databases storing sensitive data = MEDIUM
- **Logging sensitive data**: Search for `Log.d`, `Log.v`, `Log.i` that might log tokens, passwords, or PII = MEDIUM. Check if logging is disabled in release builds

### 5. WebView Security

Search for `WebView`, `WebViewClient`, `WebChromeClient`:

- **JavaScript enabled**: `settings.javaScriptEnabled = true` without input validation on loaded URLs = HIGH
- **File access**: `settings.allowFileAccess`, `settings.allowFileAccessFromFileURLs`, `settings.allowUniversalAccessFromFileURLs` = HIGH if true
- **addJavascriptInterface**: On API < 17 this is exploitable. On modern APIs, verify `@JavascriptInterface` annotations are minimal and don't expose sensitive methods = MEDIUM
- **SSL error handling**: Overriding `onReceivedSslError` to call `handler.proceed()` = CRITICAL
- **Loading untrusted content**: WebView loading user-controlled URLs without validation = HIGH

### 6. Intent & IPC Security

- **Implicit intents with sensitive data**: Sending sensitive data via implicit intents that any app can intercept = HIGH
- **PendingIntent**: Search for `PendingIntent.getActivity`, `PendingIntent.getBroadcast`. Missing `FLAG_IMMUTABLE` or `FLAG_MUTABLE` on API 31+ = MEDIUM. Using `FLAG_MUTABLE` when immutable would work = LOW
- **Content providers**: Check `android:grantUriPermissions`, path permissions. Overly permissive URI grants = HIGH
- **Broadcast receivers**: Unprotected receivers without permission checks = MEDIUM

### 7. Cryptography

- **Weak algorithms**: Search for `MD5`, `SHA1` (for security purposes, not checksums), `DES`, `RC4`, `ECB` mode = HIGH
- **Hardcoded keys/IVs**: Search for `SecretKeySpec` with inline byte arrays, hardcoded IVs = CRITICAL
- **Insecure random**: `java.util.Random` for security-sensitive operations instead of `SecureRandom` = HIGH
- **KeyStore usage**: Check if cryptographic keys are stored in Android KeyStore. Keys stored in files or preferences = HIGH

### 8. Authentication & Session Management

- **Token storage**: Verify auth tokens are stored securely (EncryptedSharedPreferences, Android KeyStore, or EncryptedDataStore). Plain DataStore/SharedPreferences = MEDIUM
- **Token expiry**: Check if token refresh/expiry is handled. No expiry handling = LOW
- **Biometric auth**: If present, check `BiometricPrompt` implementation for proper `CryptoObject` usage
- **Root/emulator detection**: Note absence as informational (not a finding, but worth mentioning for high-security apps)

### 9. Build Configuration Security

- **ProGuard/R8**: Check if `isMinifyEnabled = true` in release builds. Disabled = MEDIUM (code is easily reverse-engineered)
- **Debug variants**: Verify debug-only code paths are gated behind `BuildConfig.DEBUG`
- **Signing config**: Check for debug keystore usage in release builds = HIGH
- **API keys in VCS**: Check if `api.properties`, `local.properties`, `google-services.json` are in `.gitignore`. Committed secrets = CRITICAL

### 10. Compose-Specific Security

- **State exposure**: Sensitive data (passwords, tokens) held in `remember { mutableStateOf() }` visible in layout inspector = LOW
- **Custom text fields**: Password fields should use `visualTransformation = PasswordVisualTransformation()` and `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)`
- **Screenshot prevention**: Sensitive screens should set `FLAG_SECURE` on the window if needed

## Report Format

Present findings as a Markdown table sorted by severity:

```markdown
## Security Audit Report

### Summary
- Critical: X | High: X | Medium: X | Low: X

### Findings

| # | Severity | Category | Finding | File:Line | Recommendation |
|---|----------|----------|---------|-----------|----------------|
| 1 | CRITICAL | ...      | ...     | ...       | ...            |

### Dependency Status

| Dependency | Current | Latest | CVEs Found | Severity |
|------------|---------|--------|------------|----------|
| ...        | ...     | ...    | ...        | ...      |

### Positive Findings
List security measures already in place (gives credit and context).
```

## Tips

- Not every finding needs a fix — some are acceptable risks depending on the app's threat model. An educational quiz app has a different risk profile than a banking app. Note this in findings where relevant.
- Focus on real exploitable issues over theoretical concerns. A hardcoded API key that's already public (e.g., Firebase config) is different from a hardcoded secret key for encryption.
- When suggesting fixes, provide concrete code snippets, not just descriptions.
- If the project uses a code-review-graph, use `semantic_search_nodes_tool` and `query_graph_tool` to efficiently locate security-relevant code instead of scanning every file.
