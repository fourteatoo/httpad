(ns fourteatoo.httpad.ui.qr
  (:require [reagent.core :as r]
            [qrcode :as qr]
            [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.util :as u]))


(defn- qr-code-view [url size]
  (let [dom-node (atom nil)
        render-qr! (fn [el u s]
                     (when (and el (seq u))
                       (try
                         ;; qrcode npm package provides .toCanvas or .toString
                         (.toCanvas qr el u #js {:width (or s 180) :margin 1} 
                                    (fn [err] (when err (js/console.error err))))
                         (catch js/Error e
                           (js/console.error "QR generation failed:" e)))))]

    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [[_ u s] (r/argv this)]
          (render-qr! @dom-node u s)))

      :component-did-update
      (fn [this]
        (let [[_ u s] (r/argv this)]
          (render-qr! @dom-node u s)))

      :reagent-render
      (fn [_ _]
        [:canvas {:ref #(when % (reset! dom-node %))
                  :class "p-2 bg-white rounded-xl shadow-md"}])})))

(defn qr-code-modal [url loading-atom open-atom]
  (when @open-atom
    [:div {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
           :on-click #(reset! open-atom false)}
     [:div {:class "bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-2xl flex flex-col items-center gap-4 max-w-xs w-full m-4 text-center"
            :on-click #(.stopPropagation %)}
      [:h3 {:class "text-sm font-bold tracking-wider text-slate-400 uppercase"} 
       "Scan to Connect"]
      (if @loading-atom
        [:div {:class "py-8 text-xs font-mono text-slate-400 animate-pulse"} "Generating QR Code..."]
        [qr-code-view url 180])

      (when (and (not @loading-atom) (seq url))
        [:p {:class "text-xs font-mono text-slate-400 break-all"} url])
      
      [:button {:class "mt-2 w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-lg transition-colors"
                :on-click #(reset! open-atom false)}
       "Close"]]]))
