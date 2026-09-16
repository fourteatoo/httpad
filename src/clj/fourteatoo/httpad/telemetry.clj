(ns fourteatoo.httpad.telemetry
  (:require [clojure.core.async :as a]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [fourteatoo.httpad.log :as log]
            [mount.core :refer [defstate]]
            [fourteatoo.httpad.oshi :as oshi]))


(defonce active-clients (atom {}))
(defonce latest-metrics (atom {}))

(defonce telemetry-chan (a/chan (a/sliding-buffer 10)))
(defonce telemetry-mult (a/mult telemetry-chan))


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

(defn- start-sampler [interval-ms metrics-fn]
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

(defn- stop-sampler [stop-chan]
  (when stop-chan
    (a/close! stop-chan)))

(defn- disk-free-stats []
  (->> (oshi/filesystem-stats)
       (reduce (fn [m vol]
                 (assoc m (:mount vol) (Math/round (:used-pct vol))))
               {})
       (into {})))

(defn- start-telemetry-runners []
  (log/info "Starting system telemetry samplers")
  [(start-sampler 1700 #(hash-map :cpu {:load (Math/round (oshi/cpu-load))
                                        :temp (oshi/cpu-temperature)}
                                  :mem-used (Math/round (:used-pct (oshi/memory-stats)))))
   (start-sampler 13000 #(hash-map :disk-free (disk-free-stats)))])

(defn- stop-telemetry-runners [telemetry-runners]
  (log/info "Stopping system telemetry samplers")
  (run! stop-sampler telemetry-runners))

(defstate telemetry-runners
  :start (start-telemetry-runners)
  :stop (stop-telemetry-runners telemetry-runners))
