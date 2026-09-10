(ns fourteatoo.httpad.components
  (:require [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.state :as state]))

(defn status-pill []
  (let [connected? (:ws-connected? @state/state)]
    [:div {:class "flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900 border border-slate-800 shadow-inner"}
     [:div {:class (str "w-2.5 h-2.5 rounded-full "
                        (if connected? "bg-emerald-400 animate-pulse" "bg-rose-500"))}]
     [:span {:class "text-[11px] font-semibold text-slate-400 uppercase tracking-wider"}
      (if connected? "Connected" "Reconnecting")]]))

;; --- Multimethod Element Renderer ---
(defmulti render-element (fn [section-id item] (or (:type item) :button)))

(defmethod render-element :button
  [section-id {:keys [id title desc icon]}]
  (let [btn-id (keyword id)
        cmd-path [section-id btn-id]]
    [:button
     {:on-click #(ws/send-action! cmd-path)
      :class "flex flex-col justify-between p-4 sm:p-5 min-h-[110px] rounded-2xl bg-slate-900 border border-slate-800 shadow-xl active:scale-95 active:border-blue-500 transition-all select-none touch-manipulation text-left"}
     [:div {:class "flex justify-between items-center w-full mb-3"}
      [:span {:class "text-3xl sm:text-4xl"} icon]
      [:div {:class "w-2 h-2 rounded-full bg-blue-500/50"}]]
     [:div
      [:h2 {:class "text-xs sm:text-sm font-bold text-slate-100"} title]
      (when (seq desc)
        [:p {:class "text-[10px] sm:text-xs text-slate-400 mt-0.5 line-clamp-1"} desc])]]))

(defmethod render-element :stepper
  [section-id {:keys [id title desc icon actions]}]
  (let [btn-id (when id (keyword id))]
    [:div {:class "flex flex-col justify-between p-2.5 sm:p-3 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl overflow-hidden"}
     ;; Optional Header Action
     [:button
      {:on-click #(when btn-id (ws/send-action! [section-id btn-id]))
       :disabled (nil? btn-id)
       :class (str "flex items-center justify-between p-2 rounded-xl text-left touch-manipulation select-none w-full transition-colors "
                   (if btn-id
                     "hover:bg-slate-800/60 active:bg-slate-800 cursor-pointer group mb-1.5"
                     "cursor-default mb-1.5"))}
      [:div {:class "pr-1 min-w-0"}
       [:h2 {:class "text-xs sm:text-sm font-bold text-slate-100 truncate"} title]
       (when (seq desc)
         [:p {:class "text-[10px] sm:text-xs text-slate-400 truncate"} desc])]
      (when (seq icon)
        [:span {:class "text-lg opacity-85 group-active:scale-110 transition-transform flex-shrink-0 ml-1"} icon])]

     ;; Sub-Actions
     [:div {:class "grid grid-cols-2 gap-1.5 w-full"}
      (for [act actions
            :let [act-id (keyword (or (:id act) (:action act)))
                  cmd-path (if btn-id
                             [section-id btn-id act-id]
                             [section-id act-id])]]
        ^{:key (str act-id)}
        [:button
         {:on-click #(ws/send-action! cmd-path)
          :class "flex items-center justify-center gap-1.5 py-3 sm:py-3.5 min-h-[52px] bg-slate-800 hover:bg-slate-750 active:bg-blue-600 active:scale-95 rounded-xl border border-slate-700/80 text-slate-100 transition-all select-none touch-manipulation w-full"}
         (when (:icon act) [:span {:class "text-lg sm:text-xl"} (:icon act)])
         (when (:label act) [:span {:class "font-mono font-bold text-xs"} (:label act)])])]]))

(defn section-view [{:keys [id title buttons]}]
  (let [sec-id (keyword id)]
    [:section {:class "col-span-full"}
     (when (seq title)
       [:div {:class "sticky top-0 z-10 -mx-4 px-4 py-2.5 bg-slate-950/85 backdrop-blur-md border-b border-slate-800/80 flex items-center gap-3 select-none mb-3"}
        [:span {:class "text-xs font-bold tracking-wider uppercase text-slate-400"} title]
        [:div {:class "h-[1px] flex-grow bg-slate-800/80"}]])

     [:div {:class "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4"}
      (for [b buttons
            :let [b-key (or (:id b) (:title b))]]
        ^{:key (str b-key)}
        [render-element sec-id b])]]))

