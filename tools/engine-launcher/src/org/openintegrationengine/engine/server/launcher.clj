;;;
;;; SPDX-FileCopyrightText: 2025 Tony Germano tony@germano.name
;;;
;;; SPDX-License-Identifier: MPL-2.0
;;;

;;;;
;; File: launcher.clj
;; Purpose: Main entry point for the OIE Engine Launcher.
;;
;; This Clojure application acts as a sophisticated wrapper for launching a target Java application
;; (specifically, OIE Server, as indicated by hardcoded values).
;; Its primary responsibilities include:
;;   1. Parsing a `.vmoptions` file (`engine.vmoptions` by default) to gather JVM arguments.
;;   2. Supporting advanced features within the `.vmoptions` file, such as:
;;      - Environment variable substitution (e.g., `${VAR_NAME}`).
;;      - Including options from other files (`-include-options`).
;;      - Specifying the Java command path (`-java-cmd`).
;;      - Manipulating the classpath (`-classpath`, `-classpath/a`, `-classpath/p`).
;;   3. Determining the appropriate Java executable to use (respecting `-java-cmd`, JAVA_HOME, or system PATH).
;;   4. Constructing the final classpath, prepending the required JAR.
;;   5. Launching the target Java application as a separate process using ProcessBuilder.
;;   6. Managing the lifecycle of the child process, including a shutdown hook for graceful termination.
;;   7. Propagating the exit code of the child process.
;;
;; The code is structured to separate pure logic (parsing, command building) into helper functions
;; for testability, while the `-main` function orchestrates these helpers and handles side effects.
;;;;
(ns org.openintegrationengine.engine.server.launcher
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File FileNotFoundException IOException])
  ;; :gen-class allows this namespace to be compiled into a Java class
  ;; with a static `main` method, making it usable as the project's entry point.
  (:gen-class))

;; =============================================
;; Helper Functions (Pure Logic / Testable Core)
;; =============================================
;; This section contains functions responsible for the core logic of parsing,
;; substitution, and command construction. They are designed to be pure or
;; accept dependencies (like environment/file accessors) explicitly,
;; making them easier to unit test without real side effects.

(defn substitute-env-vars
  "Substitutes `${VAR_NAME}` patterns within a given string `s`.
   Uses the provided `getenv-fn` function to resolve environment variable values.
   This decoupling allows for easy testing with mock environments.
   Returns the string with substitutions applied; unresolved variables become empty strings. Pure."
  [s getenv-fn] ; getenv-fn :: String -> String | nil
  (str/replace s #"\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}"
               (fn [[_ var-name]] (or (getenv-fn var-name) ""))))

