# Testing

Run JVM tests and build the debug APK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

In a restricted CI sandbox that cannot lock a debug keystore, set `GOTHER_UNSIGNED_DEBUG=true` to verify unsigned APK packaging. Normal Android Studio/debug builds leave this unset and use standard debug signing.

`FakeGogetherRepositoryTest` covers event creation, field validation, normal registration, full-event waitlisting, cancellation, FIFO promotion, duplicate prevention, cancelled-event rejection, deadline rejection, and disabled promotion after the deadline.

Before merging, manually verify login, all bottom destinations, event details, create-to-Explore propagation, joined/waitlisted/created tabs, organizer management, user switching, and demo check-in on a common phone-size emulator. Future Firebase work requires emulator-suite integration and Security Rules tests.
