# VoxChat — Website + Android APK project

A **no-login, text-only random-chat prototype** using Node.js for live matching and an Android WebView app that opens a **separately hosted HTTPS VoxChat website**.

**Status:** A local demonstration, not a public-release-ready stranger-chat platform. You can build an **installable debug APK** using GitHub Actions. **There is no prebuilt APK in this ZIP.** The Android app is an HTTPS client; an APK alone does not provide a live chat server.

## What's inside

| Location | What it does |
|---|---|
| `public/index.html`, `styles.css`, `app.js` | Responsive website UI and browser chat client |
| `server.js` | Node.js matchmaking, messaging, basic blocking and demo reports |
| `app/src/main/java/com/vopvox/voxchat/MainActivity.java` | Native Android HTTPS WebView launcher and server-settings page |
| `app/src/main/AndroidManifest.xml` | Android app permissions and settings |
| `app/build.gradle` | Android build config (ID `com.vopvox.voxchat`) |
| `.github/workflows/android-apk.yml` | Builds and uploads a debug APK as an Actions artifact |
| `tests/server.test.js` | Integration tests of the basic chat flow |

## Website: run on your PC

1. Install **Node.js 18 or later**.
2. Open the `voxchat` folder in a terminal.
3. Run `npm start`.
4. Browse to `http://localhost:3000`.
5. Open a second window/tab to test two visitors chatting.

Run tests: `npm test`.

For an Android app to work beyond your PC, first deploy `server.js` plus the `public/` folder to a **reliable HTTPS Node.js hosting service**. GitHub Pages cannot run this Node.js live chat server, and uploading this repository to GitHub does not host a live site.

## Build your APK automatically on GitHub

1. Create a **new empty GitHub repository** named `voxchat` (the linked GitHub account may already have unrelated projects; use a separate repository).
2. Upload **all contents of the `voxchat` folder**, including the hidden `.github/workflows/android-apk.yml` file, to the root of the new repository. Or use Git on your PC:

   ```bash
   cd voxchat
   git init
   git add .
   git commit -m "Initial VoxChat website and Android app"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/voxchat.git
   git push -u origin main
   ```

3. In the GitHub repository, go to **Actions → Build VoxChat Android APK**. The workflow runs on pushes to `main`, or you can choose **Run workflow**.
4. After a successful run, open the run and download the **VoxChat-Android-APK** artifact at the bottom of the run summary.
5. Extract the downloaded artifact ZIP to get `VoxChat-debug.apk`. That is the actual installable Android debug APK, for testing on your own phone.
6. Open the app, enter the HTTPS URL of **your own deployed chat server**, and tap **Open VoxChat**.

**Note:** This build is a debug APK for testing, not a signed Play Store release. The workflow requires GitHub Actions to be enabled. The first build may download Android components. The project has **not** been compiled on this machine (no Android SDK/Gradle installed here); if the workflow fails, review its logs.

## Privacy and safety — critical for public launch

Anonymous chat has serious risks, especially for younger users. **Do not publicly launch this as an unrestricted stranger-chat app** without independent safety review, effective age assurance and access controls, **real-time automated and human moderation**, reviewable persistent reports, bans and abuse protections, emergency escalation, privacy/legal policies, security monitoring, and infrastructure reviews. Basic text filters do not make it safe. Consider an invite-only or verified community with opt-in matching instead.

This prototype does **not** have accounts, camera, microphone, location, file transfers, or link sharing. It creates ephemeral session IDs and keeps them in RAM. Chats are not saved to a database. Reports only exist in server memory and **are not actually reviewed**. Blocks only apply to the current temporary session. Restarting the server clears sessions and reports. A stranger's identity and age are never verified.

## Customize

- Brand and copy: `public/index.html`
- Website styles: `public/styles.css`
- Browser behavior: `public/app.js`
- Matching and demo filters: `server.js`
- Android theme and behavior: `app/src/main/java/com/vopvox/voxchat/MainActivity.java`
- Android name and package ID: `app/src/main/AndroidManifest.xml` and `app/build.gradle`
