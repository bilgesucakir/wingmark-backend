# App Compliance Checklist (General)

This is the shared checklist for every agent working on the app. It is always paired with one role file:

- **Frontend agent:** this file + `compliance-checklist-fe.md`
- **Backend agent:** this file + `compliance-checklist-be.md`

This file says **what** must be true. The role files say **how to check and fix it** on your side. Item IDs (`C-01` … `C-20`) are the same in all three files, so FE and BE reports line up.

---

## How to work through it

1. **Apply where applicable.** Not every item fits every app (e.g. no payments means no refund policy). If an item doesn't apply, mark it `N/A` and give a one-line reason. Never skip silently.
2. **Look at the actual code.** Base every status on what you found in the repo, not on assumptions. Cite file paths.
3. **Fix what you can and flag what you can't.** Make code changes inside your own area (FE or BE). Anything outside it, or anything that needs a human decision, goes in the report as `Needs human`.
4. **Don't invent facts.** Never make up business details (company name, address, registration number, contact email), prices, refund terms, or retention periods. Use clear placeholders like `[[COMPANY_LEGAL_NAME]]` and flag them.
5. **Legal text is a draft.** You may draft policy pages, but mark them `DRAFT – requires legal review` and list them in the report. You are not giving legal advice.
6. **Confirm before destructive changes.** Ask before deleting user data, real reviews, production records, or third-party integrations someone may depend on.

### Status values

| Status | Meaning |
|---|---|
| `Pass` | Already compliant; evidence cited |
| `Fixed` | Was non-compliant; you changed it (list files) |
| `Needs human` | Needs a decision, content, legal review, or work outside your area |
| `N/A` | Doesn't apply to this app (reason required) |

---

## Ownership at a glance

| ID | Item | FE | BE | Non-code (human/legal) |
|---|---|:-:|:-:|:-:|
| C-01 | Privacy policy | ● | ● | ● |
| C-02 | Terms of service | ● | ● | ● |
| C-03 | Refund policy | ● | ● | ● |
| C-04 | Cookie policy | ● | ○ | ● |
| C-05 | Cookie consent banner | ● | ○ | |
| C-06 | Form consents | ● | ● | |
| C-07 | No unnecessary data collected | ○ | ● | |
| C-08 | Third-party SDK audit | ● | ● | ○ |
| C-09 | No dark patterns | ● | ○ | |
| C-10 | No hidden fees | ● | ● | |
| C-11 | No fake reviews | ○ | ● | ○ |
| C-12 | No unsupported claims | ● | ○ | ● |
| C-13 | Image alt text | ● | | |
| C-14 | Color contrast | ● | | |
| C-15 | Keyboard navigation | ● | | |
| C-16 | Business details | ● | ○ | ● |
| C-17 | Age consent / children's data | ● | ● | ○ |
| C-18 | Unsubscribe link in emails | ○ | ● | |
| C-19 | Font and image licenses | ● | ○ | ○ |
| C-20 | Data deletion requests | ● | ● | ○ |

● = main owner  ○ = supporting role

---

## The checklist

### Legal pages

- [ ] **C-01 Privacy policy.** A privacy policy exists, can be reached from every page and at signup, and matches what the app actually collects, why, who it's shared with, how long it's kept, and how users exercise their rights.
- [ ] **C-02 Terms of service.** Terms exist, can be reached from every page, and users accept them at signup. The app records which version they accepted.
- [ ] **C-03 Refund policy.** If the app takes payments: a refund policy exists, appears before purchase, and matches how refunds actually work.
- [ ] **C-04 Cookie policy.** A cookie policy lists every cookie and similar storage the app uses (name, purpose, duration, first or third party).

### Consent

- [ ] **C-05 Cookie consent banner.** Non-essential cookies, scripts, and trackers load only after the user opts in. Accept and Reject are equally easy. The user can change their choice later.
- [ ] **C-06 Form consents.** Consent checkboxes start unticked. Marketing consent is separate from accepting the terms. Consent is recorded (what, when, which version) on the server.

### Data

- [ ] **C-07 No unnecessary data collected.** Every field collected, stored, logged, or sent to analytics has a clear purpose. Remove anything without one.
- [ ] **C-08 Third-party SDK audit.** There is a list of every third-party SDK, script, and API that receives user data, with what each one receives and why. Remove unused ones. Load consent-dependent ones only after consent.
- [ ] **C-17 Age consent / children's data.** If the app could attract minors: there is an age check, under-age users are blocked or need parental consent, and children's data isn't used for profiling or ads.
- [ ] **C-20 Data deletion requests.** Users can request deletion of their account and data. The request is actually carried out across the database, storage, and third parties, and the user gets confirmation.

### Honest UX and commerce

- [ ] **C-09 No dark patterns.** No pre-ticked boxes, confirmshaming, fake urgency or scarcity, hidden cancel or unsubscribe paths, nagging, or trick wording. Cancelling is as easy as signing up.
- [ ] **C-10 No hidden fees.** The full price, including taxes and fees, appears before the final step. Nothing is added at checkout that wasn't shown earlier.
- [ ] **C-11 No fake reviews.** Every review, rating, testimonial, and "X users" count comes from real data. Remove hardcoded or seeded fake ones.
- [ ] **C-12 No unsupported claims.** Marketing and UI copy contains no claims the business can't back up ("#1", "guaranteed", "clinically proven", "100% secure", invented stats). Flag them for a human to verify or reword.
- [ ] **C-16 Business details.** The legal business name, address, contact email, and registration or VAT number (where required) are visible in the app, usually in the footer or an About or Imprint page, and in transactional emails.

### Accessibility

- [ ] **C-13 Image alt text.** Every meaningful image has descriptive alt text. Decorative images use `alt=""`.
- [ ] **C-14 Color contrast.** Text and UI controls meet WCAG 2.1 AA contrast: 4.5:1 for body text, 3:1 for large text and UI components.
- [ ] **C-15 Keyboard navigation.** Everything works with the keyboard alone, with a visible focus indicator, logical tab order, and no keyboard traps.

### Email and assets

- [ ] **C-18 Unsubscribe link in emails.** Every marketing email has a working one-click unsubscribe, and it takes effect immediately.
- [ ] **C-19 Font and image licenses.** Every font, image, icon, and illustration has a license that allows this use. Keep a record of the source and license.

---

## Report format

When finished, produce a report in this shape (one row per item, including `N/A`):

```markdown
## Compliance report: <FE|BE>, <date>

| ID | Item | Status | Evidence / changes | Follow-up |
|---|---|---|---|---|
| C-01 | Privacy policy | Fixed | Added footer link in `src/components/Footer.tsx` | Policy text needs legal review |
| C-03 | Refund policy | N/A | App has no payments | |
| ... | | | | |

### Needs human
- C-16: company address and VAT number unknown; placeholders in `...`
- ...

### Placeholders added
- `[[COMPANY_LEGAL_NAME]]` in `...`
```
