# Publishing Komirka on GitHub

The root `README.md` introduces the app, describes its features, and includes installation and usage instructions. GitHub displays a root README on the repository page automatically. See [GitHub's README documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes).

## Repository description and topics

Open the repository's **About** settings and use a description such as:

> Offline Android app for creating CSV tables on your phone. Custom columns, guided data entry, saved drafts, value history, and CSV/JSON export.

Add topics that describe the actual app:

```text
android
csv
csv-editor
table-maker
data-entry
data-collection
offline
json-export
sqlite
java
```

The description and topics are GitHub repository settings; adding this file does not apply them. [GitHub's topic guide](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) explains where to edit them.

The README uses phrases such as “CSV table maker for Android phones” and “create a CSV table on your phone” in headings and explanatory text. They describe what a visitor can do with the app. Avoid repeating the same search phrase or adding hidden keywords. Clear headings and useful instructions can help search engines understand the repository, but they cannot guarantee indexing or a position above other projects. See [Google's SEO starter guide](https://developers.google.com/search/docs/fundamentals/seo-starter-guide).

## Publish the source

1. Run the build, unit tests, and Android Lint using the commands in the README.
2. Copy the tested APK from `app/build/outputs/apk/debug/app-debug.apk` to `Komirka.apk` in the repository root. This is the file linked at the top of the README.
3. Review `git status` and `git diff`. Include `Komirka.apk`, the README, and the corresponding source changes in the same commit. Keep local SDKs, caches, `local.properties`, signing keys, other generated APKs, and test reports out of the commit. The `.gitignore` makes an exception for the root `Komirka.apk`.
4. Commit the reviewed changes and push them to the branch you want to publish. The root README must be on the default branch to appear on the repository's main page.
5. Apply the description and topics in the repository's About settings.

GitHub already provides source downloads for commits and tags. There is no need to maintain a second source ZIP inside the repository.

## Optional release builds

The repository already includes an installable APK at `Komirka.apk`. A separate GitHub release is optional. To distribute a release variant signed with your own release key:

1. Verify creation, drafts, editing, CSV import, and CSV/JSON export on a physical Android device, including the keyboard and file picker.
2. Set the intended `versionCode` and `versionName` in `app/build.gradle`. Increase `versionCode` for an update.
3. In Android Studio, use **Build > Generate Signed App Bundle or APK**, choose **APK**, and build the release variant with your own signing key. Keep that key outside Git and retain it for future updates. `assembleRelease` alone does not configure a release signing key in this project.
4. Create a GitHub release for the tested commit and attach the signed APK. State the minimum Android version, version number, and what changed. If distributing a debug build for testing instead, label it clearly as a debug build.
5. If you replace the root `Komirka.apk`, keep the README's version and signing description consistent with the new file. The existing download link will continue to point to that file.

The existing Android package ID is `ua.komirka`. Keep it and the signing identity consistent when providing updates to existing installations. A differently signed APK cannot update an installed copy in place.

