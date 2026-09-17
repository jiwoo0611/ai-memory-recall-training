# Personal Photo-Based Recall Training Service

**Turn personal photos into personalized autobiographical memory training.**

> Project status: proposal and development stage. Planned functions are labeled below and will be updated as implementation progresses.

## Demo Preview

A demo GIF or video will be added after the complete user journey is implemented.

Planned journey: **sign in → select a personal photo → generate recall questions → complete an activity → review saved feedback**

Demo materials and the presentation script will be stored in [`demo/`](demo/).

## Overview

This project is an account-based cognitive training service that uses personal photos and their metadata as recall cues. The service extracts cues such as people, places, dates, and events, generates factual and open-ended questions, stores the user's responses, and summarizes the session in an individual report.

The main goal is to build a reusable service that turns personal photos into personalized autobiographical memory training.

## Why This Project?

Many cognitive-training services use abstract games, repeated recognition tasks, or generic tests. These activities can train memory and attention, but they often have little connection to the user's own past.

| Existing limitation | Our design response |
| --- | --- |
| Generic tasks provide weak autobiographical cues | Use the user's personal photos as meaningful recall cues |
| Repetition and recognition dominate the activity | Generate varied factual and open-ended recall questions |
| Game scores do not directly describe real-life memories | Store answers, recall records, and progress in an individual report |

Research also supports the use of personally relevant images:

- Lifelog photo-review training helped older adults recall more episodic details.
- A research review reported improvements in the recall and recognition of events and event details.
- Personally relevant images supported the retrieval of specific positive autobiographical memories.

See [Research Basis](#research-basis) for the sources used in the proposal.

## What You Can Do

The planned service will allow users to:

- Create an account and manage personal training records
- Upload or select a personal photo
- Extract usable cues from the photo and its metadata
- Generate multiple recall questions from those cues
- Complete factual and open-ended recall activities
- Save answers and session history
- Review personalized feedback and an individual report

## How It Works

1. **Photo input**  
   The user uploads or selects a personal photo.

2. **Cue extraction**  
   The service identifies usable information from the image and its metadata.

3. **Question generation**  
   The service creates candidate questions and selects questions relevant to the available cues.

4. **Recall activity**  
   The user answers factual or open-ended questions about the pictured event.

5. **Record and feedback**  
   The service stores the session and summarizes the user's responses and progress.

A technical constraint is to generate useful recall questions without inventing facts that are not supported by the photo or metadata.

## Planned Features

- Account-based photo and training-data management
- Personalized questions based on at least two available cues
- Multiple recall activity types
- Session history and result storage
- Personalized feedback and reports

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

The README presents the service purpose and planned result first, following the clear documentation flow used by HyperFrames. The folders then separate application code, AI logic, data, tests, demo materials, and documentation by responsibility, following the modular approach used by Reminisce.

## Evaluation Plan

The final evaluation will examine:

- **Question personalization:** each question reflects at least two usable cues
- **Recall completion:** the average response contains at least five words
- **Question relevance:** planned CLIP similarity score of at least 0.70
- **Record reliability:** session and result storage success rate
- **Feedback availability:** report-generation success rate

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

## Research Basis

- Xu et al. (2020), *Scientific Reports*: [Lifelog photo-review training and autobiographical memory](https://www.nature.com/articles/s41598-020-58076-6)
- Foley et al. (2020), *Review of General Psychology*: [Review of photo-based remembering](https://journals.sagepub.com/doi/10.1177/1089268020958686)
- Fernández-Pérez et al. (2024), *Current Psychology*: The role of the personal relevance of images in retrieving autobiographical memories for emotion regulation

## Repository References

The README flow and repository organization were inspired by:

- [HyperFrames](https://github.com/heygen-com/hyperframes)
- [Reminisce](https://github.com/rayray2006/Reminisce)
