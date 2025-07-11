;;;
;;; SPDX-FileCopyrightText: 2025 Tony Germano tony@germano.name
;;;
;;; SPDX-License-Identifier: MPL-2.0
;;;

;;;;
;; File: launcher_test.clj
;; Purpose: Unit tests for the OIE Engine Launcher (`launcher.clj`).
;;
;; These tests verify the behavior of the core, pure logic helper functions
;; defined in the `org.openintegrationengine.engine.server.launcher` namespace.
;;
;; Testing Strategy:
;; - Focus on unit testing the deterministic helper functions (`substitute-env-vars`,
;;   `parse-vmoptions`, `determine-java-executable`, `build-command-list`).
;; - Employ mocking for external dependencies (file system access, environment variables)
;;   to isolate the logic under test and ensure predictable, fast execution.
;; - The `-main` function, being primarily concerned with orchestrating side effects,
;;   is *not* unit tested here. Its behavior should be validated through integration tests
;;   if necessary, as its core logic components are already tested via the helpers.
;;;;
(ns org.openintegrationengine.engine.server.launcher-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            ;; Require the namespace under test, aliased for clarity and brevity.
            [org.openintegrationengine.engine.server.launcher :as launcher])
  (:import [java.io File FileNotFoundException])) ; Import exception for mock file reading tests.

;; === Tests for substitute-env-vars ===
;; Verifies the environment variable substitution logic.

(deftest substitute-env-vars-test
  ;; Define a simple mock environment (map) and a getter function for it.
  (let [mock-env {"EXISTING_VAR" "VAR_VALUE"
                  "OTHER_VAR"    "OTHER_VALUE"}
        ;; This function simulates System/getenv for the test.
        mock-getenv (fn [var-name] (get mock-env var-name))]

    (testing "String with no variables - should remain unchanged"
      (is (= "hello world" (launcher/substitute-env-vars "hello world" mock-getenv))))

    (testing "String with existing variable - should substitute value"
      (is (= "hello VAR_VALUE" (launcher/substitute-env-vars "hello ${EXISTING_VAR}" mock-getenv))))

    (testing "String with multiple existing variables"
      (is (= "VAR_VALUE meets OTHER_VALUE" (launcher/substitute-env-vars "${EXISTING_VAR} meets ${OTHER_VAR}" mock-getenv))))

    (testing "String with non-existent variable - should substitute empty string"
      (is (= "hello " (launcher/substitute-env-vars "hello ${NON_EXISTENT_VAR}" mock-getenv))))

    (testing "String with mixed existing and non-existent variables"
      (is (= "VAR_VALUE and " (launcher/substitute-env-vars "${EXISTING_VAR} and ${NON_EXISTENT_VAR}" mock-getenv))))

    (testing "Empty string input - should return empty string"
      (is (= "" (launcher/substitute-env-vars "" mock-getenv))))))

;; === Tests for parse-vmoptions ===
;; Verifies the core vmoptions parsing logic, including directives and includes.
;; This uses a more extensive mocking setup due to file I/O and env var dependencies.

