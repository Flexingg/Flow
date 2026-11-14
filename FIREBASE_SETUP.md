# Flow: app-wide Firebase + Google Sign-In setup

Flow uses **one shared Firebase project** baked into the APK. Users just tap *Sign in with Google*;
nobody configures Firebase inside the app. Each user's data lives at `users/{their Firebase UID}`
and `firestore.rules` stops anyone reading anyone else's data.

You need three things: a Firebase project, a stable signing key, and four GitHub Secrets.

## 1. Create a stable release signing key (once, keep it forever)

Google Sign-In only works for APKs whose SHA-1 fingerprint is registered in Firebase, so every
build must be signed with the same key. On your computer (needs a JDK; Android Studio ships one):

```bash
keytool -genkeypair -v -keystore flow-release.jks -alias flow \
  -keyalg RSA -keysize 2048 -validity 10000
```

Pick a password when prompted. **Back up `flow-release.jks` and the password**: lose them and you
can never ship an update that installs over the old app.

Print the fingerprints (you need SHA-1 and SHA-256 for step 2):

```bash
keytool -list -v -keystore flow-release.jks -alias flow
```

## 2. Create the Firebase project

1. <https://console.firebase.google.com> → **Add project** (Google Analytics optional).
2. **Add app → Android**. Package name: `com.flow.todo`. Paste the **SHA-1** from step 1, and
   afterwards add the **SHA-256** too under *Project settings → Your apps → Add fingerprint*.
3. **Build → Authentication → Get started → Sign-in method**: enable **Google**
   (and **Email/Password** if you want the email option).
4. **Build → Firestore Database → Create database** (production mode, any region).
   Open the **Rules** tab, paste the contents of [`firestore.rules`](./firestore.rules), **Publish**.
5. **Project settings → Your apps → Android app → Download `google-services.json`.**
   Download it *after* enabling Google sign-in, so it contains the OAuth Web client ID
   (look for `"client_type": 3` inside it).

## 3. Add GitHub Secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_B64` | `base64 -w0 flow-release.jks` (macOS: `base64 -i flow-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `flow` |
| `RELEASE_KEY_PASSWORD` | the key password (same as the keystore one unless you chose another) |
| `GOOGLE_SERVICES_JSON_B64` | `base64 -w0 google-services.json` (*optional*: see below) |

`google-services.json` is not a secret (its API key is restricted to your package + SHA-1), so
you can commit it as `app/google-services.json` instead of using the secret.

## 4. Build and install

Push to `main` (or run the workflow manually). The workflow restores the keystore and Firebase config
and builds a signed APK. The first APK signed with the new key **cannot install over an older
debug-signed build**: sync or export anything you need, uninstall, then install. From then on,
updates install in place.

## Troubleshooting

- **Code 10 (DEVELOPER_ERROR)**: the APK's SHA-1 isn't registered in Firebase, or the build had no
  `google-services.json`. Check the workflow log for "Release keystore restored from secret".
- **"Firebase sign-in failed"** after picking an account: the Google provider isn't enabled in
  Authentication, or `google-services.json` was downloaded before enabling it.
- **PERMISSION_DENIED in sync**: Firestore rules not published.
