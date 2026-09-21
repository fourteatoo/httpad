(ns fourteatoo.httpad.auth
  (:require [clojure.tools.logging :as log]
            [java-time.api :as jt]))

;; Active long-lived sessions: {session-id {:created-at Instant}}
(defonce active-sessions (atom {}))

;; Active short-lived pair tokens: {token {:created-at Instant}}
(defonce pair-tokens (atom {}))

(defn generate-pair-token
  "Creates a short-lived token (valid for 5 mins) for QR pairing."
  []
  (let [token (str (random-uuid))]
    (swap! pair-tokens assoc token {:created-at (jt/local-time)})
    token))

(defn consume-pair-token
  "Validates and invalidates a pair token. Returns true if valid."
  [token]
  (when (seq token)
    (let [valid?  (when-let [{:keys [created-at]} (get @pair-tokens token)]
                    (jt/before? (jt/local-time)
                                (jt/+ created-at (jt/minutes 5))))]
      (swap! pair-tokens dissoc token)
      valid?)))

(defn create-session
  "Registers a new authenticated session ID."
  []
  (let [session-id (str (random-uuid))]
    (swap! active-sessions assoc session-id {:created-at (jt/local-time)})
    session-id))

(defn valid-session?
  "Checks if a session ID is valid."
  [session-id]
  (boolean (and (seq session-id) (contains? @active-sessions session-id))))