(deftest parse-vmoptions-test
  ;; --- Mocking Setup ---
  ;; Simulate a file system using a map from path to content.
  (let [mock-files {"main.vmoptions" (str "-Xmx512m\n"
                                          "-Dprop=${ENV_PROP}\n" ; Substitution test
                                          "-java-cmd /specific/java\n" ; Will be overridden by include
                                          "-include-options included.vmoptions\n" ; Include directive
                                          "-classpath/a /main/append") ; Classpath append

                    "included.vmoptions" (str "# A comment\n"
                                              "-XincOpt\n" ; Option from included file
                                              "-java-cmd /included/java\n" ; Overrides main's -java-cmd
                                              "-classpath/p /included/prepend") ; Classpath prepend

                    "override.vmoptions" (str "-include-options included.vmoptions\n" ; Include sets -java-cmd first
                                              "-java-cmd /override/java") ; This should take precedence

                    "env_java.vmoptions" "-java-cmd ${JAVA_CMD_PATH}" ; -java-cmd value from env var
                    "cp_replace.vmoptions" "-classpath /new/path" ; Replace classpath directive
                    "cp_append.vmoptions" "-classpath/a /append"  ; Append classpath directive
                    "cp_prepend.vmoptions" "-classpath/p /prepend" ; Prepend classpath directive
                    "empty.vmoptions" "" ; Empty file test case
                    "only_comments.vmoptions" "# line 1\n   # line 2"} ; Comments-only test case

        ;; Simulate environment variables needed for substitutions.
        mock-env {"ENV_PROP" "env-value"
                  "JAVA_CMD_PATH" "/env/java/path"}

        ;; Mock implementations of the functions required by the `config` map:
        ;; Simulates reading a file from our mock file system.
        mock-read-file (fn [path]
                         (if-let [content (get mock-files path)]
                           content
                           (throw (FileNotFoundException. (str "Mock file not found: " path)))))
        ;; Simulates getting an environment variable from our mock environment.
        mock-getenv (fn [var-name] (get mock-env var-name))
        ;; Simulates checking if a path corresponds to a file in our mock system.
        mock-is-file (fn [path] (contains? mock-files path))

        ;; The `config` map passed to the parser, using our mock functions.
        ;; Uses Unix path separator for consistency in tests.
        test-config {:read-file-fn   mock-read-file
                     :getenv-fn      mock-getenv
                     :is-file-fn     mock-is-file
                     :path-separator ":"}] ; Use ':' for testing classpath logic


    (testing "Parsing basic file with includes, substitutions, and directive precedence"
      (let [result (launcher/parse-vmoptions "main.vmoptions" "initial/cp" test-config)]
        (is (:ok? result) "Parsing should succeed")
        ;; Options from both files, substitution applied.
        (is (= ["-Xmx512m" "-Dprop=env-value" "-XincOpt"] (:options result)))
        ;; Classpath: prepend from include, initial, append from main.
        (is (= "/included/prepend:initial/cp:/main/append" (:classpath result)))
        ;; Java command path from *included* file should win as it's processed last within its scope.
        (is (= "/included/java" (:java-cmd-path result)))
        ;; Should have a warning about the include.
        (is (= 1 (count (:warnings result))))
        (is (str/includes? (first (:warnings result)) "Included options from: included.vmoptions"))))

    (testing "Parsing file where a later -java-cmd overrides an included one"
      (let [result (launcher/parse-vmoptions "override.vmoptions" "" test-config)]
        (is (:ok? result))
        (is (= ["-XincOpt"] (:options result))) ; Options from include
        (is (= "/included/prepend" (:classpath result))) ; Classpath from include
        ;; The -java-cmd in override.vmoptions takes precedence over the one in included.vmoptions.
        (is (= "/override/java" (:java-cmd-path result)))
        (is (= 1 (count (:warnings result))))))

    (testing "Parsing file with -java-cmd specified via environment variable"
      (let [result (launcher/parse-vmoptions "env_java.vmoptions" "" test-config)]
        (is (:ok? result))
        (is (empty? (:options result)))
        (is (= "" (:classpath result)))
        ;; Path should be the substituted value from mock-env.
        (is (= "/env/java/path" (:java-cmd-path result)))
        (is (empty? (:warnings result)))))

    (testing "Parsing an empty vmoptions file"
      (let [result (launcher/parse-vmoptions "empty.vmoptions" "" test-config)]
        (is (:ok? result))
        (is (empty? (:options result)))
        (is (= "" (:classpath result)))
        (is (nil? (:java-cmd-path result))) ; No directive encountered
        (is (empty? (:warnings result)))))

    (testing "Parsing a file containing only comments and blank lines"
      (let [result (launcher/parse-vmoptions "only_comments.vmoptions" "" test-config)]
        (is (:ok? result))
        (is (empty? (:options result)))
        (is (= "" (:classpath result)))
        (is (nil? (:java-cmd-path result)))
        (is (empty? (:warnings result)))))

    (testing "Classpath directive: -classpath (Replace)"
      (let [result (launcher/parse-vmoptions "cp_replace.vmoptions" "old/path" test-config)]
        (is (:ok? result))
        (is (empty? (:options result)))
        ;; The initial "old/path" should be replaced entirely.
        (is (= "/new/path" (:classpath result)))))

    (testing "Classpath directive: -classpath/a (Append) to existing path"
      (let [result (launcher/parse-vmoptions "cp_append.vmoptions" "initial" test-config)]
        (is (:ok? result))
        (is (= "initial:/append" (:classpath result)))))

    (testing "Classpath directive: -classpath/a (Append) to empty initial path"
      (let [result (launcher/parse-vmoptions "cp_append.vmoptions" "" test-config)]
        (is (:ok? result))
        ;; Should just be the appended path.
        (is (= "/append" (:classpath result)))))

    (testing "Classpath directive: -classpath/p (Prepend) to existing path"
      (let [result (launcher/parse-vmoptions "cp_prepend.vmoptions" "initial" test-config)]
        (is (:ok? result))
        (is (= "/prepend:initial" (:classpath result)))))

    (testing "Classpath directive: -classpath/p (Prepend) to empty initial path"
      (let [result (launcher/parse-vmoptions "cp_prepend.vmoptions" "" test-config)]
        (is (:ok? result))
        ;; Should just be the prepended path.
        (is (= "/prepend" (:classpath result)))))

    (testing "Error handling: Included file specified but not found by mock is-file-fn"
      ;; Modify the config for this test case only, so is-file-fn always returns false.
      (let [config-no-include (assoc test-config :is-file-fn (constantly false))
            result (launcher/parse-vmoptions "main.vmoptions" "" config-no-include)]
        ;; Parsing of the main file itself should still succeed.
        (is (:ok? result))
        ;; Options from main file (with substitution) should be present.
        (is (= ["-Xmx512m" "-Dprop=env-value"] (:options result)))
        ;; Classpath append from main file should still work relative to initial "".
        (is (= "/main/append" (:classpath result)))
        ;; The -java-cmd from the main file should be effective as the include was skipped.
        (is (= "/specific/java" (:java-cmd-path result)))
        ;; A warning about the missing include should be generated.
        (is (= 1 (count (:warnings result))))
        (is (str/includes? (first (:warnings result)) "not a file or not found: 'included.vmoptions'"))))

    (testing "Error handling: Main vmoptions file not found"
      ;; Try parsing a path not present in `mock-files`.
      (let [result (launcher/parse-vmoptions "/non/existent/path.vmoptions" "initial/cp" test-config)]
        ;; ok? should be false, indicating a fatal error during parsing.
        (is (false? (:ok? result)))
        ;; Should indicate the specific error type and path.
        (is (= :file-not-found (:error result)))
        (is (= "/non/existent/path.vmoptions" (:path result)))
        ;; In case of failure, it should return the initial classpath passed in.
        (is (= "initial/cp" (:classpath result)))
        ;; Options should be empty.
        (is (empty? (:options result)))
        ;; java-cmd-path should be nil as parsing failed early.
        (is (nil? (:java-cmd-path result)))))))


