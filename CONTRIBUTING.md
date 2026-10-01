
# Contributing to Open Integration Engine

Thank you for your interest in contributing to the **Open Integration Engine** project. Contributions are vital to the continued growth and success of the project, and we welcome all forms of participation, whether you are a developer, a documentation contributor, or a user providing feedback.

## Community Guidelines

- Be respectful and professional in all interactions.
- Provide constructive feedback and suggestions.
- Engage in discussions around pull requests and issues with an open and collaborative mindset.

## Table of Contents

- [How to Contribute Time](#how-to-contribute-time)
- [How to Contribute Money](#how-to-contribute-money)
- [How to Contribute Code](#how-to-contribute-code)

## How to Contribute Time

Individuals can contribute code, issues, or questions at any time. 

Corporations are encouraged to give time by allowing their staff to contribute to OIE with code, participation in the community, or other professional time. 

Entities working on larger issues or offering significant or recurring contributions of time are encouraged to [contact the OIE Steering Committee](https://openintegrationengine.org/contact/). This allows us to make the most of the contributed time for mutual benefit.


### Reporting Bugs

If you encounter a bug, please report it using the **GitHub Issues Tracker**:
1. **Search for existing issues** to check if the problem has already been reported.
2. If the issue is not listed, create a new issue with the following information:
   - A clear and descriptive title.
   - Steps to reproduce the issue.
   - The expected vs. actual behavior.
   - Any relevant logs, error messages, or screenshots to help diagnose the issue.

### Suggesting Features

If you would like to suggest a new feature or enhancement:
1. Open a new issue in the [**GitHub Issues Tracker**](https://github.com/OpenIntegrationEngine/engine/issues).
2. Label the issue as a **feature request**.
3. Provide a detailed description of the feature and the problem it aims to solve.
4. If applicable, include examples or use cases to demonstrate the value of the feature.


## How to Contribute Money

OIE has modest expenses for signing certificates, hosting, build minutes, and development tools. Our primary expense is human time for developing code and managing the project. Financial contributions will be applied to making OIE better.

Small individual donations are appreciated, however your individual time is often more valuable to us than your currency.

Corporations can contribute by:
* Investing money by paying or hiring developers [to contribute code](#how-to-contribute-code)
* Directly contributing to OIE [by contacting the OIE Steering Committee](https://openintegrationengine.org/contact/)
* Joining a sponsoring foundation. Learn which foundations are sponsoring the project [by contacting the OIE Steering Committee](https://openintegrationengine.org/contact/)

## How to Contribute Code

### 1. Open an Issue
Before making any changes, please open an issue in the [GitHub Issues Tracker](https://github.com/OpenIntegrationEngine/engine/issues). This step helps us discuss the problem or feature before work begins, ensuring alignment and reducing redundant efforts.

### 2. Fork the Repository
Start by forking the [Open Integration Engine GitHub repository](https://github.com/OpenIntegrationEngine/engine) to your own GitHub account.

### 3. Clone Your Fork
Clone your fork locally to your development environment:
```bash
git clone git@github.com:your-github/engine.git
```

### 4. Make Changes
Create a new branch for your feature or bug fix:
```bash
git checkout -b feature/your-feature-name
```

### 5. Install Tooling
OIE specifies the working Java version in [.sdkmanrc](./.sdkmanrc). To take advantage of this, install [SDKMAN](https://sdkman.io/) and run `sdk env install`
in the project's root directory. No other tooling is required.

### 5a. Build
The build is driven by Gradle through the included wrapper; no separate Gradle install is needed:
```bash
./gradlew build -PdisableSigning=true   # full build without jar signing
./gradlew test                          # run the unit tests (-Pcoverage=true for JaCoCo)
./gradlew dist                          # build the distribution extension zips
```
On Windows use `gradlew.bat` instead of `./gradlew`. The assembled distribution lands in `server/setup`, the same location the previous Ant build used. For release artifacts, run a clean build: `./gradlew clean build dist`. Windows with SDKMan may generate an error about the file path being too long in the javadoc step, skip this by adding `-x :server:userApiJavadoc` to your Gradle command.

Dependencies are pinned and checksum-verified. To change a dependency version: edit `gradle/libs.versions.toml`, then refresh the checksum metadata **with a cold dependency cache and CI's flags**:
```bash
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 build dist -PdisableSigning=true -Pcoverage=true
```
The cold cache matters: a warm cache skips re-resolving already-cached parent POMs, so they never get recorded, and the build then fails verification only in CI (this bit us once during the migration). The run downloads everything once and takes a few minutes. Only when adding a **new** artifact that ships in the distribution does `gradle/vendored-layout.json` need a one-line placement entry, and the build fails with a message telling you so.

### Changing build logic

The build's correctness is guarded by output comparison, not by unit
tests of the build scripts. When you change build logic (staging,
packaging, jar definitions), confirm the change does not alter the
product: build `server/setup` before and after, and compare the two
trees. The [oie-build-parity](https://github.com/pacmano1/oie-build-parity)
tooling does this at the archive-entry level (and can reproduce the
original byte-identical comparison against the pre-Gradle Ant baseline).
Only the changes you intended should appear.

### Run and debug

```bash
./gradlew :server:createDerbyDb     # one-time: create the embedded database
./gradlew :server:devRun            # run the server from the development tree
./gradlew :server:devLauncher       # run the server the way production starts it (from server/setup)
./gradlew :client:devClient         # run the administrator client against https://localhost:8443
```

Add `--debug-jvm` to any of these to suspend on JVM start and attach a debugger on port 5005. The JDK module flags come from `server/conf/default_modules.vmoptions`, the same file the production launcher uses. For IDEs, import the repository as a Gradle project (IntelliJ does this natively; Eclipse via Buildship); the old `.classpath`/`.project` files are gone on purpose.

### 6. Implement your changes

Implement the necessary changes, ensuring they align with the project’s coding standards and practices.

### 7. Test Your Changes
Before submitting your changes, please ensure that all tests pass and that your changes work as expected in your local environment.

### 8. Submit a Pull Request
Once your changes are ready, push them to your fork and create a **draft pull request (PR)** from your branch to the `main` branch of the project. Draft PRs help indicate that the work is in progress.  
Mark the PR as **"Ready for review"** only when it is actually complete and ready for feedback. Include a brief description of the changes and reference the related issue.

## License

By contributing to **Open Integration Engine**, you agree that your contributions will be licensed under the [Mozilla Public License (MPL) 2.0](./LICENSE).

Thank you for your interest in improving **Open Integration Engine**.
