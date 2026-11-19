# Release notes

When releasing, write user-readable update notes as `vX.Y.Z.md` in this directory; the release workflow uses them verbatim as the Release body via `--notes-file`.

- Match the `versionName` in `app/build.gradle.kts` and the tag; commit and tag first, then trigger the Release workflow manually.
- Group points under new, improved and fixed, written for users: no commit lists, no implementation details.
- The body shows directly on the app's Software update page, so avoid wording like "see the attached file" that only makes sense on GitHub.
