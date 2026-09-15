(ns fourteatoo.httpad.rt
  (:require [fourteatoo.httpad.log :as log]
            [mount.core :as mount]))

(defn- add-shutdown-hook [f]
  (.addShutdownHook (Runtime/getRuntime) (Thread. f)))

(def exit? (promise))

(defn arm-exit-hooks []
  (add-shutdown-hook (fn []
                       (log/info "Shutting down application")
                       (mount/stop))))

(defmacro daemon [& body]
  `(future
     (try
       (do ~@body)
       (catch Exception e#
         (log/fatal e# "Exception in daemon")
         (deliver exit? e#)))))