;; === Tests for determine-java-executable ===
;; Verifies the logic for finding the default Java executable (based on JAVA_HOME or fallback).
;; Uses mock environment and file existence checks.

(deftest determine-java-executable-test
  (testing "JAVA_HOME set and corresponding bin/java exists"
    (let [mock-env {"JAVA_HOME" "/opt/java"}
          ;; Simulate only the expected java path existing.
          mock-exists #(= % "/opt/java/bin/java")]
      (is (= "/opt/java/bin/java"
             (launcher/determine-java-executable (fn [v] (get mock-env v)) mock-exists File/separator)))))

  (testing "JAVA_HOME set but corresponding bin/java does NOT exist"
    (let [mock-env {"JAVA_HOME" "/opt/java"}
          ;; Simulate no files existing.
          mock-exists (constantly false)]
      ;; Should fall back to the default "java".
      (is (= "java"
             (launcher/determine-java-executable (fn [v] (get mock-env v)) mock-exists File/separator)))))

  (testing "JAVA_HOME environment variable is not set"
    (let [mock-env {} ; Empty environment
          ;; Assume "java" would be found on PATH (mock doesn't need to check PATH).
          mock-exists (constantly true)]
      ;; Should use the default "java".
      (is (= "java"
             (launcher/determine-java-executable (fn [v] (get mock-env v)) mock-exists File/separator))))))

;; === Tests for build-command-list ===
;; Verifies the construction of the final command vector for ProcessBuilder.

(deftest build-command-list-test
  (testing "Basic command construction with all elements"
    (is (= ["/usr/bin/java" "-Xmx1g" "-Dprop=val" "-cp" "app.jar:/lib/*" "com.app.Main" "arg1" "--flag"]
           (launcher/build-command-list "/usr/bin/java"
                                        ["-Xmx1g" "-Dprop=val"]
                                        "app.jar:/lib/*"
                                        "com.app.Main"
                                        ["arg1" "--flag"]))))

  (testing "Command construction with no VM options"
    (is (= ["java" "-cp" "app.jar" "com.app.Main" "arg"]
           (launcher/build-command-list "java"
                                        [] ; Empty options
                                        "app.jar"
                                        "com.app.Main"
                                        ["arg"]))))

  (testing "Command construction with no pass-through arguments"
    (is (= ["java" "-Xmx512m" "-cp" "app.jar" "com.app.Main"]
           (launcher/build-command-list "java"
                                        ["-Xmx512m"]
                                        "app.jar"
                                        "com.app.Main"
                                        [])))) ; Empty args

  (testing "Command construction with only essential elements"
    (is (= ["java" "-cp" "main.jar" "com.app.Start"]
           (launcher/build-command-list "java"
                                        []
                                        "main.jar"
                                        "com.app.Start"
                                        [])))))

;; === Note on Testing `-main` ===
;;;;
;; The `-main` function is intentionally *not* covered by these unit tests.
;; Its primary role is orchestration: integrating the results from the helper
;; functions (which *are* tested here) and managing side effects like file I/O,
;; process execution (`ProcessBuilder`), and system interactions (`System/exit`,
;; shutdown hooks).
;;
;; Unit testing `-main` directly would require extensive mocking of Java's
;; `ProcessBuilder`, `Runtime`, `System`, etc., which becomes complex and brittle.
;;
;; The correctness of `-main` relies on the correctness of the helper functions
;; tested above. If end-to-end validation of the launcher executable is required,
;; it should be done via **integration tests** that execute the compiled JAR
;; in a controlled environment and verify its behavior and the state of the
;; launched child process.
;;;;
