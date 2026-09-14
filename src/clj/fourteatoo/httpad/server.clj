(ns fourteatoo.httpad.server
  (:require [mount.core :refer [defstate]]
            [org.httpkit.server :as http]
            [ring.util.response :as resp]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [fourteatoo.httpad.config :refer [config]]
            [fourteatoo.httpad.executor :as executor]
            [clojure.core.async :as a :refer [go-loop <! >! timeout chan mult tap untap]]
            [fourteatoo.httpad.telemetry :as telemetry]
            [clojure.edn :as edn]
            [clojure.string :as s])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


;; Set of active session UUIDs in memory
(defonce active-sessions (atom #{}))

(defn- decode-transit
  "Decodes a Transit JSON string into Clojure data structures."
  [msg-str]
  (let [in (ByteArrayInputStream. (.getBytes msg-str "UTF-8"))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn- encode-transit
  "Encodes Clojure data structures into a Transit JSON string."
  [data]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer data)
    (.toString out "UTF-8")))

(defn- get-cookie
  "Extracts a specific cookie value from Ring request map."
  [req cookie-name]
  (get-in req [:cookies cookie-name :value]))

(defn- make-response
  ([body] (make-response body 200))
  ([body status]
   {:status status
    :headers {"Content-Type" "application/transit+json"}
    :body (encode-transit body)}))

(defn- login-handler
  "Validates passphrase against config and issues an HttpOnly session cookie."
  [req]
  (try
    (let [body (decode-transit (slurp (:body req)))
          submitted-pass (:passphrase body)
          expected-pass   (:auth-token config)]
      (if (and (seq expected-pass) (= submitted-pass expected-pass))
        (let [session-id (str (java.util.UUID/randomUUID))]
          (swap! active-sessions conj session-id)
          (log/infof "Successful authentication from IP: %s" (:remote-addr req))
          (assoc-in (make-response {:status "ok"})
                    [:cookies "macropad_session"]
                    {:value session-id
                     :path "/"
                     :http-only true
                     :same-site :lax
                     :max-age 31536000}))
        (do
          (log/warnf "Failed login attempt from IP: %s" (:remote-addr req))
          (make-response {:status "error" :message "Invalid Passphrase"} 401))))
    (catch Exception e
      (log/error e "Error processing login request")
      (make-response {:status "error" :message "Bad Request"} 400))))

(defn auth-status-handler
  "Returns current authentication state based on request cookie."
  [req]
  (let [session-id (get-cookie req "macropad_session")
        valid? (and (seq session-id) (contains? @active-sessions session-id))]
    (make-response {:authenticated? valid?})))

(defn- sanitize-button
  [b]
  (let [clean-b (update b :cmd some?)]
    (if (seq (:actions b))
      (update clean-b :actions
              (fn [acts]
                (map #(dissoc % :cmd) acts)))
      clean-b)))

(defn- sanitize-config
  "Prepares configuration data for the frontend client by stripping commands."
  [config]
  {:sections
   (mapv (fn [sec]
           (assoc sec :buttons (mapv sanitize-button (:buttons sec))))
         (:sections config))})

(defn config-handler
  "Returns frontend layout configuration (sanitized of backend commands)."
  [req]
  (let [session-id (get-cookie req "macropad_session")]
    (if (and (seq session-id) (contains? @active-sessions session-id))
      (make-response (sanitize-config config))
      (make-response {:status "error" :message "Unauthorized"} 401))))

(defn ws-handler
  "Validates session cookie before upgrading HTTP to WebSocket channel,
   deserializing incoming Transit messages."
  [req]
  (let [session-id (get-cookie req "macropad_session")]
    (if (and (seq session-id) (contains? @active-sessions session-id))
      (http/as-channel req
        {:on-open
         (fn [ch]
           (log/infof "Authenticated WebSocket connected from IP: %s" (:remote-addr req))
           (let [client-async-chan (a/chan (a/sliding-buffer 10))]
             
             ;; Register handles tapping the mult, updating active state, & pushing initial snapshot
             (telemetry/register-client ch client-async-chan)
             
             (a/go-loop []
               (if-let [msg (a/<! client-async-chan)]
                 (do
                   (http/send! ch (encode-transit msg))
                   (recur))
                 (log/debug "Client async loop terminated")))))

         :on-receive
         (fn [_ch raw-msg]
           (try
             (let [payload (decode-transit raw-msg)
                   action  (:action payload)]
               (if action
                 (executor/execute action)
                 (log/warn "Received Transit WebSocket frame missing ':action' key")))
             (catch Exception e
               (log/error e "Failed to decode incoming Transit WebSocket frame"))))

         :on-close
         (fn [ch status]
           (log/debugf "WebSocket channel closed (status: %s)" status)
           ;; Unregister handles untapping, channel closing, and atom removal
           (telemetry/unregister-client ch))})
      (do
        (log/warnf "Rejected unauthenticated WebSocket attempt from IP: %s" (:remote-addr req))
        {:status 403 :body "Forbidden"}))))

(defn- logout-handler [req]
  {:status 200
   :headers {"Content-Type" "application/transit+json"
             ;; Force browser to immediately drop the session cookie
             "Set-Cookie" "macropad_session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax"}
   :session nil ;; Clear Ring session memory
   :body (encode-transit {:success true})})

(defn focus-handler
  "Processes incoming window focus EDN payloads from external watcher
   script and broadcasts a tab-switch message to UIs via the telemetry
   channel."
  [req]
  (try
    (let [body-str    (slurp (:body req))
          payload     (edn/read-string body-str)
          win-name    (some-> (:window payload) name s/trim)
          sections    (:sections config)
          class->id   (into {}
                            (for [sec sections
                                  :let [sec-id (:id sec)
                                        win-key (name (or (:window-class sec) (:id sec)))]]
                              [win-key sec-id]))
          
          target-id   (get class->id win-name)]
      (if target-id
        (do
          (log/infof "Focus match found: window '%s' -> section tab '%s'" win-name target-id)
          ;; Push onto telemetry pipeline - mult automatically fans out to all clients
          (telemetry/broadcast {:type :active-section 
                                :section target-id})
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (pr-str {:status :ok})})
        (do
          (log/debugf "No matching section for focused window '%s'" win-name)
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (pr-str {:status :ignored})})))
    (catch Exception e
      (log/error e "Failed to process focus EDN request")
      {:status 500
       :headers {"Content-Type" "application/edn"}
       :body (pr-str {:status :error
                      :message "Internal error"
                      :error (str e)})})))

#_
(defn focus-handler [req]
  (let [payload (try 
                  (edn/read-string (slurp (:body req))) 
                  (catch Exception _ nil))
        win-name    (some-> (:window payload) name s/trim)
        sections    (:sections config)
        valid-ids   (set (map #(name (:id %)) sections))
        matching-id (when (contains? valid-ids win-name)
                      win-name)]
    (when matching-id
      (ws/broadcast! [:dashboard/set-tab (keyword matching-id)]))
    
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body "{:status :ok}"}))

(defn- app-routes [req]
  (case (:uri req)
    "/" (resp/resource-response "public/index.html")
    "/api/login"       (if (= (:request-method req) :post)
                         (login-handler req)
                         {:status 451 :body "Method Not Allowed"})
    "/api/logout" (logout-handler req)
    "/api/auth-status" (auth-status-handler req)
    "/api/config"      (config-handler req)
    "/api/focus" (focus-handler req)
    "/ws"              (ws-handler req)
    {:status 404 :body "Not Found"}))

(def handler
  (-> app-routes
      (wrap-keyword-params)
      (wrap-params)
      (wrap-cookies)
      (wrap-resource "public")
      (wrap-content-type)))

(defstate http-server
  :start (let [port (:port config 8080)]
           (log/infof "Starting Httpad server on port %d..." port)
           (http/run-server handler {:port port :ip "0.0.0.0"}))
  :stop  (when http-server
           (http-server :timeout 100)))
