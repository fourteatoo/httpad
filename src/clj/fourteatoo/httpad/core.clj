(ns fourteatoo.httpad.core
  (:gen-class)
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [fourteatoo.httpad.browser :as browser]
   [fourteatoo.httpad.config :as c :refer [opt conf]]
   [fourteatoo.httpad.log :as log]
   [fourteatoo.httpad.network :as network]
   [fourteatoo.httpad.server]
   [fourteatoo.httpad.focus]
   [mount.core :as mount]
   [fourteatoo.httpad.rt :as rt]))


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
  (let [local-url (str "http://localhost:" port "/index.html")
        lan-ip    (network/get-ip-address)
        lan-url   (str "http://" lan-ip ":" port "/index.html")]
    (println "UI is running on port" port)
    (println (str "Local URL: " local-url))
    (println (str "LAN URL:   " lan-url))))

(defn usage [summary errors]
  (run! println errors)
  (println "usage: httpad [option] ...")
  (println summary)
  (System/exit 1))

(defn start-program [options]
  (binding [c/options options]
    (log/info "Starting HTTPAD")
    (rt/arm-exit-hooks)
    (log/info (mount/start))
    (print-ui-urls (c/port))
    (println "HTTPAD running\ntype C-c to stop the program")
    (when (opt :launch-ui)
      (browser/open-browser (str "http://localhost:" (c/port) "/index.html")))
    (deref rt/exit?)))

(defn -main [& args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)]
    (cond errors (usage summary errors)
          (:help options) (usage summary errors)
          :else (start-program options))))


(comment
  (mount/stop))
