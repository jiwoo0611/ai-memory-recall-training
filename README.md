# Personal Photo-Based Recall Training Service

A graduation project that uses personal photos as cues for personalized recall activities.

> Project status: proposal and development stage. Planned functions are labeled below and will be updated as implementation progresses.

## Overview

The service is designed to connect cognitive training with the user's own experiences. After a user uploads or selects a personal photo, the service analyzes available visual and metadata cues, generates a recall question, stores the training session, and provides feedback.

## Planned Features

- Account-based photo and training-data management
- Personalized questions based on photo cues
- Multiple recall activity types
- Session history and result storage
- Personalized feedback and reports

## Demo Plan

The demonstration will show one complete user journey:

1. Sign in
2. Upload or select a personal photo
3. Generate a personalized recall question
4. Complete the activity
5. Save the result and review feedback

Detailed demo materials will be stored in [`demo/`](demo/).

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4 |
| Web UI | Thymeleaf |
| Persistence | Spring Data JPA |
| Development database | H2 |
| Image metadata | metadata-extractor |
| Build | Gradle Kotlin DSL |
| Test | JUnit Platform and Spring test starters |

## Repository Structure

```text
graduation_project/
├── ai/                         # Photo analysis and question-generation plans
├── assets/                     # README, diagram, and demo media
├── database/                   # Schema, ERD, migrations, and sample-data plans
├── demo/                       # Demo scenario, media, and setup instructions
├── docs/                       # Architecture, API, design, and evaluation documents
├── src/
│   ├── main/
│   │   ├── java/               # Spring Boot application code
│   │   └── resources/          # Templates, static files, and configuration
│   └── test/                   # Application tests
├── build.gradle.kts            # Dependencies and build configuration
└── README.md                   # Project overview
```

This structure follows two principles: show the project purpose and result clearly in the README, and separate interfaces, application logic, AI, data, tests, and documentation by responsibility.

## Evaluation Plan

The final evaluation will examine:

- Question personalization: each question reflects at least two usable cues
- Recall completion: the response contains enough content for analysis
- Question relevance: planned CLIP similarity score of at least 0.70
- Record reliability: successful storage of sessions and results
- Feedback availability: successful report generation

Measured results will be added after implementation. The proposal includes only the demo and evaluation plan.

## Getting Started

### Requirements

- Java 17
- Git

### Run

macOS or Linux:

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

### Test

```bash
./gradlew test
```

## Team Responsibilities

- Database construction
- Server construction
- User interface and interaction
- AI integration and evaluation

Detailed assignments will be updated after the team confirms the implementation scope.

## References

The repository presentation and documentation structure were inspired by:

- [HyperFrames](https://github.com/heygen-com/hyperframes)
- [Reminisce](https://github.com/rayray2006/Reminisce)
