(ns fourteatoo.httpad.server
  (:require [mount.core :refer [defstate]]
            [org.httpkit.server :as http]
            [reitit.ring :as ring]
            [ring.util.response :as resp]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [cheshire.core :as json]
            [fourteatoo.httpad.log :as log]
            [cognitect.transit :as transit]
            [fourteatoo.httpad.config :as c]
            [fourteatoo.httpad.executor :as executor]
            [clojure.core.async :as a :refer [go-loop <! >! timeout chan mult tap untap]]
            [fourteatoo.httpad.telemetry :as telemetry]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [fourteatoo.httpad.focus :as focus]
            [fourteatoo.httpad.network :as network]
            [fourteatoo.httpad.auth :as auth])
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

;; Replace your existing get-cookie definition with this:
(defn- get-session-id [req]
  (get-cookie req "httpad_session"))

(defn- valid-session? [req]
  (let [session-id (get-session-id req)]
    (boolean (and (seq session-id)
                  (contains? @active-sessions session-id)))))

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
          expected-pass   (:auth-token c/config)]
      (if (and (seq expected-pass) (= submitted-pass expected-pass))
        (let [session-id (str (java.util.UUID/randomUUID))]
          (swap! active-sessions conj session-id)
          (log/info "Successful authentication from IP" (:remote-addr req))
          (assoc-in (make-response {:status "ok"})
                    [:cookies "macropad_session"]
                    {:value session-id
                     :path "/"
                     :http-only true
                     :same-site :lax
                     :max-age 31536000}))
        (do
          (log/warn "Failed login attempt from IP:" (:remote-addr req))
          (make-response {:status "error" :message "Invalid Passphrase"} 401))))
    (catch Exception e
      (log/error e "Error processing login request")
      (make-response {:status "error" :message "Bad Request"} 400))))

(defn auth-status-handler
  "Returns current authentication state based on request cookie."
  [req]
  (make-response {:authenticated? (valid-session? req)}))

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
  (make-response (sanitize-config c/config)))

(defn user-interface-url [& {:keys [host port token]}]
  (str "http://" (or host
                     (network/get-ip-address)
                     "localhost")
       ":" (or port
               (c/port)
               8080)
       "/"                              ; "index.html"
       (when token
         (str "?token=" (if (string? token)
                          token
                          (auth/generate-pair-token))))))

(defn- pair-token-handler [req]
  (let [token (get-in req [:params :token])]
    (if (auth/consume-pair-token token)
      (let [session-id (str (random-uuid))]
        (swap! active-sessions conj session-id)
        (log/info "Device authenticated successfully via QR pair-token from IP:" (:remote-addr req))
        (-> (resp/redirect "/")
            (assoc-in [:cookies "httpad_session"]
                      {:value     session-id
                       :path      "/"
                       :http-only true
                       :same-site :lax
                       :max-age   31536000})))
      {:status 401 :body "Invalid or expired pair token"})))

(defn ws-handler
  "Validates session cookie before upgrading HTTP to WebSocket channel,
   deserializing incoming Transit messages."
  [req]
  (http/as-channel req
                   {:on-open
                    (fn [ch]
                      (log/info "Authenticated WebSocket connected from IP:" (:remote-addr req))
                      (let [client-async-chan (a/chan (a/sliding-buffer 10))]
                        ;; Register handles tapping the mult, updating active state, & pushing initial snapshot
                        (telemetry/register-client ch client-async-chan)
                        (a/go-loop []
                          (if-let [msg (a/<! client-async-chan)]
                            (do
                              (http/send! ch (encode-transit msg))
                              (recur))
                            (log/debug "Client async loop terminated")))
                        (a/put! client-async-chan
                                {:type :server
                                 :url (user-interface-url :token true)})))

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
                      (log/debug "WebSocket channel closed with status:" status)
                      ;; Unregister handles untapping, channel closing, and atom removal
                      (telemetry/unregister-client ch))}))

(defn- logout-handler [req]
  {:status 200
   :headers {"Content-Type" "application/transit+json"
             ;; Force browser to immediately drop the session cookie
             "Set-Cookie" "httpad_session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax"}
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
          win-name (some-> (:window payload) name s/trim)
          target-id (focus/window-class->section-id win-name)]
      (if target-id
        (do
          (focus/update-section target-id)
          {:status 200
           :headers {"Content-Type" "application/edn"}
           :body (pr-str {:status :ok})})
        (do
          (log/debug "No matching section for focused window" win-name)
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

(defn wrap-auth [handler]
  (fn [req]
    (if (valid-session? req)
      (handler req)
      (make-response {:status "error"
                      :message "Unauthorized"} 401))))

(defn generate-pair-token-handler
  "Protected endpoint: Generates a new ephemeral pairing token for
  display in a QR code."
  [_req]
  (let [token (auth/generate-pair-token)]
    (make-response {:token token
                    :url (user-interface-url :token token)})))

(def app-routes
  [;; 1. Root / Public Static Landing
   ["/" {:get (fn [req]
                (if (get-in req [:params :token])
                  (pair-token-handler req)
                  (resp/resource-response "public/index.html")))}]

   ;; 2. Public API endpoints
   ["/api"
    ["/login" {:post login-handler}]
    ["/focus" {:post focus-handler}]
    ["/pair" {:get pair-token-handler}]

    ;; 3. Internal Protected endpoints (Auth middleware applied ONLY to this branch)
    ["/int" {:middleware [wrap-auth]}
     ["/config"      {:get config-handler}]
     ["/auth-status" {:get auth-status-handler}]
     ["/ws"          {:get ws-handler}]
     ["/logout"      {:post logout-handler}]
     ["/pair-token"  {:post generate-pair-token-handler}]]]])

(def handler
  (ring/ring-handler
    (ring/router app-routes)
    
    ;; Default fallbacks (404, resource serving, etc.)
    (ring/create-default-handler
      {:not-found (constantly {:status 404 :body "Not Found"})})
    
    ;; Global Ring Middleware
    {:middleware [wrap-params
                  wrap-keyword-params
                  wrap-cookies
                  [wrap-resource "public"]
                  wrap-content-type]}))

(defn start-server []
  (let [port (c/port)]
    (log/info "Starting API server on port" port)
    (http/run-server handler {:port port :ip "0.0.0.0"})))

(defn stop-server [server]
  (when server
    (log/info "Shutting down API")
    (server :timeout 100)))

(defstate http-server
  :start (start-server)
  :stop  (stop-server http-server))
