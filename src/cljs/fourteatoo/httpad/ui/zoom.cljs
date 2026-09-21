(ns fourteatoo.httpad.ui.zoom
  (:require [fourteatoo.httpad.state :as state]))

(def ^:private zoom-presets-list
  [{:id :small
    :label "S"
    :min-width 100
    :height "h-16"
    :text-size "text-xs"}
   {:id :medium
    :label "M"
    :min-width 150
    :height "h-24"
    :text-size "text-sm"}
   {:id :large
    :label "L"
    :min-width 220
    :height "h-32"
    :text-size "text-base"}
   {:id :extra-large
    :label "XL"
    :min-width 310
    :height "h-40"
    :text-size "text-lg"}])

(def zoom-presets
  (let [padded (concat [nil] zoom-presets-list [nil])]
    (into {}
          (map (fn [[prev curr next]]
                 [(:id curr) (assoc curr :prev prev :next next)]))
          (partition 3 1 padded))))

(defn- get-zoom-preset [id]
  (or (get zoom-presets id)
      (get zoom-presets (get-in zoom-presets-list [1 :id]))))

(defn- step-zoom [current-id dir]
  (let [current (get-zoom-preset current-id)]
    (:id (or (case dir
               :dec (:prev current)
               :inc (:next current))
             current))))

(defn zoom-scaler
  "Stepper control adjusting minimum button tile size safely."
  []
  (let [current (get-in @state/state [:layout :zoom-level] :medium)]
    [:div {:class "flex items-center gap-2 bg-slate-900 border border-slate-800 rounded-xl p-1"}
     [:button {:type "button"
               :on-click #(swap! state/state update-in [:layout :zoom-level] step-zoom :dec)
               :class "p-1 px-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold"} "-"]
     [:span {:class "text-xs font-mono text-slate-400 capitalize w-6 text-center select-none"} 
      (:label (get-zoom-preset current))]
     [:button {:type "button"
               :on-click #(swap! state/state update-in [:layout :zoom-level] step-zoom :inc)
               :class "p-1 px-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold"} "+"]]))
