 (ns fourteatoo.httpad.core
  (:gen-class)
  (:require [mount.core :as mount]
            [fourteatoo.httpad.server]
            [unilog.config :as unilog]
            [clojure.tools.logging :as log]))

(defn- init-logging! []
  (let [log-dir (str (System/getProperty "user.home") "/.local/state")
        log-file (str log-dir "/httpad.log")]
    (.mkdirs (java.io.File. log-dir))
    (unilog/start-logging!
     {:level :info
      :console true
      #_#_:appenders [{:type :rolling
                   :file log-file
                   ;; :pattern "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
                   ;; :max-history 5
                   #_#_:max-file-size "5MB"}]})))

(defn -main [& args]
  (init-logging!)
  (log/info "Initializing Httpad Session Daemon...")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(mount/stop)))
  (mount/start))


(comment
  (mount/stop))
