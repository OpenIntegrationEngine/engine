# HOWTO Release Open Integration Engine

## Audience

This document is primarily meant to be referenced by OIE maintainers. This document is meant to help maintainers perform releases with a consistent process. This document may be useful to forks who want to release in a similar way. This document should also be updated with PRs that improve or change the release process.

## Task Overview

These are the tasks to perform a release:
1. Update version numbers
2. Build the installer
3. Sign the installer
4. Release the installer and artifacts as RC
5. Fix showstopping bugs. Log all others.
5. Release the installer and artifacts as a final release

These tasks are ordered. They support the direction of the maintainers and steering committee to release regularly.

## Task Details

### Update Version Numbers

Edit the version number in several files to indicate the release version. Follow (this PR)[https://github.com/OpenIntegrationEngine/engine/pull/301] as a guide.

Execute an automated build and verify that the version is correctly populated. It will appear in various properties and XML files for the application. The old version should be replaced.

### Build The Installer

You will need Install4J installed. OIE has an Install4J license available for maintainers. Forks or independent builds will need to acquire their own license.

Review the [Install4J specific readme.](tools/install4j/readme.html)

Run Install4J and load the [Install4J configuration for OIE](tools/install4j/oie-installer-config.install4j)

### Sign The Installer

You will need a code-signing certificate. OIE has sponsors with these certificates who sign our builds.

After signing the installer and artifacts ensure that OIE executables, services, and JARs have the expected signature and that it is valid.

### Publish Installer and Artifacts for Release Candidate

The installer and artifacts should be published to the [OIE Engine github Releases Section](https://github.com/OpenIntegrationEngine/engine/releases).

### Bug Report Period

Elicit feedback from the maintainers and the greater OIE community. Listen to feedback and document it.

Showstopping bugs should be recorded as issues in the current release milestone and worked on promptly. 

Bug reports that are not showstoppers should be recorded and planned following our typical processes.

Ask for volunteers to test some common paths and combinations for OIE.

OS and DB combinations:

|OS|DB|
|---|---|
|Windows|Postgres|
|Windows|MS SQL|
|Windows|Oracle|
|Windows|MySQL/Maria|
|Windows|Derby|
|Mac|Postgres|
|Mac|MS SQL|
|Mac|Oracle|
|Mac|MySQL/Maria|
|Mac|Derby|
|Linux|Postgres|
|Linux|MS SQL|
|Linux|Oracle|
|Linux|MySQL/Maria|
|Linux|Derby|

Install and Upgrade paths:

|OS|Installer|New Install or Upgrade|
|---|---|---|
|Windows|EXE|New|
|Windows|EXE|Upgrade|
|Windows|TAR|New|
|Windows|TAR|Upgrade|
|Mac|EXE|New|
|Mac|EXE|Upgrade|
|Mac|TAR|New|
|Mac|TAR|Upgrade|
|Linux|EXE|New|
|Linux|EXE|Upgrade|
|Linux|TAR|New|
|Linux|TAR|Upgrade|

### Publish Installer and Artifacts for Final Release

Generate the installer and build artifacts for the final release following the same version update, build, and signing process. 

Review the artifacts with the maintainers.

Collaborate with the OIE steering committee for social media announcements, website updates, etc.

Publish the releases to the [OIE Engine github Releases Section](https://github.com/OpenIntegrationEngine/engine/releases).


