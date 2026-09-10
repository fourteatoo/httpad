(ns fourteatoo.httpad.state
  (:require [reagent.core :as r]))

;; --- Application State ---
(defonce state
  (r/atom {:auth-status :checking ; :checking | :authenticated | :unauthenticated
           :passphrase ""
           :auth-error nil
           :sections []
           :ws-connected? false}))

