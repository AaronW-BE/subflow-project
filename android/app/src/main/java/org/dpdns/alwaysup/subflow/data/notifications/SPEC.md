---
id: subflow-trial-final-day-reminders
type: module-design
status: draft
title: Trial final-day cancellation checks
references: [subflow-localization-preferences]
tags: [trials, notifications, permissions]
---

## Responsibility

Final-day cancellation checks help users decide about automatic trial conversion; they neither cancel a provider's service nor guarantee prevention of charges. They are free, independent of ordinary advance reminders, and globally opt-in (default off). The shared time defaults to 09:00 in the current device timezone; the user can change it. Exact timing is a separate opt-in, default off.

Only active, nondeleted, pending, auto-converting trials with individual reminders enabled and no matching keep decision qualify. Checks are due on the end date at or after the selected local time. First enabling or editing after that time permits same-day catch-up; past dates never receive a cancellation check. Timezone changes recalculate local targets, including DST resolution by the platform's zone rules. Ordinary positive-day advance reminders retain their own policy and deduplication.

## Decisions and persistence

A keep decision is bound to the current trial end date. Keeping or undoing changes only that decision: it does not convert the trial, change zero-cost trial accounting, or suppress advance reminders. Ordinary edits preserve it when the pending trial and end date are unchanged; changing the date or extending invalidates it. Recording provider cancellation requires confirmation, then updates and pauses only the local record. Existing post-expiry conversion and extension remain explicit user actions.

Room v3 adds the keep date using a non-destructive migration; older rows default to undecided. JSON backup v3 preserves it, and missing/null legacy values normalize to empty. Reminder preferences and delivery markers live separately in local SharedPreferences, not the subscription JSON backup. Cloud DTOs do not carry keep intent: release backend sync remains disabled, and this contract does not claim cross-device preservation.

## Scheduling boundary

The pure decision policy owns eligibility and date arithmetic. The scheduler reads current persisted subscriptions and preferences, serializes all scans with one process-local mutex, submits notifications, and replaces a single nearest-future AlarmManager alarm. The receiver performs that check directly with a bounded asynchronous lifetime rather than delegating precision to WorkManager. Foreground subscription/preference observation, resume, permission grants, boot, package replacement, clock/timezone broadcasts and the existing daily Worker reconcile the same state.

Exact delivery uses setExactAndAllowWhileIdle only when selected and accessible; otherwise setAndAllowWhileIdle provides ordinary timing. Only SCHEDULE_EXACT_ALARM is declared. It is user-controlled special access, not a claim that SubFlow qualifies for restricted USE_EXACT_ALARM. Access is checked again when scheduling, with a SecurityException race falling back to ordinary scheduling. There is no revocation broadcast dependency. A stopped process cannot actively react to revoked access until another supported entry point runs.

## Delivery and security

Each subscription/end-date pair has a persistent sent marker and stable notification tag. Editing, keep/undo and repeated scans must not normally alert twice for that pair. App notification permission and channel restrictions are independent of exact-alarm access. Blocked submissions remain unmarked and can be retried on later reconciliation. Notification submission and marker storage are not an atomic transaction across process death; stable tags and only-alert-once reduce duplicate effects but do not promise exactly-once delivery after crashes or storage failure.

The receiver is non-exported, action-allowlisted, and uses explicit immutable PendingIntents. Notifications open the matching subscription detail only. There are no nested-intent forwarding, one-tap cancellation, full-screen intents, persistent ringing or DND bypasses. A dedicated default-importance channel remains under user control; notification text follows subflow-localization-preferences.

Doze, force-stop, reboot, OEM restrictions and notification settings can prevent or delay delivery. Dates are not provider charge timestamps. Both the UI and notification copy must avoid implying guaranteed timing, successful provider cancellation or absence of prior charges. Android special-access grant and receiver behavior require device validation in addition to host-side tests.
