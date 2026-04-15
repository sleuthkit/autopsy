# Developer Documentation (Claude Code)

This folder contains documentation intended to help Claude Code assist with development on this codebase. Files here capture non-obvious debugging procedures, architecture notes, and tribal knowledge that would otherwise have to be re-derived each session.

These files are meant to be read by both humans and Claude Code. Keep them concise and factual.

## Coding Standards

### Use Optional instead of null for absent returns

Methods that may not find a result must return `Optional<T>` rather than `null`. Use `Optional.empty()` for the absent case and `Optional.of(value)` for a result. Call sites should use `.isPresent()` / `.get()`, `.orElse()`, or `.map()` as appropriate.

```java
// correct
static Optional<AutopsyContentProvider> findInstalledProvider(String createdName) { ... }

// wrong
static AutopsyContentProvider findInstalledProvider(String createdName) { ... } // don't return null
```

This applies to all new helper and utility methods. It does not apply to framework callbacks or interface overrides whose contract requires returning null (e.g., NetBeans/Swing APIs).

## Refactoring

When moving files and packages, use 'git mv' instead of deleting a file and creating a new file. 

## Error Handling

Do not ignore or swallow errors. Catch them and at a minimum log them. If they are in response to a user request, ensure they get basic feedback that an error occurred. 
