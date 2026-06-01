# FocusLock Release Signing

Release signing is configured through environment variables so keystore secrets never enter source control.

## Required Variables

```powershell
$env:FOCUSLOCK_RELEASE_STORE_FILE = "C:\secure\focuslock-release.jks"
$env:FOCUSLOCK_RELEASE_STORE_PASSWORD = "<store password>"
$env:FOCUSLOCK_RELEASE_KEY_ALIAS = "<key alias>"
$env:FOCUSLOCK_RELEASE_KEY_PASSWORD = "<key password>"
```

## Build

```powershell
.\gradlew.bat clean assembleRelease
```

If any variable is absent, Gradle builds an unsigned release artifact instead of embedding credentials in the project.

## Security Rules

- Store the keystore outside the repository.
- Never commit passwords, keystore files, or local signing properties.
- Back up the production keystore in an access-controlled secrets vault.

