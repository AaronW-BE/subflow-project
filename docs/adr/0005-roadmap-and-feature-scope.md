# 0005. Roadmap and Feature Scope Definition

We finalized the phased implementation roadmap and feature boundaries for SubFlow across the Android client, Golang backend, and Admin Console.

## Context
A commercial app targeting rapid distribution and monetization on Google Play must prevent scope creep while delivering exceptional visual polish and enterprise-grade reliability in its core loop.

## Decision
1. **Phase 1 (Design & Specifications)**: Build Stitch design system, high-fidelity Apple-style screen specifications, design reviews, and token contracts.
2. **Phase 2 (Golang Backend & Admin Console)**: High-performance Go RESTful API with Google OAuth verification, preset service catalog, exchange rates, sync engine, and an embedded modern React Admin Console for BI analytics and catalog management.
3. **Phase 3 (Android Native Client)**: Kotlin + Jetpack Compose with Room local-first storage, Google Credential Manager, Google Play Billing 7.x, AdMob, WorkManager renewal notifications, 6-language i18n, and Apple HIG dark/light theming.

## Consequences
Defines clear integration seams between client, backend, and admin, enabling decoupled development and systematic verification.
