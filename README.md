# CivicConnect

<p align="center">
  <img src="screenshots/app_logo.png" alt="CivicConnect logo" width="140" />
</p>

[![Platform](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com/)
[![Backend](https://img.shields.io/badge/Backend-Firebase-orange?logo=firebase)](https://firebase.google.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue?logo=kotlin)](https://kotlinlang.org/)
[![ML](https://img.shields.io/badge/ML-TensorFlow%20Lite-ff6f00?logo=tensorflow)](https://www.tensorflow.org/lite)

CivicConnect is an Android-based civic issue reporting platform built as a capstone project. Citizens can report public issues with photos and location data, receive a priority score before submission, track issue progress, and review remarks tied to each report. Administrators get a consolidated dashboard to search, filter, prioritize, and resolve issues in real time.

The latest runnable application snapshot in this repository is `v4.0.0`, and this README is intentionally aligned to that version's codebase, backend, model artifacts, and screenshots.

## Repository At A Glance

This repository is both a runnable project and a capstone archive. It currently contains:

- Android Studio project snapshots from `v1.0.0` through `v4.0.0`
- The active Firebase backend bundled inside the `v4.0.0` Android snapshot
- The notebook, dataset, exported mobile ML artifacts, and evaluation outputs used for on-device scoring
- Project deliverables such as the paper, patent draft, presentation deck, and final reports
- Versioned screenshot sets for both the original UI and the latest `v4.0.0` interface

## Version Archive

- `App/v1.0.0/CivicConnect` through `App/v4.0.0/CivicConnect` preserve the Android project at each milestone.
- `App/v4.0.0/CivicConnect` is the latest runnable snapshot and the only version referenced in the setup instructions below.
- `screenshots/v1.0.0/` keeps the older UI capture set for archival purposes.
- `screenshots/v4.0.0/` is the current screenshot set used throughout this README.

## Repository Structure

```text
Capstone/
|-- App/
|   |-- v1.0.0/CivicConnect/
|   |-- v2.0.0/CivicConnect/
|   |-- v3.0.0/CivicConnect/
|   `-- v4.0.0/CivicConnect/
|       |-- app/src/main/
|       |   |-- java/com/example/civicconnect/
|       |   |   |-- adapters/
|       |   |   |-- data/
|       |   |   `-- ml/
|       |   |-- res/
|       |   `-- assets/ml/
|       |-- civicconnect-backend/functions/
|       `-- gradle/
|-- Model/
|   |-- Code/CivicConnect.ipynb
|   |-- Dataset/civic_priority_dataset_6000.csv
|   `-- priority_mobile_outputs/
|       |-- export_summary.json
|       |-- priority_mobile_regressor.tflite
|       `-- training_config.json
|-- Paper/Civic_Connect_IEEE.pdf
|-- Patent/Civic_Connect_Patent.docx
|-- PPT/
|   |-- CivicConnect-AI-Powered-Citizen-Reporting-App.pptx
|   `-- civicconnectppt.pdf
|-- Report/
|   |-- Android/FINAL REPORT CIVIC.pdf
|   `-- Capstone/FINAL REPORT CAPSTONE.pdf
|-- screenshots/
|   |-- app_logo.png
|   |-- v1.0.0/
|   `-- v4.0.0/
`-- README.md
```

Some version folders also contain generated or IDE-managed directories such as `.gradle`, `.idea`, `.kotlin`, `build`, and backend `node_modules`. Those are present because the repository preserves working project snapshots rather than a minimal source-only export.

## What `v4.0.0` Contains

### Android app

The current Android project lives at `App/v4.0.0/CivicConnect` and is configured with:

- `versionName = "4.0.0"`
- `minSdk = 24`
- `compileSdk = 36`
- `targetSdk = 36`
- Java 11 / Kotlin on Android
- XML layouts with ViewBinding
- Firebase Authentication, Firestore, Storage, and Cloud Functions
- TensorFlow Lite for on-device priority scoring

### Source map

| Path                                                                                 | Responsibility                                                                    |
| ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- |
| `app/src/main/java/com/example/civicconnect/LoginActivity.kt`                        | Citizen/admin login, registration, and password reset                             |
| `app/src/main/java/com/example/civicconnect/SplashActivity.kt` and `MainActivity.kt` | App launch flow and main shell                                                    |
| `app/src/main/java/com/example/civicconnect/HomeFragment.kt`                         | Dashboard cards and recent issue overview                                         |
| `app/src/main/java/com/example/civicconnect/ReportFragment.kt`                       | Issue submission, image capture/upload, location detection, and on-device scoring |
| `app/src/main/java/com/example/civicconnect/MyIssuesFragment.kt`                     | Citizen issue history, search, filter, and highlighting                           |
| `app/src/main/java/com/example/civicconnect/ProfileFragment.kt`                      | User profile view                                                                 |
| `app/src/main/java/com/example/civicconnect/AdminIssuesFragment.kt`                  | Admin-wide issue search, filters, and operations view                             |
| `app/src/main/java/com/example/civicconnect/IssueDetailFragment.kt`                  | Status updates, remarks, duplicate details, and issue media preview               |
| `app/src/main/java/com/example/civicconnect/data/Issue.kt`                           | Shared issue data model                                                           |
| `app/src/main/java/com/example/civicconnect/ml/PriorityTokenizer.kt`                 | Tokenization support for the bundled TensorFlow Lite model                        |

The manifest confirms camera, internet, and location permissions, and the app also includes a `FileProvider` for camera capture workflows on device.

### Backend

The active backend lives at `App/v4.0.0/CivicConnect/civicconnect-backend/functions` and targets Firebase Functions on Node.js `22`.

It currently exposes two callable functions in `asia-south1`:

| Function              | Purpose                                                                                                                                   |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `checkDuplicateIssue` | Runs a pre-submit duplicate check using category match, Universal Sentence Encoder embeddings, cosine similarity, and geographic distance |
| `submitIssuePipeline` | Validates the caller, generates embeddings, repeats duplicate detection, stores the issue in Firestore, and assigns a tracking number     |

Duplicate handling in `v4.0.0` is based on:

- Category match
- Approximate radius limit of `300` meters
- Similarity threshold of `0.75`
- Automatic rejection and linking when a report is classified as a duplicate

## Model Assets

The machine learning workflow is organized under `Model/`:

- `Model/Code/CivicConnect.ipynb` contains the notebook workflow
- `Model/Dataset/civic_priority_dataset_6000.csv` contains the civic priority dataset
- `Model/priority_mobile_outputs/` contains the exported mobile artifacts and evaluation files

According to `Model/priority_mobile_outputs/export_summary.json`, the current mobile export includes:

- Vocabulary size: `151`
- Max sequence length: `96`
- Student model RMSE: `0.0759`
- Student model R^2: `0.9166`
- Band accuracy: `87.44%`

The Android app mirrors the runtime assets into `App/v4.0.0/CivicConnect/app/src/main/assets/ml/`, where the repository currently stores:

- `priority_mobile_regressor.tflite`
- `PriorityTokenizer.kt.txt`
- `tokenizer_examples.json`
- `training_config.json`
- `vocabulary.txt`

This allows priority inference to run on-device before the issue is submitted to Firebase.

## Core Features In `v4.0.0`

### Citizen flow

- Register and sign in with Firebase Authentication
- Report issues with title, description, category, image, and geocoded location
- Auto-detect location using the fused location provider
- Run on-device priority prediction before submission
- Receive a tracking number for each new report
- Search and filter personal issue history
- Open detailed issue views with status and remarks
- Receive duplicate-report feedback when a similar issue already exists nearby

### Administrator flow

- View all submitted issues in one place
- Search by title or tracking number
- Filter by status
- Review automatically surfaced priority levels
- Add remarks and update issue status directly from the detail screen
- Inspect duplicate-report metadata such as similarity score, linked issue ID, and tracking number

## Tech Stack

| Layer               | Implementation                             |
| ------------------- | ------------------------------------------ |
| Android app         | Kotlin, XML layouts, ViewBinding           |
| Mobile ML           | TensorFlow Lite                            |
| Backend             | Firebase Cloud Functions                   |
| Data                | Firebase Firestore                         |
| Media               | Firebase Storage                           |
| Auth                | Firebase Authentication                    |
| Duplicate detection | TensorFlow.js + Universal Sentence Encoder |
| Location            | Fused Location Provider + Geocoder         |

## Screenshots (`v4.0.0`)

All screenshots below are sourced from `screenshots/v4.0.0/` so the README reflects the latest UI snapshot, not the archived `v1.0.0` screen set.

| Login                                   | Register                                      | Home                                  |
| --------------------------------------- | --------------------------------------------- | ------------------------------------- |
| ![Login](screenshots/v4.0.0/login.jpeg) | ![Register](screenshots/v4.0.0/register.jpeg) | ![Home](screenshots/v4.0.0/home.jpeg) |

| Report Issue                                    | My Issues                                                         | Issue Detail                                                          |
| ----------------------------------------------- | ----------------------------------------------------------------- | --------------------------------------------------------------------- |
| ![Report Issue](screenshots/v4.0.0/report.jpeg) | ![Citizen Issue List](screenshots/v4.0.0/citizen-issue-list.jpeg) | ![Citizen Issue Detail](screenshots/v4.0.0/citizen-issue-detail.jpeg) |

| Profile                                     | Admin Issue List                                              | Admin Issue Detail                                                |
| ------------------------------------------- | ------------------------------------------------------------- | ----------------------------------------------------------------- |
| ![Profile](screenshots/v4.0.0/profile.jpeg) | ![Admin Issue List](screenshots/v4.0.0/admin-issue-list.jpeg) | ![Admin Issue Detail](screenshots/v4.0.0/admin-issue-detail.jpeg) |

## Setup

### Prerequisites

- Android Studio
- JDK 11
- A configured Firebase project
- `google-services.json` for the Android app
- Node.js `22` for Firebase Functions
- Firebase CLI for backend deployment

### Run the Android app

```bash
git clone https://github.com/Illicitus25/Civic-Connect.git
cd Civic-Connect
```

1. Open `App/v4.0.0/CivicConnect` in Android Studio.
2. Place your Firebase config file at `App/v4.0.0/CivicConnect/app/google-services.json`.
3. Sync Gradle and run the app on an emulator or physical device.

### Deploy the backend

```bash
cd App/v4.0.0/CivicConnect/civicconnect-backend/functions
npm install
cd ..
firebase deploy --only functions
```

If your Firebase project alias is not configured yet, run `firebase login` and `firebase use <project-id>` before deployment.

## Supporting Project Artifacts

Beyond the application code, the repository also keeps the project documentation together:

- Research paper: [Paper/Civic_Connect_IEEE.pdf](Paper/Civic_Connect_IEEE.pdf)
- Patent draft: [Patent/Civic_Connect_Patent.docx](Patent/Civic_Connect_Patent.docx)
- Presentation deck: [PPT/CivicConnect-AI-Powered-Citizen-Reporting-App.pptx](PPT/CivicConnect-AI-Powered-Citizen-Reporting-App.pptx)
- Presentation PDF: [PPT/civicconnectppt.pdf](PPT/civicconnectppt.pdf)
- Android report: [Report/Android/FINAL REPORT CIVIC.pdf](Report/Android/FINAL REPORT CIVIC.pdf)
- Capstone report: [Report/Capstone/FINAL REPORT CAPSTONE.pdf](Report/Capstone/FINAL REPORT CAPSTONE.pdf)

## Contributors

Developed by Jayaprasad T P
Guided by the Department of Computer Science, Lovely Professional University
