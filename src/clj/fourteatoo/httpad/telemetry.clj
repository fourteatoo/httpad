(ns fourteatoo.httpad.telemetry
  (:require [clojure.core.async :as a]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]))

;; -----------------------------------------------------------------------------
;; 1. Private State & Channels
;; -----------------------------------------------------------------------------

(defonce active-clients (atom {}))
(defonce latest-metrics (atom {}))

(defonce telemetry-chan (a/chan (a/sliding-buffer 10)))
(defonce telemetry-mult (a/mult telemetry-chan))

;; -----------------------------------------------------------------------------
;; 2. Client Lifecycle API
;; -----------------------------------------------------------------------------

(defn register-client
  "Registers a new WebSocket channel, taps it to the mult, and sends initial metrics."
  [ch client-async-chan]
  (swap! active-clients assoc ch client-async-chan)
  (a/tap telemetry-mult client-async-chan)
  ;; Send initial snapshot if available
  (when (seq @latest-metrics)
    (a/>!! client-async-chan {:type :telemetry :metrics @latest-metrics})))

(defn unregister-client
  "Untaps, closes, and removes a client's async channel on disconnect."
  [ch]
  (when-let [client-async-chan (get @active-clients ch)]
    (a/untap telemetry-mult client-async-chan)
    (a/close! client-async-chan)
    (swap! active-clients dissoc ch)))

(defn broadcast
  "Pushes an event onto the telemetry channel, fanning out to all tapped
  clients."
  [event-map]
  (a/>!! telemetry-chan event-map))


;; -----------------------------------------------------------------------------
;; 3. Samplers & Loops
;; -----------------------------------------------------------------------------

(defn sample-cpu []
  (try
    (let [res (shell/sh "sh" "-c" "top -bn1 | grep 'Cpu(s)' | awk '{print $2}'")]
      (Double/parseDouble (str/trim (:out res))))
    (catch Exception _ 0.0)))

(defn sample-memory []
  (try
    (let [res (shell/sh "sh" "-c" "free | grep Mem | awk '{print $3/$2 * 100.0}'")]
      (Math/round (Double/parseDouble (str/trim (:out res)))))
    (catch Exception _ 0)))

(defn start-sampler [interval-ms metrics-fn]
  (let [stop-chan (a/chan)]
    (a/go-loop []
      (let [[_ port] (a/alts! [(a/timeout interval-ms) stop-chan])]
        (when-not (= port stop-chan)
          ;; Check the local active-clients atom directly
          (when (seq @active-clients)
            (let [metrics (metrics-fn)]
              (swap! latest-metrics merge metrics)
              (a/>! telemetry-chan {:type :telemetry :metrics metrics})))
          (recur))))
    stop-chan))

(defn stop-sampler [stop-chan]
  (when stop-chan
    (a/close! stop-chan)))

;; -----------------------------------------------------------------------------
;; 4. Mount Lifecycle
;; -----------------------------------------------------------------------------

(defstate telemetry-runners
  :start
  (do
    (log/info "Starting system telemetry sampler loops...")
    [(start-sampler 1000 #(hash-map :cpu-load (sample-cpu)
                                    :mem-used (sample-memory)))
     (start-sampler 10000 #(hash-map :disk-free 72))])
  
  :stop
  (do
    (log/info "Stopping system telemetry sampler loops...")
    (run! stop-sampler telemetry-runners)))
