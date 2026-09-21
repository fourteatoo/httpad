(ns fourteatoo.httpad.executor
  (:require [clojure.java.shell :refer [sh]]
            [diehard.core :as dh]
            [fourteatoo.httpad.log :as log]
            [fourteatoo.httpad.config :refer [config]]
            [fourteatoo.httpad.mqtt :as mqtt]
            [clojure.string :as s]))


(defn- lookup-command
  "Finds the command in configuration matching action-id."
  [action-path]
  (or (get-in config (concat [:action-index] action-path [:default]))
      (get-in config (concat [:action-index] action-path))))

(defmulti execute-command :type)

(defmethod execute-command :mqtt
  [{:keys [topic message params]}]
  (mqtt/publish topic (or message
                          (first params)
                          "")))

(defmethod execute-command :shell
  [{:keys [command params]}]
  (let [{:keys [exit out err]} (sh "sh" "-c"
                                   (cond-> command
                                     params (str " " (s/join " " params) )))]
    (if (zero? exit)
      (when-not (clojure.string/blank? out)
        (log/debug (str "Command '" command "' stdout: " out)))
      (log/error (str "Command '" command "' failed with exit code " exit ": " err)))))

(defmethod execute-command :default
  [cmd]
  (throw (ex-info "unrecognised command" {:cmd cmd})))

(dh/defratelimiter command-rate-limit {:rate 3})

(defn execute
  "Executes a command asynchronously."
  [action]
  (dh/with-rate-limiter {:ratelimiter command-rate-limit}
    (let [cmd (lookup-command (:action action))
          cmd (if (string? cmd)
                {:type :shell :command cmd}
                cmd)]
      ;; Future handles non-blocking execution
      (if cmd
        (future
          (try
            (log/info "Executing command for" action ": " cmd)
            (execute-command (cond-> cmd (:params action) (assoc :params (:params action))))
            (catch Exception e
              (log/error e (str "Failed to dispatch command for" action)))))
        (log/warn "No command configured for action-id" action)))))
