(ns fourteatoo.httpad.ws
  (:require [cognitect.transit :as t]
            [fourteatoo.httpad.util :as util]
            [fourteatoo.httpad.state :as state]))

(defonce ws-conn (atom nil))
(defonce reconnect-delay (atom 1000)) ; Initial reconnect delay (1s)

;; --- Transit Serialization ---
(def ^:private transit-writer (t/writer :json))
(def ^:private transit-reader (t/reader :json))

(defn- encode-transit [data]
  (t/write transit-writer data))

(defn- decode-transit [raw-msg]
  (t/read transit-reader raw-msg))

(defn- parse-transit [response-promise]
  (.then response-promise
         (fn [res]
           (if (.-ok res)
             (.then (.text res) #(decode-transit %))
             (js/Promise.reject res)))))

;; --- WebSocket Connection Management ---
(declare connect-ws!)

(defn- schedule-reconnect! []
  (let [delay @reconnect-delay]
    (js/console.log (str "Scheduling WS reconnect in " delay "ms"))
    (js/setTimeout connect-ws! delay)
    (reset! reconnect-delay (min 10000 (* delay 1.5)))))

(defn connect-ws! []
  (let [host (.. js/window -location -host)
        protocol (if (= (.. js/window -location -protocol) "https:") "wss:" "ws:")
        ws-url (str protocol "//" host "/ws")
        ws (js/WebSocket. ws-url)]

    (set! (.-onopen ws)
          (fn []
            (js/console.log "WebSocket connection established")
            (swap! state/state assoc :ws-connected? true)
            (reset! reconnect-delay 1000)))

    (set! (.-onclose ws)
          (fn [evt]
            (js/console.log "WebSocket closed:" (.-code evt))
            (swap! state/state assoc :ws-connected? false)
            (schedule-reconnect!)))

    (set! (.-onerror ws)
          (fn [err]
            (js/console.error "WebSocket error:" err)
            (.close ws)))

    (set! (.-onmessage ws)
          (fn [evt]
            (try
              (let [msg (decode-transit (.-data evt))]
                (js/console.log "Received WS message:" msg))
              (catch :default e
                (js/console.error "Error decoding Transit message:" e)))))

    (reset! ws-conn ws)))

(defn send-action! [cmd-path]
  (util/haptic!)
  (when-let [ws @ws-conn]
    (if (= (.-readyState ws) js/WebSocket.OPEN)
      (.send ws (encode-transit {:action cmd-path}))
      (js/console.warn "WebSocket not open. Action dropped:" cmd-path))))

;; --- API Handlers ---
(defn fetch-config! []
  (-> (js/fetch "/api/config"
                #js {:headers #js {"Accept" "application/transit+json"}})
      parse-transit
      (.then (fn [data]
               (swap! state/state assoc
                      :sections (:sections data)
                      :auth-status :authenticated)
               (connect-ws!)))
      (.catch (fn [err]
                (js/console.error "Failed to fetch config:" err)
                (swap! state/state assoc :auth-status :unauthenticated)))))

(defn check-auth! []
  (-> (js/fetch "/api/auth-status")
      parse-transit
      (.then (fn [data]
               (let [js-data (js->clj data :keywordize-keys true)]
                 (if (:authenticated? js-data)
                   (do
                     (swap! state/state assoc :auth-status :authenticated)
                     (fetch-config!))
                   (swap! state/state assoc :auth-status :unauthenticated)))))
      (.catch (fn [_]
                (swap! state/state assoc :auth-status :unauthenticated)))))

(defn login! [evt]
  (.preventDefault evt)
  (let [passphrase (:passphrase @state/state)]
    (-> (js/fetch "/api/login"
                  #js {:method "POST"
                       :headers #js {"Content-Type" "application/transit+json"
                                     "Accept" "application/transit+json"}
                       :body (encode-transit {:passphrase passphrase})})
        parse-transit
        (.then (fn [data]
                 (if (or (:success data) (:authenticated? data) (= data true))
                   (do
                     (swap! state/state assoc
                            :auth-status :authenticated
                            :auth-error nil
                            :passphrase "")
                     (fetch-config!))
                   (swap! state/state assoc :auth-error "Invalid Passphrase"))))
        (.catch (fn [err]
                  (js/console.error "Login failed:" err)
                  (swap! state/state assoc :auth-error "Invalid Passphrase or Server Error"))))))
