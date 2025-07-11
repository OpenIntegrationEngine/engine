;;;
;;; SPDX-FileCopyrightText: 2025 Tony Germano tony@germano.name
;;;
;;; SPDX-License-Identifier: MPL-2.0
;;;

(defproject org.openintegrationengine/engine "0.1.0-SNAPSHOT"
  :description "A native engine for launching Java applications with advanced .vmoptions support"
  :url "https://github.com/OpenIntegrationEngine/engine"
  :license {:name "Mozilla Public License Version 2.0"
            :url "https://mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]]
  :main org.openintegrationengine.engine.server.launcher
  :aot :all

  :uberjar-name "engine.jar"

  :profiles {:dev {:resource-paths ["resources"]}})
