(ns fourteatoo.httpad.client
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [cognitect.transit :as t]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.ui.dashboard :as dash]
            [fourteatoo.httpad.ws :as ws]))

(defn main-ui []
  (case (:auth-status @state/state)
    :checking
    [:div {:class "min-h-screen bg-slate-950 flex items-center justify-center text-slate-500 text-sm font-mono"}
     "Validating session..."]

    :unauthenticated
    [dash/login-screen]

    :authenticated
    [dash/dashboard]))

(defonce root-ref (atom nil))

(defn mount-root! []
  (when-let [app-el (.getElementById js/document "app")]
    (let [root (or @root-ref (rdom/create-root app-el))]
      (reset! root-ref root)
      (rdom/render root [main-ui]))))

(defonce wake-lock-ref (atom nil))

(defn request-wake-lock! []
  (when (exists? js/navigator.wakeLock)
    (-> (.. js/navigator -wakeLock (request "screen"))
        (.then (fn [lock]
                 (reset! wake-lock-ref lock)
                 (js/console.log "Screen Wake Lock active")
                 ;; Re-request if page visibility changes (e.g., user switched tabs and came back)
                 (.addEventListener lock "release"
                                    (fn [] (reset! wake-lock-ref nil)))))
        (.catch (fn [err]
                  (js/console.warn "Wake Lock request failed:" (.-message err)))))))

(defn release-wake-lock! []
  (when-let [lock @wake-lock-ref]
    (.release lock)
    (reset! wake-lock-ref nil)))

(defonce ^:private init-visibility-listener
  (when (exists? js/document.addEventListener)
    (.addEventListener js/document "visibilitychange"
                       (fn []
                         (when (and (= (.-visibilityState js/document) "visible")
                                    (nil? @wake-lock-ref))
                           (request-wake-lock!))))
    true))

(defn ^:dev/after-load init! []
  (ws/check-auth!)
  (mount-root!))
