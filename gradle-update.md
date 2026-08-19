# Gradle update plan

This document captures a step-by-step plan to upgrade this repository from Gradle `5.2.1` to the newest Gradle release that still runs on Java 11.

## Goal

- Move the build from Gradle `5.2.1` to the latest Gradle `8.x` release available at implementation time.
- Adopt Java 11 as the build/runtime baseline.
- Make the migration in small, reviewable commits.
- Keep the build green after each step when practical.

## Why target Gradle 8.x?

Gradle 9.x requires a newer Java runtime than Java 11 to run. Since this repository can move to Java 11, the appropriate target is the latest Gradle 8.x release.

## Current state observed in this repository

- The wrapper currently points to Gradle `5.2.1` in `gradle/wrapper/gradle-wrapper.properties`.
- CI now uses Java 11 in `.github/workflows/build.yml`.
- `buildSrc/build.gradle` still uses deprecated dependency configurations such as `compile` and `testCompile`.
- `buildSrc` custom tasks still use the removed incremental API based on `IncrementalTaskInputs`.
- `build.gradle` still references `jcenter()`.
- `tools/copy_source_from_jasper_service.sh` auto-generates dependency entries using the old `compile` syntax, but this can be ignored

## Implementation status

### Completed

- Phase 1 has been implemented.
- `.github/workflows/build.yml` was updated to use Java 11.
- `README.md` now documents the Java 11 baseline and the verification commands.
- The build was validated with SDKMAN Java `11.0.20-tem` using `./gradlew --version` and `./gradlew check`.
- Phase 2 has been implemented.
- `buildSrc/build.gradle` now uses `implementation` and `testImplementation` instead of `compile` and `testCompile`.
- `tools/copy_source_from_jasper_service.sh` no longer rewrites dependency declarations in `buildSrc/build.gradle` and is now limited to source synchronization.
- The build was revalidated with SDKMAN Java `11.0.20-tem` using `./gradlew check`.
- Phase 3 has been implemented.
- `buildSrc/build.gradle` now uses lazy task lookup for the Groovy/Scala wiring instead of direct eager task references.
- The Groovy compile classpath still includes Scala outputs, using a cross-version lookup that works with the current Gradle 5 wrapper and prepares for newer Gradle versions.
- The build was revalidated with SDKMAN Java `11.0.20-tem` using `./gradlew check --rerun-tasks`.

### Decisions made

- Java 11 is the baseline runtime for the migration.
- Java toolchains are intentionally deferred until a later phase because the repository still uses Gradle `5.2.1`.

### Next phase

- Phase 4: replace removed incremental task APIs.

## Migration strategy

The migration should be done in phases. The key principle is:

1. modernize build logic first,
2. then upgrade the wrapper,
3. then fix remaining breakages,
4. then document and clean up.

---

## Phase 1: Establish Java 11 baseline

### Objectives

- Update CI and local expectations to Java 11.
- Avoid mixing a Gradle migration with an old Java baseline.

### Tasks

- [x] Update `.github/workflows/build.yml` to use Java 11.
- [x] Decide whether to add explicit Java toolchains in `build.gradle` and `buildSrc/build.gradle`.
- [x] Update `README.md` to mention the Java 11 requirement for contributors.

### Expected commit

- `build: switch CI and docs to Java 11`

### Validation

- Run `./gradlew --version`
- Run `./gradlew check`

### Status

- Completed.
- Validated with SDKMAN Java `11.0.20-tem`.

---

## Phase 2: Modernize `buildSrc` dependency declarations

### Objectives

- Remove dependency configurations that are not supported by modern Gradle.
- Keep generated dependency blocks compatible with the chosen style.

### Tasks

- [x] Replace `compile` with `implementation` or `api` where appropriate in `buildSrc/build.gradle`.
- [x] Replace `testCompile` with `testImplementation`.
- [x] Review whether any dependencies in `buildSrc` must remain exposed to consumers; prefer `implementation` unless exposure is required.
- [x] Update `tools/copy_source_from_jasper_service.sh` by removing the dependency-related parts, as dependencies in this repo are managed without this script.
- [x] Re-run tests after the change.

### Notes

`buildSrc` is compiled as an internal build. In most cases, `implementation` is the right replacement for old `compile` usage there.

### Expected commit

- `build: replace deprecated buildSrc dependency configurations`

### Validation

- Run `./gradlew check`

### Status

- Completed.
- Validated with SDKMAN Java `11.0.20-tem` using `./gradlew check`.

---

## Phase 3: Modernize mixed Scala/Groovy build wiring

### Objectives

- Replace task wiring and properties that are deprecated or removed in newer Gradle versions.

### Tasks

- [x] Update `buildSrc/build.gradle` to avoid old task property access such as `compileScala.destinationDir`.
- [x] Replace direct task property reads with modern provider-based access where needed.
- [x] Verify that Groovy compilation still sees Scala outputs correctly.
- [x] Keep the change minimal and avoid unrelated refactoring.

