# 0001. Privacy-First Manual and Preset Tracking over Open Banking

We decided to build SubFlow as a privacy-first, curated preset and manual entry tracker with local-first encrypted cloud sync, instead of integrating automated Open Banking aggregators (like Plaid or Salt Edge).

## Context
SubFlow targets rapid Google Play distribution and high conversion across global markets (US, EU, UK, JP, etc.). Open Banking integrations introduce heavy regulatory burdens, KYC/compliance hurdles, country-specific licensing restrictions, recurring per-user API fees ($0.30-$1.50/active user/month), and elevated security liabilities that delay app store review and erode unit economics for an indie app.

## Decision
1. Users register subscriptions either by choosing from an extensive curated presets catalog (Netflix, Spotify, ChatGPT, Gyms, Utilities, etc.) or entering custom items in seconds.
2. Zero bank credentials or financial account numbers are collected, ensuring instant Google Play policy clearance and privacy trust.
3. Multi-device sync is handled by an encrypted payload synced with our Golang backend.

## Consequences
Users must manually input or edit subscriptions upon first setup, but onboarding friction is minimized via pre-filled presets (logos, billing intervals, default prices). The app achieves 100% gross margin on subscriptions without banking API overhead and zero regional banking availability lockouts.
