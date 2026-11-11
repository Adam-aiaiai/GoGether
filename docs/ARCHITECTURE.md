# Architecture

The app uses a compact MVVM + Repository design. Compose feature screens observe `StateFlow` values from `GogetherViewModel`. The ViewModel delegates mutations to `GogetherRepository`; `FakeGogetherRepository` is the single source of truth for users, events, registrations, and check-ins.

Registration mutations are synchronized and centralized. The creator receives a confirmed registration, free capacity produces a confirmed registration, full capacity produces a FIFO waitlist entry, and cancellation promotes the earliest eligible waitlisted user. Promotion and joining stop after deadline, event start, or event cancellation.

The fake synchronization only protects one in-process repository. It is not a multi-user concurrency solution. Production must enforce invariants in trusted backend transactions and security rules.

Feature packages are intentionally shallow so members can work independently while sharing stable models and repository contracts.
