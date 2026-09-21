(ns fourteatoo.httpad.ui.header
  (:require [reagent.core :as r]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.ui.qr :as qr]
            [fourteatoo.httpad.ui.zoom :as zoom]))


(defn- status-pill [show-qr-atom qr-url-atom loading-atom]
  (let [connected? (:ws-connected? @state/state)]
    [:button {:class "flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-300 hover:text-white transition-all cursor-pointer group"
              :title "Click to show connection QR code"
              :on-click (fn []
                          (let [opening? (not @show-qr-atom)]
                            (reset! show-qr-atom opening?)
                            (when opening?
                              (ws/fetch-pair-token! qr-url-atom loading-atom))))}
     ;; Connection LED status dot
     [:span {:class (str "w-2 h-2 rounded-full "
                         (if connected? "bg-emerald-400 animate-pulse" "bg-rose-500"))}]
     [:span {:class "text-xs font-mono font-medium text-slate-400 group-hover:text-slate-200"}
      (if connected? "ONLINE" "OFFLINE")]
     ;; QR Code Icon
     [:svg {:class "w-3.5 h-3.5 ml-0.5 text-slate-500 group-hover:text-slate-300 transition-colors"
            :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z"}]]]))

(defn header-component []
  (let [show-qr? (r/atom false)
        qr-url   (r/atom nil)
        loading? (r/atom false)]
    (fn []
      [:header {:class "flex items-center justify-between mb-4 flex-shrink-0"}
       [:div
        [:h1 {:class "text-base font-bold tracking-widest text-slate-200 uppercase"} "HTTPAD"]
        [:p {:class "text-[11px] text-slate-500 font-mono"} "a web-based macropad"]]
       [zoom/zoom-scaler]
       [status-pill show-qr? qr-url loading?]
       [qr/qr-code-modal @qr-url loading? show-qr?]])))
