Scope Forms Tools
=================

This repository contains tools that are used to develop forms.

Normally, these tools aren't used directly. Instead, the [forms repository](https://bitbucket.riege.de/projects/SCOPE/repos/forms/browse) contains scripts to bootstrap and invoke the software contained in this repository.

This repo is open-source under the [Universal Permissive License](https://opensource.org/licenses/UPL). This allows anonymous cloning, which simplifies the setup for forms developers.

Gradle build
------------

### Java version

The build now targets Java 11.

Use a Java 11 runtime when invoking `./gradlew`. During the Gradle upgrade, the wrapper is still on Gradle `5.2.1`, so Java toolchains are not configured yet; the active JVM itself must be Java 11.

### Tests

To run all self-tests, run

    ./gradlew test

To run the full verification build, run

    ./gradlew check

