(ns fourteatoo.httpad.focus
  (:require [clojure.core.async :as async]
            [fourteatoo.httpad.process :as proc]
            [fourteatoo.httpad.config :refer [config]]
            [mount.core :as mount]
            [fourteatoo.httpad.telemetry :as telemetry]
            [clojure.java.shell :as shell]
            [clojure.string :as s]))


(def window-id-regex #"_NET_ACTIVE_WINDOW\(WINDOW\): window id # (0x[0-9a-fA-F]+)")
(def wm-class-regex #"WM_CLASS\(STRING\) = \"([^\"]+)\", \"([^\"]+)\"")

(defn window-class->section-id [window]
  (let [class->id (->> (:sections config)
                       (map (fn [{:keys [id window-class]}]
                              [(or window-class id) id]))
                       (into {}))]
    (class->id window)))

(defn- parse-window-id [line]
  (when-let [[_ id-hex] (re-find window-id-regex line)]
    id-hex))

(defn update [target-id]
  (telemetry/broadcast {:type :active-section 
                        :section target-id}))

(defn identify-window
  "Queries `xprop` for WM_CLASS given a window ID string (e.g. '0x03e00003').
   Returns {:instance ... :class ...} or nil."
  [window-id]
  (let [{:keys [exit out err]} (shell/sh "xprop" "-id" window-id "WM_CLASS")]
    (when (and (zero? exit) (not (s/blank? out)))
      (when-let [[_ instance class-name] (re-find wm-class-regex out)]
        {:instance instance
         :id window-id
         :class class-name}))))

(defn start-active-window-watcher
  []
  (let [{:keys [out-ch stop]} (proc/start-process-stream
                                 ["xprop" "-spy" "-root" "_NET_ACTIVE_WINDOW"])
        event-ch (async/chan (async/sliding-buffer 16))]
    ;; Plain background thread — blocking shell/sh directly is completely fine here
    (future
      (loop []
        (when-let [line (async/<!! out-ch)]
          (when-let [win-id (parse-window-id line)]
            (when-let [win (identify-window win-id)]
              (async/>!! event-ch win)))
          (recur)))
      (async/close! event-ch))
    {:event-ch event-ch
     :stop stop}))

(mount/defstate active-window-watcher
  :start
  (let [{:keys [event-ch stop]} (start-active-window-watcher)]
    ;; Consume window change events asynchronously
    (async/go-loop []
      (if-let [win (async/<! event-ch)]
        (do
          (println "Active Window Changed:" win)
          ;; Forward to WebSocket client / App State
          (when-let [section-id (window-class->section-id (:class win))]
            (update section-id))
          (recur))
        (println "XProp event channel closed.")))
    ;; Return state record with stop fn for Mount lifecycle teardown
    {:stop stop})

  :stop
  (when-let [stop (:stop active-window-watcher)]
    (stop)))
