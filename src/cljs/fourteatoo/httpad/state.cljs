(ns fourteatoo.httpad.state
  (:require [reagent.core :as r]
            [cljs.reader :as reader]))


(def storage-key "httpad-layout-settings")

(defn load-layout
  "Reads layout settings from localStorage, falling back to defaults."
  [default-layout]
  (if-let [raw (.getItem js/localStorage storage-key)]
    (try
      (reader/read-string raw)
      (catch :default _
        default-layout))
    default-layout))

(defonce state
  (r/atom {:auth-status :checking ; :checking | :authenticated | :unauthenticated
           :passphrase ""
           :auth-error nil
           :sections []
           :ws-connected? false
           :telemetry {}
           :layout (load-layout {:grid-cols 6
                                 :gap 16})}))

;; Watch state atom and persist layout changes automatically
(defonce _layout-persister
  (add-watch state :persist-layout
             (fn [_ _ old-state new-state]
               (let [old-layout (:layout old-state)
                     new-layout (:layout new-state)]
                 (when (not= old-layout new-layout)
                   (.setItem js/localStorage storage-key (pr-str new-layout)))))))
