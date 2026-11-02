# Komirka — CSV table maker for Android phones

## [Download Komirka for Android (.apk)](Komirka.apk?raw=true)

Android 8.0 or later · English interface · Version 1.0.0

Download the APK, open it on your phone, and install it. You do not need Android Studio or any development tools.

Komirka is an offline Android app for creating CSV tables on your phone. Define your columns, enter records one field at a time, and export the result as CSV or JSON. It is useful for field observations, inventories, research logs, and other data you want to collect away from a computer.

The app runs on Android 8.0 or later. The interface is in English. No account or internet connection is required, and there are no ads.

[Install](#install-on-android) · [Getting started](#create-a-csv-table-on-your-phone) · [Features](#features) · [CSV import and export](#csv-import-and-export)

## Install on Android

1. Tap **[Download Komirka for Android](Komirka.apk?raw=true)** on your phone. The same `Komirka.apk` file is also in the root of this repository.
2. Open the downloaded `Komirka.apk` from your browser's downloads or your file manager. If you downloaded it on a computer, transfer it to your phone first.
3. If Android asks, allow that file manager to install apps, then complete the installation.
4. Open **Komirka**. Tap **Create table**, **Import CSV**, or **Try a sample table**.

The included APK is a debug build signed for direct installation. The usage instructions below apply to this English version.

## Create a CSV table on your phone

### 1. Name your table

Tap **Create table**. Enter a name and, optionally, a description. Choose how records should be identified:

| ID mode | What it does |
| --- | --- |
| No ID column | Keeps only the columns you add. |
| Automatic UUID | Generates a unique text identifier when you save a record. |
| Auto-increment | Numbers saved records 1, 2, 3, and so on. |
| Manual ID | Lets you enter your own unique text code for each record. |

If you use an ID column, you can give it a name. Tap **Next: columns**.

### 2. Add columns and choose data types

Tap **Add column**, enter a column name, and choose a data type. Tap **Save column** and repeat for the other fields. Use the arrows to change their order, or remove a column before finishing setup.

| Type | Use it for | Input and exported values |
| --- | --- | --- |
| Text (`String`) | Names, notes, codes, and multiline text | Preserves text, including literal `null` and `NaN`. |
| Integer (`int`) | Counts and whole numbers | Signed 64-bit integers. |
| Decimal (`float`) | Measurements and fractional values | Finite double-precision numbers; accepts a decimal point or comma and scientific notation. |
| Category (`category`) | A choice from a fixed list | Add your own options and select one during entry. |
| Ordered categories (`ordinal`) | Ratings or ranked levels | Arrange options from lowest to highest with the arrows. Exports the selected label. |
| Yes / No (`bool`) | A two-option answer | Tap Yes or No; exports as `true` or `false`. |
| Date (`date`) | Observation dates and other calendar dates | Choose from the calendar or enter `YYYY-MM-DD`. |

For category fields, add each option separately. For ordered categories, check the order before saving. Tap **Create table** when your columns are ready.

For example, an observation table might have **Location** as Text, **Sample count** as Integer, **Temperature** as Decimal, **Quality** as Ordered categories, and **Checked** as Yes / No.

### 3. Enter a record

1. Open the table and tap **New record**.
2. Fill in each field, then tap **Next**. Text and numeric fields open the appropriate keyboard; categories and Yes / No use buttons.
3. For a missing value, tap **No value**. It will export as an empty CSV cell or JSON `null`. A manual ID cannot be missing.
4. Use **Back** to revisit an earlier answer. Tap **Review** after the last field.
5. Check the record. Tap **Edit** beside any value that needs changing.
6. Tap **Save record** to add the row to the table.

Until that final step, the record is a draft. Tap **Save draft and exit** to continue later. Each table keeps one draft, including after you restart the app. Starting another record in the same table lets you continue or replace the existing draft.

### 4. Browse and edit records

Open a table to see its records, with the newest first. Use **Search records** to find values or record IDs. Switch between **Grid view** and **Card view**; results are shown in pages of 40 records. The grid scrolls horizontally for wide tables.

Open a record, edit a field, and tap **Save changes**. Previous values are kept under **Value history**. You can restore a value, edit or delete a past value, or clear the history of one field. These history changes take effect when you save the record. Manual IDs have history too.

The **Columns** tab shows field types, category options, and their order. The table menu lets you edit the name and description, export the table, or delete it. To delete an individual record, open it and use **Delete record** on the review screen.

### 5. Export your table as CSV or JSON

1. Open a table and tap **Export**, or select **Export CSV / JSON** from its menu.
2. Choose **CSV** for headers and current values, or **JSON** to include the table definition and value history.
3. Choose a filename and location in Android's file picker.
4. Wait for **File saved** before opening or transferring the file.

You can open a CSV in a spreadsheet application or read it in your own analysis tools. For example, to keep codes such as `001` as text when using pandas:

```python
import pandas as pd

data = pd.read_csv("observations.csv", encoding="utf-8-sig", dtype={"Code": "string"})
```

Use the actual name of your code column in `dtype`, or leave that argument out if you do not have one.

## Features

- **Multiple tables:** separate names, descriptions, columns, and drafts; search tables by name and see record counts on the home screen.
- **Seven field types:** text, integers, decimals, categories, ordered categories, booleans, and dates, with validation before saving.
- **Flexible IDs:** no extra ID, UUID, sequential number, or a manually entered unique code. Drafts do not consume sequential IDs, and deleted IDs are not reused.
- **Guided data entry:** one field at a time, suitable keyboards, a date picker, category buttons, Yes / No choices, explicit missing values, and a final review.
- **Resumable work:** one saved record draft per table, plus saved table setup and import configuration.
- **Category lists:** custom options, ordinal ordering, and search for longer lists. Large lists show up to 100 matching options at once; narrow the search to find others.
- **Record management:** searchable cards and a horizontally scrolling grid, pagination, editing, and deletion.
- **Value history:** dated previous values, including manual IDs, with restore, edit, delete, and clear actions.
- **CSV import:** delimiter detection, type inference, sample values, editable column definitions, column reordering, and an all-or-nothing import.
- **CSV and JSON export:** save through Android's document picker without granting access to all device storage.
- **Local storage:** a private SQLite database, no network permission, no account, no analytics SDK, and no runtime libraries beyond the Android platform.

## CSV import and export

### Import an existing CSV file on Android

1. Tap **Import CSV** on the home screen and select a file.
2. Check the table name and choose an ID mode. An ID already present in the file stays a regular column. If you add an automatic ID, give it a different column name.
3. Tap **Next: columns**. Review the inferred types and the sample values shown for each column.
4. Tap a column to rename it or change its type. Category options can be populated from the imported values; arrange ordinal options in the order you need.
5. Reorder or remove columns as needed. You can also add columns; they will start with missing values in imported rows.
6. Tap **Import table**. If a value cannot be converted, the error identifies the CSV record and column. No partial table is saved.

Try [observations.csv](examples/observations.csv) for a table with several data types, or [semicolon.csv](examples/semicolon.csv) for decimal commas and codes with leading zeros. Download the raw file to your phone before importing it.

### CSV format and compatibility

| Detail | Behavior |
| --- | --- |
| Import encoding | UTF-8, with or without a BOM; UTF-16 with a BOM. |
| Import delimiters | Comma, semicolon, or tab, detected from the header record. |
| Headers | The first record supplies column names. Empty or repeated names get unique defaults that you can edit. |
| Type inference | Conservative; numeric codes with leading zeros remain text. Types can be changed before import. |
| Quoted fields | Supports escaped quotes, delimiters inside quotes, and multiline text. |
| Missing values | Empty or whitespace-only input becomes a missing value. Literal text `null` and `NaN` stays text in a Text column. |
| Export encoding | UTF-8 with a BOM, comma-delimited, with CRLF line endings. |
| Export contents | Column headers and current values. Booleans use `true` / `false`; decimals use a point; dates use `YYYY-MM-DD`. |
| Export order | Oldest records first; imported records keep their source order when exported again. |

CSV does not retain column types, category definitions, drafts, or value history. It also does not carry spreadsheet formatting or calculate formulas. Formula-like text is exported unchanged, so choose text column types when importing such values into a spreadsheet if you want to keep them as literal text.

### What JSON export contains

The JSON file includes `format`, `version`, `exportedAt`, the table name and description, ID settings, column definitions, and `rows`. Each row contains current `values`, previous values in `aliases`, and creation and update timestamps.

Numbers and booleans keep their JSON types. Missing values are `null`. Category definitions include the option list, so the order of ordinal categories is preserved. A history item contains its previous `value` and an `at` timestamp in Unix milliseconds; row timestamps are ISO 8601 strings.

JSON is for export and further processing. This version imports CSV only, so a JSON export cannot be restored directly in the app. Drafts are not included in either export format.

## Limits and local data

- Android 8.0 / API 26 or later; there is no iOS version in this repository.
- Up to 100 columns per table, including an optional ID column.
- Table names: 1–120 characters. Column names: 1–100 characters and unique within a table.
- CSV import: up to 10 MB and 50,000 data records per file. Every data record must have the same number of cells as the header.
- Manual entry has no configured row limit; available storage and device performance still apply.
- Column definitions are fixed after creating a table. To change them, export a CSV and import it as a new table; the CSV round trip does not preserve value history.

Tables and drafts live in the app's private `komirka.db` database. Automatic Android backup is disabled. Uninstalling the app or clearing its storage deletes the local data, so export important tables beforehand. There is no cloud sync.

## For developers

The source code and tests are included for anyone who wants to modify the app. To use it, download the APK above.

<details>
<summary>Build instructions and project layout</summary>

### Build the Android app

### 1. Prepare the tools

Install a JDK (17 or later; 21 is a suitable choice) and the Android SDK. Set `JAVA_HOME` to your JDK. In Android Studio's SDK Manager, install **Android SDK Platform 36** and **Android SDK Build-Tools 36.0.0**.

The repository includes Gradle Wrapper **9.2.1** and uses Android Gradle Plugin **9.0.1**. A separate Gradle installation is not needed. The first build needs internet access to download dependencies.

### 2. Get the source

```sh
git clone https://github.com/KoVaLsKeY/TableMakerAppForPhone.git
cd TableMakerAppForPhone
```

Open this directory in Android Studio, or continue from the command line.

### 3. Set the Android SDK path

Set `ANDROID_HOME` to your SDK directory, or create `local.properties` in the project root. For example, on Windows:

```properties
sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

On macOS or Linux, use the full path to your own SDK installation. `local.properties` is machine-specific and is ignored by Git.

### 4. Build and run the checks

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS or Linux:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The Windows helper `tools/build.ps1` can also run these tasks. It uses a project-local Gradle cache and debug signing key, and detects a JDK from `JAVA_HOME` or supported local IDE installations. Pass `-Offline` only when all dependencies are already cached.

### 5. Install the result

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your phone and follow the [installation steps](#install-on-android), or install it from a connected development machine:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Updating an existing installation requires the same signing key. Keep your local debug key if you need successive debug builds to install as updates.

## Tests and project layout

The test suite covers CSV quoting and malformed input, Unicode, data types, missing values, IDs, SQLite transactions, drafts, edit conflicts, history, import/export, and the main UI flows. UI tests run in Robolectric with Android API 35 and native graphics. They do not replace a check of the keyboard and system file picker on a physical phone.

Generated reports go under `app/build/`. UI tests generate screenshots in `artifacts/screenshots/`. These output directories are ignored by Git.

```text
app/src/main/java/ua/komirka/
  MainActivity.java       Navigation, browsing, search, import, and export
  core/                  Models, value validation, and CSV parsing
  data/                  SQLite storage and JSON serialization
  ui/                    Native views, table setup, and record editing
app/src/main/res/         App name, theme, icon, and backup rules
app/src/test/            Unit, storage, and UI tests
examples/                CSV files to try importing
docs/                    Publishing notes
gradle/wrapper/          Reproducible Gradle launcher
tools/build.ps1          Optional Windows build helper
```

`Komirka.apk` is intentionally included in the repository root for direct download. Other generated packages, source archives, test reports, local SDKs, caches, and signing keys are ignored by Git. The [publishing notes](docs/PUBLISHING.md) describe how to refresh the APK and configure the repository description and topics.

</details>

If you find a problem, [open an issue](https://github.com/KoVaLsKeY/TableMakerAppForPhone/issues) with your Android version, the steps to reproduce it, and a small sample CSV when relevant. Remove personal data from examples before sharing them.
