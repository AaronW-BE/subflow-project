---
id: subflow-localization-preferences
type: module-design
status: draft
title: Language and currency preference boundaries
tags: [localization, preferences]
---

## Responsibility

Language preferences choose the interface and notification language independently of the home currency. An explicit language choice survives restarts; following the system selects the first supported language in device preference order, falling back to English when none matches.

Dutch uses Netherlands wording and informal je/jij. Both Netherlands and Belgian Dutch match the shared Dutch translation; the explicit Dutch locale uses nl-NL date and number conventions. This does not force EUR: first-install currency selection uses device regions, while an existing saved currency always wins.

## Boundary

A supported language must be available consistently in the in-app picker, locale resolution, Android per-app language settings and release resources. Missing translations must not silently turn a listed supported language into English. Every translatable string and applicable plural form preserves its formatting arguments; provider-mandated nontranslatable attribution remains inherited.

Store copy describes uploaded capabilities, not merely the working tree. A new language's listing stays a draft until an upload includes its resources; only then are every listing's language claims and release notes updated. Adding a language alone does not increment the app version or change currency preferences.
