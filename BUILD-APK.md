# Build APK with GitHub Actions

1. Upload the contents of this project to the root of a private GitHub repository.
2. Open **Actions**.
3. Select **Build Nexora APK**.
4. Choose **Run workflow** on `main`.
5. Wait for the workflow to finish successfully.
6. Open the successful run and download the **Nexora-Android-debug** artifact.

The workflow intentionally calls `:app:assembleDebug` so Gradle targets the Android application module explicitly.
