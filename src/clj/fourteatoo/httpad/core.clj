(ns fourteatoo.httpad.core
  (:gen-class)
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [fourteatoo.httpad.browser :as browser]
   [fourteatoo.httpad.config :as c :refer [conf]]
   [fourteatoo.httpad.log :as log]
   [fourteatoo.httpad.network :as network]
   [fourteatoo.httpad.server :as server]
   [fourteatoo.httpad.mqtt :as mqtt]
   [mount.core :as mount :refer [defstate]]
   [fourteatoo.httpad.rt :as rt]
   [fourteatoo.httpad.qr :as qr]))


(def cli-options
  [["-b" "--launch-ui" "Open the UI automatically in the browser"]
   ["-c" "--config FILE" "Use FILE as configuration instead of ~/.httpad"]
   ["-p" "--port PORT" "Port number"
    :parse-fn parse-long]
   ["-v" "--verbose" "Increase logging verbosity"
    :default 0
    :update-fn inc]
   ["-h" "--help" "Show this"]])

(defn print-ui-urls [port]
  (let [lan-url (server/user-interface-url)]
    (qr/print-small-qr lan-url)
    (println (str "UI at " lan-url))
    (println (str "   or " (server/user-interface-url :host "localhost")))))

(defn usage [summary errors]
  (run! println errors)
  (println "usage: httpad [option] ...")
  (println summary)
  (System/exit 1))

;; NOTE: if namespaces are completely self-contained, we may miss to
;; require them, and even if we do require them, cljr may later remove
;; the dependency. As a consequence, mount will miss the dependency
;; and skip to start the states defined in those namespaces.  The
;; solution is either to use at least a function defined in the other
;; namespace, or move the state here altogether.  Thus the seemingly
;; useless logs below.

(defn start-program [options]
  (log/info "Starting HTTPAD")
  (rt/arm-exit-hooks)
  (mount/with-args options)
  (log/info (mount/start))
  (print-ui-urls (c/port))
  (println "\nHTTPAD running\ntype C-c to stop it")
  (when (conf :launch-ui)
    (browser/open-browser (str "http://localhost:" (c/port) "/index.html")))
  ;; WARNING: don't remove these logs!
  (log/info (str mqtt/mqtt-client))
  (log/info (str server/http-server))
  (deref rt/exit?))

(comment
  (mount/stop))

(defn -main [& args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)]
    (cond errors (usage summary errors)
          (:help options) (usage summary errors)
          :else (start-program options))))