(defn- parse-vmoptions* ; Recursive internal implementation - see `parse-vmoptions` public API.
  "Recursively parses a vmoptions file and any files included via `-include-options`.
   Accumulates JVM options, classpath segments, the effective Java command path, and warnings.

   Parameters:
     - `file-path`: Path to the vmoptions file to parse.
     - `current-classpath`: The classpath string accumulated so far.
     - `current-java-cmd-path`: The java command path determined so far (last one wins).
     - `config`: A map containing functions for interacting with the environment,
                 enabling testability via dependency injection. Expected keys:
                   :read-file-fn :: String -> String (throws FileNotFoundException)
                   :getenv-fn :: String -> String | nil
                   :is-file-fn :: String -> boolean
                   :path-separator :: String (e.g., \":\" or \";\")

   Returns:
     A map describing the parsing result:
     { :ok?           boolean ; Indicates if parsing (including includes) succeeded without file errors.
       :options       [String] ; List of processed JVM options.
       :classpath     String   ; The fully constructed classpath string.
       :java-cmd-path String | nil ; The path specified by the *last* encountered -java-cmd, or nil.
       :warnings      [String] ; List of non-fatal issues encountered (e.g., included file not found).
       :error         Keyword | nil ; Keyword indicating fatal error type (e.g., :file-not-found) if ok? is false.
       :path          String | nil ; Path associated with the fatal error, if any.
     }

   Note on state propagation: Classpath and java-cmd-path state flows *through* recursive calls.
   The state *returned* from an include call becomes the *current* state for subsequent lines."
  [file-path current-classpath current-java-cmd-path config]
  ;; Destructure the configuration map for easier access to injected dependencies.
  (let [{:keys [read-file-fn getenv-fn is-file-fn path-separator]} config]
    (try
      ;; Read the file content using the injected function.
      (let [content (read-file-fn file-path)]
        ;; Process lines using loop/recur for tail-call optimization.
        (loop [lines (->> content
                          (str/split-lines)         ; Split into lines
                          (map str/trim)            ; Trim whitespace
                          (remove #(or (str/blank? %) (str/starts-with? % "#")))) ; Remove blank lines and comments
               ;; Accumulators for parsing results:
               options []                          ; JVM options
               classpath current-classpath         ; Classpath string
               java-cmd-path current-java-cmd-path ; Path to java executable
               warnings []]                        ; Non-fatal warnings

          (if (empty? lines)
            ;; Base case: All lines processed, return accumulated results.
            {:ok? true :options options :classpath classpath :java-cmd-path java-cmd-path :warnings warnings}

            ;; Recursive step: Process the first line.
            (let [line (first lines)
                  remaining-lines (rest lines)
                  trimmed-line (str/trim line) ; Ensure trimming even if original map didn't trim perfectly
                  ;; Define a local substitution function using the injected getenv-fn.
                  ;; This performs ${VAR} substitution on relevant parts of the line.
                  subst-fn (fn [s] (substitute-env-vars s getenv-fn))]

              (cond
                ;; --- Handle -include-options directive ---
                (str/starts-with? trimmed-line "-include-options")
                (let [included-path-str (str/trim (subs trimmed-line (count "-include-options")))]
                  ;; Check if the included path is a valid file using the injected function.
                  (if (is-file-fn included-path-str)
                    ;; Recursively parse the included file, passing down the *current* state.
                    (let [sub-result (parse-vmoptions* included-path-str classpath java-cmd-path config)]
                      (if (:ok? sub-result)
                        ;; Include succeeded: Continue parsing remaining lines, using the *updated* state
                        ;; (options, classpath, java-cmd-path, warnings) returned from the recursive call.
                        (recur remaining-lines
                               (concat options (:options sub-result)) ; Append options from included file
                               (:classpath sub-result)                ; Use classpath from included result
                               (:java-cmd-path sub-result)            ; Use java path from included result
                               (concat warnings (:warnings sub-result) [(str "Included options from: " included-path-str)])) ; Accumulate warnings
                        ;; Include failed (e.g., nested include file not found): Add a warning, keep *current* state, and continue.
                        (recur remaining-lines options classpath java-cmd-path
                               (conj warnings (str "Failed to parse included options from '" included-path-str "': " (:error sub-result))))))
                    ;; Include path is not a file: Add a warning, keep *current* state, and continue.
                    (recur remaining-lines options classpath java-cmd-path
                           (conj warnings (str "Included options path is not a file or not found: '" included-path-str "'")))))

                ;; --- Handle -java-cmd directive ---
                ;; Specifies the path to the Java executable. The last one encountered wins.
                (str/starts-with? trimmed-line "-java-cmd ")
                (let [path-from-directive (subst-fn (str/trim (subs trimmed-line (count "-java-cmd "))))]
                  ;; Update the java-cmd-path and continue with remaining lines.
                  (recur remaining-lines
                         options
                         classpath
                         path-from-directive ; This value replaces the previous one
                         warnings))

                ;; --- Handle -classpath directive (Replace) ---
                ;; Replaces the entire classpath with the substituted value.
                (str/starts-with? trimmed-line "-classpath ")
                (recur remaining-lines options (subst-fn (str/trim (subs trimmed-line (count "-classpath ")))) java-cmd-path warnings)

                ;; --- Handle -classpath/a directive (Append) ---
                ;; Appends the substituted value to the classpath, using the configured path separator.
                (str/starts-with? trimmed-line "-classpath/a")
                (let [path-to-append (subst-fn (str/trim (subs trimmed-line (count "-classpath/a"))))]
                  (recur remaining-lines options (if (str/blank? classpath) path-to-append (str classpath path-separator path-to-append)) java-cmd-path warnings))

                ;; --- Handle -classpath/p directive (Prepend) ---
                ;; Prepends the substituted value to the classpath, using the configured path separator.
                (str/starts-with? trimmed-line "-classpath/p")
                (let [path-to-prepend (subst-fn (str/trim (subs trimmed-line (count "-classpath/p"))))]
                  (recur remaining-lines options (if (str/blank? classpath) path-to-prepend (str path-to-prepend path-separator classpath)) java-cmd-path warnings))

                ;; --- Handle regular JVM option ---
                ;; Assume any other non-comment, non-blank line is a standard JVM option.
                :else
                ;; Apply substitution and add it to the options list.
                (recur remaining-lines (conj options (subst-fn trimmed-line)) classpath java-cmd-path warnings))))))

      ;; --- Handle File Not Found Error ---
      ;; If the initial `read-file-fn` call fails for `file-path`.
      (catch FileNotFoundException _
        ;; Indicate failure, return the initial classpath, set java-cmd-path to nil, and provide error details.
        {:ok? false :error :file-not-found :path file-path :options [] :classpath current-classpath :java-cmd-path nil :warnings []}))))

(defn parse-vmoptions
  "Public API for parsing a vmoptions file.
   Initializes the state (empty classpath, nil java command path) and calls the
   recursive `parse-vmoptions*` helper.

   Parameters:
     - `file-path`: Path to the root vmoptions file.
     - `initial-classpath`: A classpath string to start with (typically \"\").
                            Allows prepending/appending relative to a base classpath if needed, although
                            the current `-main` usage starts with an empty string.
     - `config`: The configuration map with injected dependencies (see `parse-vmoptions*`).

   Returns:
     The result map from `parse-vmoptions*` (see its docstring). Pure (given the config map)."
  [file-path initial-classpath config]
  (parse-vmoptions* file-path initial-classpath nil config))

(defn determine-java-executable
  "Determines the default Java executable path based on the JAVA_HOME environment variable.
   Does *not* consider the `-java-cmd` directive from vmoptions; that's handled in `-main`.
   Takes functions for environment lookup and file existence checks to enable testing. Pure.

   Parameters:
     - `getenv-fn`: Function to look up environment variables (e.g., `#(System/getenv %)`).
     - `file-exists-fn`: Function to check if a file path exists (e.g., `#(.exists (io/file %))`).
     - `file-separator`: The OS-specific file separator char (e.g., `/` or `\\`).

   Returns:
     The canonical path to `$JAVA_HOME/bin/java` if JAVA_HOME is set and the file exists,
     otherwise defaults to the string \"java\" (relying on the system PATH)."
  [getenv-fn file-exists-fn file-separator]
  (let [java-home (getenv-fn "JAVA_HOME")]
    (if (and java-home (not (str/blank? java-home)))
      ;; If JAVA_HOME is set and non-blank, construct the potential path.
      (let [;; Construct path like /path/to/java/home/bin/java
            exec-path-str (str java-home file-separator "bin" file-separator "java")
            ;; Using io/file locally to handle potential path normalization before checking existence.
            exec-path (io/file exec-path-str)]
        ;; Check existence using the canonical path to resolve symlinks etc.
        (if (file-exists-fn (.getCanonicalPath exec-path))
          (.getCanonicalPath exec-path) ;; Return the resolved, existing path
          "java")) ; JAVA_HOME is set, but bin/java doesn't exist, fallback to default
      "java"))) ; JAVA_HOME not set, use default

(defn build-command-list
  "Constructs the final command vector suitable for `ProcessBuilder`. Pure.

   Parameters:
     - `java-exec`: The determined path to the Java executable.
     - `vm-opts`: A sequence of JVM option strings.
     - `final-cp`: The fully constructed classpath string.
     - `main-cls`: The fully qualified name of the main class to execute.
     - `args`: A sequence of arguments to pass to the main class.

   Returns:
     A vector of strings representing the command and its arguments
     (e.g., [\"/path/to/java\" \"-Xmx1g\" \"-cp\" \"lib.jar\" \"com.example.Main\" \"arg1\"])."
  [java-exec vm-opts final-cp main-cls args]
  (-> [java-exec]          ; Start with the java executable path
      (into vm-opts)       ; Add all JVM options
      (into ["-cp" final-cp]) ; Add the classpath flag and value
      (conj main-cls)      ; Add the main class name
      (into args)))         ; Add arguments for the target application

;; =============================================
;; Main Function (Orchestration & Side Effects)
;; =============================================
;; This is the application entry point. It orchestrates the process:
;; 1. Sets up real environment access functions (file I/O, env vars).
;; 2. Calls the pure helper functions (`parse-vmoptions`, `determine-java-executable`, `build-command-list`)
;;    with the real environment accessors passed via the `config` map.
;; 3. Performs side effects: logging, launching the child process, managing shutdown.

(defn -main [& args]
  ;; --- Define Real Side-Effecting Dependencies ---
  ;; Create functions that perform actual system interactions. These will be
  ;; passed into the pure helper functions via the `config` map.
  (let [real-getenv (fn ([var] (System/getenv var))) ; Real environment variable lookup
        real-read-file slurp                          ; Real file reading
        real-is-file #(.isFile (io/file %))           ; Real check if path is a file
        real-file-exists #(.exists (io/file %))       ; Real check if path exists
        os-file-separator File/separator              ; System file separator ("/" or "\\")
        os-path-separator File/pathSeparator          ; System path separator (":" or ";")

        ;; Configuration map bundling the real dependencies for the pure functions.
        config {:getenv-fn      real-getenv
                :read-file-fn   real-read-file
                :is-file-fn     real-is-file
                :path-separator os-path-separator}

        ;; --- Configuration ---
        vmoptions-file-path "engine.vmoptions"      ; Default location of the options file
        mirth-launcher-jar "mirth-server-launcher.jar" ; Hardcoded target application JAR
        main-class "com.mirth.connect.server.launcher.MirthLauncher" ; Hardcoded target main class

        ;; --- State Management for Child Process ---
        process-atom (atom nil) ; Holds the launched Process object, nil if not running. Used by shutdown hook.
        shutting-down?-atom (atom false) ; Flag to prevent shutdown hook race conditions.

        ;; --- Step 1: Parse vmoptions file ---
        ;; Use `real-is-file` to check existence before parsing.
        parse-result (if (real-is-file vmoptions-file-path)
                       ;; File exists, parse it with an empty initial classpath.
                       (parse-vmoptions vmoptions-file-path "" config)
                       ;; File doesn't exist or isn't a file, create a default 'ok' result with a warning.
                       {:ok? true :options [] :classpath "" :java-cmd-path nil :warnings [(str "vmoptions file not found or not a file: " vmoptions-file-path)]})

        ;; Log any warnings generated during parsing (e.g., included file issues).
        _ (doseq [warning (:warnings parse-result)] (println "WARNING:" warning))

        ;; Optional: Could add a check here to exit if `(:ok? parse-result)` is false,
        ;; indicating a fatal parsing error like the root vmoptions file not being found
        ;; by `read-file-fn` inside `parse-vmoptions`. Currently, it proceeds even on error,
        ;; using potentially incomplete options/classpath.

        ;; --- Step 2: Determine Final Java Executable Path ---
        ;; Prioritize the path from the `-java-cmd` directive if it was present in vmoptions.
        java-cmd-directive-path (when (:ok? parse-result) ; Only consider if parsing itself was okay.
                                  (let [raw-path (get parse-result :java-cmd-path)]
                                    ;; Ensure the path from the directive is not nil or blank.
                                    (when (and raw-path (not (str/blank? raw-path)))
                                      raw-path)))

        ;; Decide which Java executable path to use.
        final-java-executable (if (and java-cmd-directive-path (real-file-exists java-cmd-directive-path))
                                ;; If -java-cmd path was provided AND the file actually exists, use it.
                                (do (println (str "Using Java executable from -java-cmd directive: " java-cmd-directive-path))
                                    java-cmd-directive-path)
                                ;; Otherwise, fall back to standard determination (JAVA_HOME or default 'java').
                                (let [determined-path (determine-java-executable real-getenv real-file-exists os-file-separator)]
                                  ;; Log appropriately based on whether an invalid directive was present.
                                  (if java-cmd-directive-path
                                    (println (str "WARNING: Path from -java-cmd ('" java-cmd-directive-path "') not found or invalid. Using determined Java executable: " determined-path))
                                    (println (str "Using determined Java executable: " determined-path)))
                                  determined-path)) ; Use the determined path

        ;; --- Step 3: Extract other results and Build Command ---
        ;; Get the parsed JVM options. Default to empty list if parsing failed.
        vm-options (if (:ok? parse-result) (:options parse-result) [])
        ;; Get the classpath constructed during parsing. Default to empty string if parsing failed.
        parsed-classpath (if (:ok? parse-result) (:classpath parse-result) "")

        ;; Construct the final classpath string: Prepend the specific launcher JAR.
        final-classpath (if (str/blank? parsed-classpath)
                          mirth-launcher-jar ; If vmoptions didn't specify any CP, use only mirth-launcher-jar JAR.
                          (str mirth-launcher-jar os-path-separator parsed-classpath)) ; Prepend mirth-launcher-jar otherwise.

        ;; Assemble the complete command vector using the pure helper function.
        command (build-command-list final-java-executable vm-options final-classpath main-class args)

        ;; --- Side Effects Execution: Launching and Management ---
        ;; Log the command that will be executed.
        _ (println "Launching Engine with command:" (str/join " " command))

        ;; Setup a JVM shutdown hook. This runs if the *launcher* JVM is terminated (e.g., Ctrl+C).
        shutdown-hook (Thread. (fn []
                                 ;; Set flag to indicate shutdown is in progress (for finally block).
                                 (reset! shutting-down?-atom true)
                                 ;; If the child process reference exists...
                                 (when-let [proc @process-atom]
                                   (println "\nLauncher shutting down, attempting to terminate Engine process...")
                                   (try
                                     ;; Attempt to terminate the child process.
                                     (.destroy proc)
                                     (catch Exception e (println "ERROR during shutdown hook:" (.getMessage e)))))))
        _ (.addShutdownHook (Runtime/getRuntime) shutdown-hook)

        ;; Variable to hold the exit code of the child process.
        exit-code (try
                    ;; --- Launch the Child Process ---
                    (let [;; Create a ProcessBuilder with the command list.
                          ;; .inheritIO() redirects child's stdout/stderr/stdin to launcher's streams.
                          process (.start (.inheritIO (ProcessBuilder. ^java.util.List command)))]
                      ;; Store the running Process object in the atom so the shutdown hook can access it.
                      (reset! process-atom process)
                      ;; Wait for the child process to complete and get its exit code.
                      ;; This blocks the launcher thread until the server exits.
                      (.waitFor process))
                    ;; --- Handle Launch Errors ---
                    (catch IOException e
                      (println (str "ERROR: Could not start Engine process: " (.getMessage e)))
                      (println "Check java executable path, JAR path validity, and command details:")
                      (println (str/join " " command))
                      1) ; Return a non-zero exit code indicating launch failure.
                    ;; --- Cleanup ---
                    (finally
                      ;; Clear the process atom now that the process has terminated or failed to start.
                      (reset! process-atom nil)
                      ;; Remove the shutdown hook *if* we are not currently exiting *because* of the shutdown hook.
                      ;; This prevents errors if the hook tries to remove itself while running.
                      (when (not @shutting-down?-atom)
                        (try
                          (.removeShutdownHook (Runtime/getRuntime) shutdown-hook)
                          ;; Catch exception if hook is already running or already removed.
                          (catch IllegalStateException _)))))]

    ;; Exit the launcher JVM, propagating the exit code from the child process (or 1 if launch failed).
    (System/exit exit-code)))
