(ns fourteatoo.httpad.executor
  (:require [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log]
            [fourteatoo.httpad.config :refer [config]]
            [fourteatoo.httpad.mqtt :as mqtt]))

;; Tracks last execution timestamp (in ms) per button ID
(defonce ^:private last-executed (atom {}))

(defn- rate-limited?
  "Returns true if action-id was executed within cooldown-ms window."
  [action-id cooldown-ms]
  (let [now (System/currentTimeMillis)
        last-time (get @last-executed action-id 0)]
    (if (< (- now last-time) cooldown-ms)
      true
      (do
        (swap! last-executed assoc action-id now)
        false))))

(defn- lookup-command
  "Finds the command vector in configuration matching action-id."
  [action-path]
  (or (get-in config (concat [:action-index] action-path [:default]))
      (get-in config (concat [:action-index] action-path))))

(defmulti execute-command :type)

(defmethod execute-command :mqtt
  [{:keys [topic message]}]
  (mqtt/publish! mqtt/mqtt-subscriber topic (or message "")))

(defmethod execute-command :shell
  [{:keys [command]}]
  (let [{:keys [exit out err]} (sh "sh" "-c" command)]
    (if (zero? exit)
      (when-not (clojure.string/blank? out)
        (log/debugf "Command '%s' stdout: %s" command out))
      (log/errorf "Command '%s' failed (exit code %d): %s" command exit err))))

(defmethod execute-command nil
  [cmd]
  (execute-command {:type :shell :command cmd}))

(defn execute
  "Executes a button command asynchronously behind a rate limiter.
   Default cooldown is 500ms per action-id."
  ([action-id]
   (execute action-id 400))
  ([action-id cooldown-ms]
   (if (rate-limited? action-id cooldown-ms)
     (log/warnf "Rate limit hit for action '%s'. Dropping execution." action-id)
     (if-let [cmd (lookup-command action-id)]
       ;; Future handles non-blocking execution off the http-kit worker thread
       (future
         (try
           (log/infof "Executing command for %s: %s" action-id cmd)
           (execute-command cmd)
           (catch Exception e
             (log/error e (format "Failed to dispatch command for %s" action-id)))))
       (log/warnf "No command configured for action-id: %s" action-id)))))
