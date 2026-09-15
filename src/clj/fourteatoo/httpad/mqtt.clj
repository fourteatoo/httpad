(ns fourteatoo.httpad.mqtt
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [fourteatoo.httpad.config :refer [config]]
            [fourteatoo.httpad.telemetry :as telemetry]
            [mount.core :refer [defstate]])
  (:import [org.eclipse.paho.client.mqttv3
            MqttClient
            MqttConnectOptions
            MqttCallbackExtended ; Use Extended callback interface
            MqttMessage]
           [org.eclipse.paho.client.mqttv3.persist MemoryPersistence]))

(defn- parse-payload [^MqttMessage message]
  (let [raw (str/trim (String. (.getPayload message) "UTF-8"))]
    (try
      (json/parse-string raw true)
      (catch Exception _
        (try
          (if (re-matches #"-?\d+(\.\d+)?" raw)
            (Double/parseDouble raw)
            raw)
          (catch Exception _ raw))))))

(defn- process-message! [topic message topic-config out-chan]
  (when-let [mapping (get topic-config topic)]
    (let [payload (parse-payload message)]
      (cond
        ;; Direct scalar or path mapping (e.g., "stat/..." -> :washing-machine-state or [:kids-room-devices :light1])
        (or (keyword? mapping) (vector? mapping))
        (let [target-path (if (vector? mapping) mapping [mapping])]
          (async/put! out-chan {:type    :telemetry
                                :metrics (assoc-in {} target-path payload)}))
        ;; JSON key path extraction map
        (map? mapping)
        (if (map? payload)
          (let [extracted-metrics
                (reduce (fn [acc [path metric-key]]
                          (let [val (get-in payload path)]
                            (if (some? val)
                              (assoc-in acc (if (vector? metric-key) metric-key [metric-key]) val)
                              acc)))
                        {}
                        mapping)]
            (when (seq extracted-metrics)
              (log/debugf "MQTT metrics [%s]: %s" topic extracted-metrics)
              (async/put! out-chan {:type    :telemetry
                                    :metrics extracted-metrics})))
          (log/warnf "MQTT topic %s configured for JSON extraction, but payload was not a map: %s" topic payload))
        :else
        (log/warnf "Invalid topic configuration for %s: %s" topic mapping)))))

(defn- create-callback [^MqttClient client topic-config out-chan]
  (reify MqttCallbackExtended
    (connectionLost [_ cause]
      (log/warnf "MQTT connection lost: %s. Paho auto-reconnect will attempt recovery..." 
                 (some-> cause .getMessage)))

    (connectComplete [_ reconnect server-uri]
      (if reconnect
        (log/infof "MQTT reconnected to %s. Resubscribing to topics..." server-uri)
        (log/infof "MQTT initial connection established to %s." server-uri))
      
      ;; CRITICAL: Resubscribe to all configured topics on every connect/reconnect
      (try
        (doseq [topic (keys topic-config)]
          (log/infof "Subscribing to MQTT topic: %s" topic)
          (.subscribe client topic 0))
        (catch Exception e
          (log/error e "Failed to subscribe to MQTT topics after reconnect"))))

    (messageArrived [_ topic message]
      (try
        (process-message! topic message topic-config out-chan)
        (catch Exception e
          (log/error e "Error processing incoming MQTT payload on topic:" topic))))

    (deliveryComplete [_ _token] nil)))

(defn start-subscriber! [mqtt-cfg out-chan]
  (let [{:keys [host port user password topics client-id]
         :or   {host "127.0.0.1"
                port 1883
                client-id "httpad-server"}} mqtt-cfg]
    (when (seq topics)
      (let [broker-url  (str "tcp://" host ":" port)
            persistence (MemoryPersistence.)
            client      (MqttClient. broker-url client-id persistence)
            options     (doto (MqttConnectOptions.)
                          (.setCleanSession true)
                          (.setAutomaticReconnect true))]

        (when (and user password)
          (.setUserName options user)
          (.setPassword options (.toCharArray password)))

        ;; Set callback BEFORE connect so connectComplete handles initial subscriptions too
        (.setCallback client (create-callback client topics out-chan))
        
        (log/infof "Connecting MQTT client to broker at %s..." broker-url)
        (.connect client options)

        client))))

(defn stop-subscriber! [^MqttClient client]
  (when (and client (.isConnected client))
    (log/info "Disconnecting MQTT subscriber...")
    (try
      (.disconnect client)
      (.close client)
      (catch Exception e
        (log/error e "Error stopping MQTT client")))))

(defn publish!
  "Publishes a payload string to a given MQTT topic."
  [^MqttClient client topic payload]
  (if (and client (.isConnected client))
    (try
      (let [msg (MqttMessage. (.getBytes (if (map? payload)
                                           (json/generate-string payload)
                                           (str payload))
                                         "UTF-8"))]
        (.setQos msg 1)
        (.publish client topic msg)
        (log/debugf "Published MQTT message to %s: %s" topic payload))
      (catch Exception e
        (log/error e "Failed to publish MQTT message to topic:" topic)))
    (log/warnf "Cannot publish to %s: MQTT client is disconnected" topic)))

(defstate mqtt-subscriber
  :start (when-let [mqtt-cfg (:mqtt config)]
           (when (:enabled mqtt-cfg true)
             (log/info "Initializing MQTT subscriber component...")
             (start-subscriber! mqtt-cfg telemetry/telemetry-chan)))
  :stop  (when-let [client mqtt-subscriber]
           (stop-subscriber! client)))
