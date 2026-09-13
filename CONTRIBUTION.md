# Contributing to iTantra

Thank you for contributing to iTantra.

This project is being developed as an SIH 2026 project, so we want contributions to remain **organized, reviewable, and easy for the entire team to understand**.

## 1. Do Not Work Directly on `main`

Do not make feature or fix commits directly on `main`.

Always create a separate branch.

```bash
git checkout main
git pull origin main

git checkout -b feat/ml-offline-stt
```

Make your changes, commit them, push the branch, and create a Pull Request.

## 2. Branch Naming Convention

Use:

```text
<type>/<short-description>
```

Keep the name lowercase and use hyphens.

### Feature

```text
feat/offline-stt
feat/offline-tts
feat/bluetooth-communication
feat/wifi-direct
feat/location-sharing
feat/sos-alert
feat/team-tracking
feat/qr-pairing
```

### Bug Fix

```text
fix/bluetooth-reconnection
fix/audio-playback
fix/location-permission
fix/stt-crash
```

### Removing Something

```text
remove/unused-audio-module
remove/old-stt-implementation
remove/dead-code
```

### Refactoring

```text
refactor/communication-layer
refactor/ml-service
refactor/navigation
```

### Documentation

```text
docs/setup-guide
docs/architecture
docs/contributing
```

### Chores / Configuration

```text
chore/update-dependencies
chore/gradle-config
chore/ci
```

### Tests

```text
test/bluetooth
test/stt
test/emergency-flow
```

## 3. Recommended Branch Names

For example, if you are implementing offline STT:

```bash
git checkout -b feat/offline-stt
```

If you are fixing Bluetooth reconnection:

```bash
git checkout -b fix/bluetooth-reconnection
```

If you are adding the SOS feature:

```bash
git checkout -b feat/sos-alert
```

If you are updating dependencies:

```bash
git checkout -b chore/update-dependencies
```

## 4. Commit Messages

Keep commits small and meaningful.

Recommended format:

```text
<type>: <description>
```

Examples:

```text
feat: add offline speech recognition
feat: implement bluetooth message transport
feat: add emergency priority messages

fix: handle bluetooth disconnection
fix: prevent duplicate emergency alerts

refactor: simplify communication repository

docs: update project architecture

chore: update dependency versions

test: add communication flow tests
```

Avoid commits such as:

```text
update
changes
final
final2
working
stuff
test
asdf
```

## 5. Keep Branches Focused

A branch should generally represent **one logical change**.

Good:

```text
feat/offline-stt
```

with changes related to offline STT.

Bad:

```text
feat/everything
```

containing:

```text
STT
Bluetooth
UI redesign
SOS
database changes
unrelated bug fixes
```

If multiple independent features are required, create separate branches where practical.

## 6. Architecture Rules

Follow the existing project structure.

```text
core/
    Shared/device-level infrastructure

data/
    Data sources and persistence

domain/
    Business logic and use cases

feature/
    Feature-specific UI and presentation logic
```

Do not create a new top-level folder without discussing why it is necessary.

Similarly, do not move existing code between layers simply for stylistic reasons.

## 7. Pull Request Flow

The normal workflow is:

```text
main
  │
  ├── feat/offline-stt
  │
  ├── feat/bluetooth-communication
  │
  ├── feat/sos-alert
  │
  └── fix/audio-playback
```

For a new task:

```bash
git checkout main
git pull origin main

git checkout -b feat/<your-feature>
```

Then:

```bash
git add .
git commit -m "feat: add <feature>"
git push -u origin feat/<your-feature>
```

Create a Pull Request into:

```text
main
```

After review and approval, merge the Pull Request.

## 8. Keep `main` Stable

`main` should represent the version of the project that the team considers reasonably stable.

Do not push experimental or broken work directly into `main`.

If you are experimenting with something risky, create a branch:

```text
experiment/<short-description>
```

Example:

```text
experiment/indoor-positioning
experiment/stt-model-comparison
```

Once the approach is proven, create a proper feature branch if necessary.
