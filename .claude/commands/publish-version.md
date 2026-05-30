Prepare the plugin for a new marketplace release by updating the changelog and bumping the version.

Steps:

1. Read `gradle.properties` and extract the current `version` value (e.g. `0.0.5`).

2. Compute today's date in `YYYY-MM-DD` format.

3. Update `CHANGELOG.md`:
   - Replace the `## [Unreleased]` heading with `## [<current_version>] - <today>` (keep the content below it intact).
   - Insert a new empty `## [Unreleased]` section immediately above the newly versioned block, separated by a blank line.

4. Increment the patch component of the version in `gradle.properties` (e.g. `0.0.5` → `0.0.6`), keeping the `version = ` key intact.

5. Show a brief summary of what changed (old version → new version, changelog section promoted).
