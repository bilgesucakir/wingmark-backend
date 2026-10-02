# App Compliance Checklist: Backend

Read this together with `compliance-checklist.md`, which holds the rules, status values, and report format. This file covers only the **backend** side of each item. If something below needs a UI change, don't build it. Note it as `Needs human – FE` with the details the frontend will need (endpoint, payload, response).

Items that are FE-only are listed at the end, marked `N/A (FE)`.

---

## Legal pages

### C-01 Privacy policy
- [ ] Produce a factual **data inventory** to feed the policy: every personal data field stored (table or collection, field), why it's stored, how long it's kept, and which third parties receive it. The policy must match this.
- [ ] If policy pages are served or versioned by the BE, keep the version identifier available to the FE.

### C-02 Terms of service
- [ ] Store terms acceptance per user: `terms_version`, `accepted_at`, and optionally IP or user agent.
- [ ] Reject signup if acceptance is missing. Don't trust the FE alone.
- [ ] Support asking users to accept again when the terms version changes (a flag or check on login).

### C-03 Refund policy
- [ ] If payments exist, check the refund logic (payment provider config, webhooks, admin tools) matches what the policy promises: time window, full or partial refunds, how subscriptions are handled.
- [ ] Flag any mismatch rather than changing business terms.

### C-04 Cookie policy
- [ ] List every cookie the server sets (session, CSRF, auth, load balancer, A/B testing), with name, purpose, duration, and flags (`HttpOnly`, `Secure`, `SameSite`). Send this to the FE or legal for the cookie table.

## Consent

### C-05 Cookie consent banner
- [ ] The server doesn't set non-essential cookies (analytics, marketing) before consent.
- [ ] If server-side tracking exists (e.g. server-side GA, Meta Conversions API), it respects the user's consent state.
- [ ] Optionally store consent server-side for logged-in users.

### C-06 Form consents
- [ ] Persist each consent as a record: user, consent type (terms, privacy, marketing, ...), granted or withdrawn, version, timestamp.
- [ ] Marketing consent defaults to `false`. Withdrawing it is possible and takes effect immediately.
- [ ] Validate consent server-side. Never assume consent from a missing field.

## Data

### C-07 No unnecessary data collected
- [ ] Review the schema or models. Flag or remove PII fields that are never read or have no clear purpose.
- [ ] Logs don't contain passwords, tokens, full card numbers, or unnecessary PII. Mask or redact them.
- [ ] Retention: old or inactive data, logs, and backups have a defined retention period and cleanup jobs. Flag if none exist. Don't invent the period.
- [ ] API responses don't return more user fields than the client needs (no over-fetching of other users' PII).

### C-08 Third-party SDK audit
- [ ] List every server-side third-party integration: payments, email or SMS, analytics, error tracking, AI or LLM APIs, CRM, storage or CDN, auth providers.
- [ ] For each one, record: name, purpose, which user data is sent, region if known, and whether it's still used.
- [ ] Remove unused SDKs and dependencies. Minimize the data sent (e.g. no full user objects to error tracking).
- [ ] Flag vendors that need a data processing agreement (DPA) for a human to confirm.

### C-17 Age consent / children's data
- [ ] If applicable, store age or date of birth, or an `is_minor` flag, and enforce the age rule server-side.
- [ ] Block signup or processing for under-age users, or require recorded parental consent.
- [ ] Exclude minors from marketing, profiling, and ad or analytics data sent to third parties.

### C-20 Data deletion requests
- [ ] There's an authenticated deletion endpoint (and a way to verify requests that come by email).
- [ ] Deletion cascades to all related records, uploaded files and storage objects, search indexes, and caches. Anonymize anything that must be kept for legal or accounting reasons.
- [ ] Deletion propagates to third parties where possible (email provider, CRM, analytics user-deletion APIs, payment customer). List what can't be automated.
- [ ] Log the request and when it was completed, without keeping the deleted data. Send a confirmation email.
- [ ] Note how backups are handled (e.g. expire naturally within N days). Flag if unknown.
- [ ] Optional but recommended: a data export endpoint (right of access and portability).

## Honest UX and commerce

### C-09 No dark patterns
- [ ] A cancel or downgrade API exists and needs no more steps than subscribing (no "call us to cancel" when signup is online).
- [ ] Any scarcity or urgency data the API returns ("only 3 left", timers) is real, not randomized or hardcoded.

### C-10 No hidden fees
- [ ] Price calculation (tax, fees, shipping) has a single source of truth. The amount charged equals the amount quoted to the FE.
- [ ] No fees are added server-side at the final step that weren't returned in earlier quotes.

### C-11 No fake reviews
- [ ] Check seed scripts, fixtures, and migrations for fake reviews, ratings, or testimonials that reach production.
- [ ] Review and rating endpoints return only real, user-submitted data. Aggregates (average rating, counts) are computed, not hardcoded.
- [ ] Ask before deleting any review data from a real database.

### C-12 No unsupported claims
- [ ] Check BE-owned copy: email templates, push notifications, API-served marketing content, and SEO metadata. Flag any unsupported claims, same as the FE.

### C-16 Business details
- [ ] Transactional and marketing email templates include the legal business name and physical address in the footer.
- [ ] Invoices and receipts include the required business details (legal name, address, VAT or registration number). Use placeholders if unknown.

## Email and assets

### C-18 Unsubscribe link in emails
- [ ] Every marketing email template has a visible unsubscribe link.
- [ ] Add `List-Unsubscribe` and `List-Unsubscribe-Post: List-Unsubscribe=One-Click` headers (RFC 8058) to bulk or marketing mail.
- [ ] The unsubscribe endpoint works without login (signed token), takes effect immediately, and updates the suppression list (including at the email provider).
- [ ] Transactional emails (receipts, password reset) don't carry marketing content that would need consent.

### C-19 Font and image licenses
- [ ] Check assets the BE serves or embeds: email template images or fonts, generated images or PDFs, seed media. Record their source and license, and flag unknowns.

---

## Not BE-owned
- C-13 Image alt text: `N/A (FE)`, except alt attributes on images in email templates, which the BE should set.
- C-14 Color contrast: `N/A (FE)`, except email template colors, which the BE should check.
- C-15 Keyboard navigation: `N/A (FE)`.
