(ns fourteatoo.httpad.client
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [cognitect.transit :as t]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.components :as components]
            [fourteatoo.httpad.ws :as ws]))

(defn main-ui []
  (case (:auth-status @state/state)
    :checking
    [:div {:class "min-h-screen bg-slate-950 flex items-center justify-center text-slate-500 text-sm font-mono"}
     "Validating session..."]

    :unauthenticated
    [components/login-screen]

    :authenticated
    [components/dashboard]))

(defonce root-ref (atom nil))

(defn mount-root! []
  (when-let [app-el (.getElementById js/document "app")]
    (let [root (or @root-ref (rdom/create-root app-el))]
      (reset! root-ref root)
      (rdom/render root [main-ui]))))

(defn ^:dev/after-load init! []
  (ws/check-auth!)
  (mount-root!))
