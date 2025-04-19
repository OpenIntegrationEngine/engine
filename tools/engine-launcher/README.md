# OIE Engine Launcher

[![License: MPL 2.0](https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg)](https://opensource.org/licenses/MPL-2.0)
A native engine launcher written in Clojure for launching the OIE Server with advanced `.vmoptions` support.

## Overview

This project provides a small, standalone launcher (`engine.jar`) that acts as a wrapper around the OIE Server.

Its primary purpose is to provide a flexible and powerful way to configure the Java Virtual Machine (JVM) arguments used to launch the target application, going beyond the capabilities of simple shell scripts. Configuration is managed via an `engine.vmoptions` file and associated files within a `conf/` directory, all located alongside the launcher JAR.

## Features

* Advanced JVM configuration via `engine.vmoptions` and included files.
* Supports standard JVM options (`-Xmx`, `-Dproperty=value`, etc.).
* Allows environment variable substitution (`${VAR_NAME}`) in options files.
* Supports including options from other files (`-include-options`).
* Allows specifying the Java executable path (`-java-cmd`).
* Flexible classpath manipulation (`-classpath`, `-classpath/a`, `-classpath/p`).
* Determines Java executable based on `-java-cmd`, `JAVA_HOME`, or system `PATH`.
* Includes a shutdown hook to attempt graceful termination of the launched Java process.

## Usage (End-User Instructions)

1.  **Obtain Release:**
    This tool is provided with the full engine. 

2.  **Customize JVM Options (Optional):**
    The default configuration includes common settings. To add custom JVM options (e.g., increase memory with `-Xmx`, define system properties with `-Dprop=val`) or use advanced directives, edit the `conf/custom.vmoptions` file. See the **Configuration** section below for details on the file structure and available syntax.
    * **Note:** If you are running on Java 8, you should comment out the line `-include-options conf/default_modules.vmoptions` within the main `engine.vmoptions` file.

3.  **Run the Launcher:**
    Open your terminal or command prompt, navigate into the extracted directory, and run the launcher using `java -jar`:

    ```bash
    java -jar engine.jar
    ```

The launcher will read `engine.vmoptions` and its included files, determine the Java command, construct the final JVM arguments and classpath (prepending `mirth-server-launcher.jar`), and start the OIE Server. Output will appear in your console. Press `Ctrl+C` to initiate shutdown; the launcher will attempt to stop the Server process gracefully.

## Building (Developer Instructions)

### Prerequisites

* **Git**
* **Leiningen** (Version 2.x recommended): The build tool for Clojure projects. [Installation Instructions](https://leiningen.org/#install)
* **Java Development Kit (JDK)** (Version 8 or higher recommended, compatible with Clojure 1.12+).

### Steps

1.  **Clone the Repository:**
    ```bash
    git clone [https://github.com/OpenIntegrationEngine/engine.git](https://github.com/OpenIntegrationEngine/engine.git)
    cd engine
    ```

2.  **Install Dependencies:** (Leiningen handles this automatically on first command run)

3.  **Run Tests:**
    ```bash
    lein test
    ```

4.  **Build the Executable Uberjar:**
    ```bash
    lein uberjar
    ```
    This command compiles the Clojure code and packages it, along with its dependencies (Clojure itself), into a single executable JAR file.

5.  **Locate the Artifact:**
    The standalone launcher JAR will be created at: `target/engine.jar`

## Configuration (`engine.vmoptions` Structure)

The JVM configuration is managed through a set of files included by the main `engine.vmoptions`. **Users should place all customizations in `conf/custom.vmoptions`.**

**Note: If you are launching the server with java 8, you must comment out the line to include `conf/default_modules.vmoptions` found in the main `engine.vmoptions` or the application will fail to start.** 

### Available Syntax in `conf/custom.vmoptions`

You can use the following within `conf/custom.vmoptions`:

* **Standard JVM Options:** e.g., `-Xmx4g`, `-XX:+UseG1GC`, `-Duser.timezone=UTC`
* **Environment Variable Substitution:** `${VAR_NAME}` (e.g., `-include-options ${ENV_PATH}/custom.vmoptions`)
* **Directives:**
    * `-include-options <path>`: Include yet another options file.
    * `-java-cmd <path>`: Specify the Java executable path.
    * `-classpath <path>`: Replace classpath segments added so far by other directives *within custom.vmoptions or its includes*.
    * `-classpath/a <path>`: Append to the classpath segments added by other directives.
    * `-classpath/p <path>`: Prepend to the classpath segments added by other directives.

(Note: `mirth-server-launcher.jar` is always prepended to the final classpath after all options files are parsed).

## License

This project is licensed under the **Mozilla Public License Version 2.0**. See the [LICENSE](https://mozilla.org/MPL/2.0/) file or the license header in source files for details.