### Expected commit

- `build: modernize buildSrc Scala and Groovy task wiring`

### Validation

- Run `./gradlew buildSrc:build` if applicable, otherwise `./gradlew check`

### Status

- Completed.
- Validated with SDKMAN Java `11.0.20-tem` using `./gradlew check --rerun-tasks`.

---

## Phase 4: Replace removed incremental task APIs

### Objectives

- Update custom task implementations in `buildSrc` so they work with Gradle 8.x.

### Affected files

- `buildSrc/src/main/groovy/com/riege/scope/gradle/tasks/JasperReportsCompile.groovy`
- `buildSrc/src/main/groovy/com/riege/scope/gradle/tasks/RenderFormsTask.groovy`
- `buildSrc/src/main/groovy/com/riege/scope/gradle/forms/FormRenderDataCache.groovy`
- potentially related tests in `buildSrc/src/test/groovy/...`

### Tasks

- [ ] Remove usage of `IncrementalTaskInputs`.
- [ ] Replace old incremental handling with modern input tracking APIs supported by Gradle 8.x.
- [ ] Adjust cache invalidation code to use the updated change model.
- [ ] Update or extend tests that cover the new behavior.

### Notes

This is likely the most invasive part of the migration. It should be isolated in its own commit.

### Expected commit

- `build: migrate custom tasks off removed incremental APIs`

### Validation

- Run `./gradlew test`
- Run `./gradlew check`

---

## Phase 5: Clean up repositories and remaining deprecated build usage

### Objectives

- Remove repository and DSL usage that may cause failures or warnings on newer Gradle versions.

### Tasks

- [ ] Remove `jcenter()` from `build.gradle` if all dependencies resolve without it.
- [ ] Keep the Jaspersoft and JitPack repositories only if they are still needed.
- [ ] Check for any remaining deprecated Gradle DSL usage in `build.gradle` and `buildSrc/build.gradle`.

### Expected commit

- `build: remove legacy repository and DSL usage`

### Validation

- Run `./gradlew dependencies` for relevant configurations if resolution becomes unclear.
- Run `./gradlew check`

---

## Phase 6: Upgrade the Gradle wrapper

### Objectives

- Move the wrapper to the final target version once the build logic is compatible.

### Tasks

- [ ] Update `gradle/wrapper/gradle-wrapper.properties` to the latest Gradle 8.x release available at implementation time.
- [ ] Regenerate wrapper artifacts using the wrapper task.
- [ ] Verify `gradlew`, `gradlew.bat`, and wrapper JAR changes are correct.

### Notes

This should happen after the compatibility work above, not before.

### Expected commit

- `build: upgrade Gradle wrapper to latest Java 11 compatible release`

### Validation

- Run `./gradlew --version`
- Run `./gradlew check`

---

## Phase 7: Stabilization and follow-up fixes

### Objectives

- Catch anything that only appears once the final wrapper is in place.

### Tasks

- [ ] Fix residual deprecations or task validation issues reported by Gradle 8.x.
- [ ] Review task inputs/outputs for stricter validation rules.
- [ ] Confirm tests in `buildSrc` still pass.
- [ ] Confirm the GitHub Actions workflow passes with Java 11 and the new Gradle wrapper.

### Expected commit

- `build: fix remaining Gradle 8 compatibility issues`

### Validation

- Run `./gradlew clean check`

---

## Suggested commit order

1. `build: switch CI and docs to Java 11`
2. `build: replace deprecated buildSrc dependency configurations`
3. `build: modernize buildSrc Scala and Groovy task wiring`
4. `build: migrate custom tasks off removed incremental APIs`
5. `build: remove legacy repository and DSL usage`
6. `build: upgrade Gradle wrapper to latest Java 11 compatible release`
7. `build: fix remaining Gradle 8 compatibility issues`

## Risks and likely trouble spots

### 1. Custom task migration

The biggest technical risk is the custom task code in `buildSrc`. Old incremental task APIs were removed in newer Gradle versions, so these classes will need real code changes, not just syntax updates.

### 2. `buildSrc` dependency exposure

Switching from `compile` to `implementation` can expose missing classpath assumptions. If something breaks, a few dependencies may need `api`, but that should be the exception.

### 3. Repository resolution

Removing `jcenter()` may surface dependencies that are only available from legacy repositories. That should be checked carefully before final cleanup.

### 4. Wrapper timing

If the wrapper is upgraded too early, the build may fail before the compatibility fixes can be applied cleanly.

## Definition of done

The migration is complete when all of the following are true:

- [ ] CI uses Java 11.
- [x] CI uses Java 11.
- [ ] The wrapper uses the latest Gradle 8.x release.
- [ ] `./gradlew --version` succeeds with Java 11.
- [ ] `./gradlew clean check` succeeds.
- [ ] No required build logic still depends on removed Gradle 5-era APIs.
- [ ] Repository and dependency generation scripts are aligned with the modernized build.

