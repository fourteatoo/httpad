(ns fourteatoo.httpad.ws
  (:require [cognitect.transit :as t]
            [fourteatoo.httpad.util :as util]
            [fourteatoo.httpad.state :as state]))

(defonce ws-conn (atom nil))

(defonce reconnect-delay (atom 1000))
(defonce reconnect-timer (atom nil))

(defn stop-reconnect-timer! []
  (when-let [timer-id @reconnect-timer]
    (js/clearTimeout timer-id)
    (reset! reconnect-timer nil))
  ;; Reset backoff delay for future logins
  (reset! reconnect-delay 1000))

(declare connect-ws!)

(defn- schedule-reconnect! []
  ;; Clear any previously scheduled timer first
  (stop-reconnect-timer!)
  (let [delay @reconnect-delay]
    (js/console.log (str "Scheduling WS reconnect in " delay "ms"))
    (let [timer-id (js/setTimeout connect-ws! delay)]
      (reset! reconnect-timer timer-id))
    (reset! reconnect-delay (min 10000 (* delay 1.5)))))


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

(defn deep-merge [a b]
  (merge-with (fn [x y]
                (if (and (map? x) (map? y))
                  (deep-merge x y)
                  y))
              a b))

(defn- handle-incoming-message [msg]
  (case (:type msg)
    :telemetry
    (swap! state/state update :telemetry deep-merge (:metrics msg))
    
    :server
    (swap! state/state assoc :server-url (:url msg))
    
    :active-section
    (when (some #(= (keyword (:id %))
                    (:section msg))
                (:sections @state/state))
      (swap! state/state assoc :active-tab (:section msg)))

    (js/console.log "Unhandled WS message type:" (:type msg))))

(defn connect-ws! []
  ;; Avoid opening duplicate sockets if one is already connecting/open
  (when-let [old-ws @ws-conn]
    (when (or (= (.-readyState old-ws) js/WebSocket.OPEN)
              (= (.-readyState old-ws) js/WebSocket.CONNECTING))
      (set! (.-onclose old-ws) nil)
      (.close old-ws)))

  (let [host (.. js/window -location -host)
        protocol (if (= (.. js/window -location -protocol) "https:") "wss:" "ws:")
        ws-url (str protocol "//" host "/ws")
        ws (js/WebSocket. ws-url)]

    (set! (.-onopen ws)
      (fn [evt]
        (try
          (js/console.log "WS Open successfully")
          (reset! reconnect-delay 1000)
          (swap! state/state assoc :ws-connected? true)
          ;; If you send an initial token or subscribe message, wrap it here:
          ;; (send-ws-message! {:type :init})
          (catch :default err
            (js/console.error "Error in WS onopen handler:" err)))))
    
    (set! (.-onclose ws)
          (fn [evt]
            (js/console.warn "WS Closed -> Code:" (.-code evt) "Reason:" (.-reason evt) "WasClean:" (.-wasClean evt))
            (swap! state/state assoc :ws-connected? false)
            (when (= (:auth-status @state/state) :authenticated)
              (schedule-reconnect!))))

    (set! (.-onerror ws)
          (fn [err]
            (js/console.error "WebSocket error:" err)
            (.close ws)))

    (set! (.-onmessage ws)
          (fn [evt]
            (try
              (let [data (decode-transit (.-data evt))]
                (handle-incoming-message data))
              (catch :default err
                (js/console.error "Error parsing WS message frame:" err (.-data evt))))))

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
  (-> (js/fetch "/api/auth-status"
                #js {:headers #js {"Accept" "application/transit+json"}})
      parse-transit
      (.then (fn [data]
               ;; data is ALREADY a Clojure map: {:authenticated? true}
               (if (:authenticated? data)
                 (do
                   (swap! state/state assoc :auth-status :authenticated)
                   (fetch-config!))
                 (swap! state/state assoc :auth-status :unauthenticated))))
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
                 ;; data is ALREADY a Clojure map: {:success true}
                 (if (= "ok" (:status data))
                   (do
                     (swap! state/state assoc
                            :auth-status :authenticated
                            :auth-error nil
                            :passphrase "")
                     (fetch-config!))
                   (swap! state/state assoc :auth-error "Invalid Passphrase"))))
        (.catch (fn [err]
                  (js/console.error "Login failed:" err)
                  (swap! state/state assoc :auth-error "Failed Login"))))))

(defn logout! []
  (stop-reconnect-timer!)
  (when-let [ws @ws-conn]
    (.close ws)
    (reset! ws-conn nil))
  (swap! state/state assoc
         :auth-status :unauthenticated
         :passphrase ""
         :auth-error nil)
  (js/fetch "/api/logout"
            #js {:method "POST"
                 :credentials "same-origin"
                 :headers #js {"Accept" "application/transit+json"}}))

(comment
  (logout!))
