(ns fourteatoo.httpad.log
  (:require
   [unilog.config :refer [start-logging!]]
   [clojure.tools.logging :as log]
   [mount.core :as mount :refer [defstate]]
   [fourteatoo.httpad.config :refer [conf]]))


(defn setup-logging [& [config]]
  (let [default-config {:level "info" :console true}]
    (start-logging! (merge default-config config))))

(defstate logging-service
  :start (setup-logging (conf :logging)))

(defmacro log [& args]
  `(log/log ~@args))

(defmacro trace [& args]
  `(log/trace ~@args))

(defmacro debug [& args]
  `(log/debug ~@args))

(defmacro info [& args]
  `(log/info ~@args))

(defmacro warn [& args]
  `(log/warn ~@args))

(defmacro error [& args]
  `(log/error ~@args))

(defmacro fatal [& args]
  `(log/fatal ~@args))

(defmacro spy [& args]
  `(log/spy ~@args))