(defn login-screen []
  [:div {:class "min-h-screen bg-slate-950 flex items-center justify-center p-4"}
   [:form {:on-submit ws/login!
           :class "bg-slate-900 border border-slate-800 p-8 rounded-3xl max-w-sm w-full text-center shadow-2xl"}
    [:div {:class "mb-6"}
     [:div {:class "w-12 h-12 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center mx-auto mb-3 text-xl"} "🔒"]
     [:h1 {:class "text-xl font-bold text-white tracking-wide"} "Macropad Deck"]
     [:p {:class "text-xs text-slate-400 mt-1"} "Session passphrase required"]]

    [:input {:type "password"
             :placeholder "Passphrase"
             :autoFocus true
             :value (:passphrase @state/state)
             :on-change #(swap! state/state assoc :passphrase (.. % -target -value))
             :class (str "w-full px-4 py-3 bg-slate-950 border rounded-xl text-white mb-4 "
                         "focus:outline-none focus:border-blue-500 text-center text-lg tracking-widest "
                         (if (:auth-error @state/state) "border-rose-500" "border-slate-800"))}]

    (when-let [err (:auth-error @state/state)]
      [:p {:class "text-rose-400 text-xs font-medium mb-4"} err])

    [:button {:type "submit"
              :class "w-full py-3.5 bg-blue-600 active:bg-blue-700 text-white font-bold rounded-xl shadow-lg transition-all text-sm tracking-wide"}
     "Unlock Deck"]]])

(defn tab-header [sections active-section-id on-select]
  [:div {:class "hidden sm:flex items-center gap-2 mb-4 overflow-x-auto"}
   (for [sec sections
         :let [sec-id (keyword (:id sec))
               active? (= sec-id active-section-id)]]
     ^{:key (str sec-id)}
     [:button
      {:on-click #(on-select sec-id)
       :class (str "px-4 py-2 rounded-xl text-xs font-bold uppercase transition-all select-none "
                   (if active?
                     "bg-blue-600 text-white shadow-lg"
                     "bg-slate-900 text-slate-400 border border-slate-800 hover:text-white"))}
      (:title sec)])])

(defn global-styles []
  [:style
   ".no-scrollbar::-webkit-scrollbar { display: none; }
    .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }"])

(defn handle-scroll [e sections]
  (let [target (.-target e)
        scroll-left (.-scrollLeft target)
        width (.-clientWidth target)
        idx (js/Math.round (/ scroll-left width))
        sec (nth sections idx nil)]
    (when (and sec (:id sec))
      (let [sec-id (keyword (:id sec))]
        (when-not (= sec-id (:active-tab @state/state))
          (swap! state/state assoc :active-tab sec-id))))))

(defn dashboard []
  (let [sections (:sections @state/state)
        active-id (or (:active-tab @state/state) (keyword (:id (first sections))))]
    [:div {:class "min-h-screen h-screen overflow-hidden bg-slate-950 text-slate-100 p-4 flex flex-col justify-between"}
     [global-styles]
     
     ;; Header & Desktop Tabs
     [:header {:class "flex items-center justify-between mb-4 flex-shrink-0"}
      [:div
       [:h1 {:class "text-base font-bold tracking-widest text-slate-200 uppercase"} "HTTP MACROPAD"]
       [:p {:class "text-[11px] text-slate-500 font-mono"} "Local Session Control"]]
      [status-pill]]

     [tab-header sections active-id #(swap! state/state assoc :active-tab %)]

     [:main {:on-scroll #(handle-scroll % sections)
:class "flex-1 w-full max-w-7xl mx-auto flex overflow-x-auto sm:overflow-visible snap-x snap-mandatory scroll-smooth no-scrollbar"
        :style {:touch-action "pan-x pan-y"
                :-webkit-overflow-scrolling "touch"}}
 (for [sec sections
       :let [sec-id (keyword (:id sec))
             active? (= sec-id active-id)]]
   ^{:key (str sec-id)}
   [:div {:class (str "w-full min-w-full flex-shrink-0 snap-center snap-always px-1 sm:px-0 "
                      ;; On mobile (below sm): ALWAYS display block for horizontal scrolling track
                      ;; On desktop (sm and up): Hide non-active sections
                      (if active? "block" "block sm:hidden"))}
    [section-view sec]])]

     ;; Page Dots
     [:div {:class "flex sm:hidden justify-center gap-1.5 my-3"}
      (for [sec sections
            :let [sec-id (keyword (:id sec))]]
        ^{:key (str sec-id)}
        [:div {:class (str "h-2 rounded-full transition-all duration-300 "
                           (if (= sec-id active-id) "bg-blue-500 w-5" "bg-slate-800 w-2"))}])]]))
