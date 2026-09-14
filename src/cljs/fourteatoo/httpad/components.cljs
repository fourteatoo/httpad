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

(defn grid-scaler []
  (let [cols (get-in @state/state [:layout :grid-cols] 6)]
    [:div {:class "flex items-center gap-2 bg-slate-900 border border-slate-800 rounded-xl p-1.5"}
     [:button {:on-click #(swap! state/state update-in [:layout :grid-cols] (fn [n] (max 2 (dec (or n 6)))))
               :class "p-1 px-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold"} "-"]
     [:span {:class "text-xs font-mono text-slate-400"} (str cols " cols")]
     [:button {:on-click #(swap! state/state update-in [:layout :grid-cols] (fn [n] (min 12 (inc (or n 6)))))
               :class "p-1 px-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold"} "+"]]))
;; EDN Example:
;; {:type :gauge :id :cpu :title "CPU Load" :col-span 2 :row-span 1 :cmd true}
(defn action-wrapper
  "Wraps a widget with optional dynamic col/row spans."
  [{:keys [id cmd class col-span row-span]} section-id content]
  (let [has-cmd? (boolean cmd)
        cmd-path (when has-cmd? [section-id (keyword id)])
        col-span (or col-span 1)
        row-span (or row-span 1)]
    [:div
     {:on-click (when has-cmd? #(ws/send-action! cmd-path))
      :style {:grid-column (str "span " col-span " / span " col-span)
              :grid-row    (str "span " row-span " / span " row-span)}
      :class (str "relative flex flex-col justify-between p-4 sm:p-5 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl select-none transition-all "
                  (if has-cmd?
                    "active:scale-95 active:border-blue-500 touch-manipulation cursor-pointer"
                    "cursor-default")
                  " " class)}
     
     (when has-cmd?
       [:div {:class "absolute top-3 right-3 w-2 h-2 rounded-full bg-blue-500/50 shadow-[0_0_8px_rgba(59,130,246,0.5)]"}])
     
     content]))


#_
(defn action-wrapper
  "Wraps an element with touch/click interaction and a visual indicator 
   if a command is attached."
  [{:keys [id cmd class]} section-id content]
  (let [has-cmd? (boolean cmd)
        cmd-path (when has-cmd? [section-id id])]
    [:div
     {:on-click (when has-cmd? #(ws/send-action! cmd-path))
      :class (str "relative flex flex-col justify-between p-4 sm:p-5 rounded-2xl bg-slate-900 border border-slate-800 shadow-xl select-none transition-all "
                  (if has-cmd?
                    "active:scale-95 active:border-blue-500 touch-manipulation cursor-pointer"
                    "cursor-default")
                  " " class)}
     
     ;; The action indicator dot appears top-right for ANY widget with a :cmd
     (when has-cmd?
       [:div {:class "absolute top-3 right-3 w-2 h-2 rounded-full bg-blue-500/50 shadow-[0_0_8px_rgba(59,130,246,0.5)]"}])
     
     content]))

;; --- Multimethod Element Renderer ---
(defmulti render-element (fn [section-id item] (or (:type item) :button)))

;; --- Standard Button / Action Card ---
(defmethod render-element :button
  [section-id {:keys [title desc icon] :as item}]
  [action-wrapper item section-id
   [:div {:class "flex flex-col justify-between min-h-[90px] w-full"}
    [:div {:class "flex items-center w-full mb-3"}
     [:span {:class "text-3xl sm:text-4xl"} icon]]
    [:div
     [:h2 {:class "text-xs sm:text-sm font-bold text-slate-100"} title]
     (when (seq desc)
       [:p {:class "text-[10px] sm:text-xs text-slate-400 mt-0.5 line-clamp-1"} desc])]]])

;; --- Pure Numerical Readout ---
(defmethod render-element :metric
  [section-id {:keys [title metric-key unit] :or {unit ""} :as item}]
  (let [metric-val    (get-in @state/state [:telemetry metric-key])
        formatted-val (if (number? metric-val)
                        (str metric-val unit)
                        "--")]
    [action-wrapper item section-id
     [:div {:class "flex flex-col justify-between min-h-[90px] w-full"}
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase"} title]
      [:div {:class "text-3xl font-mono font-extrabold text-slate-100 my-auto"}
       formatted-val]]]))

(defn resolve-status
  "Finds the applicable status keyword by checking metric-val 
   against sorted thresholds in the levels map."
  [metric-val levels]
  (if (number? metric-val)
    (let [thresholds (sort-by key > (or levels {0 :ok}))]
      (some (fn [[limit status-key]]
              (when (>= metric-val limit)
                status-key))
            thresholds))
    :ok))

(defmethod render-element :stat
  [_ {:keys [title metric-key levels unit] :or {unit "%"}}]
  (let [metric-val     (get-in @state/state [:telemetry metric-key])
        current-val    (or metric-val 0)
        current-status (resolve-status current-val levels)
        formatted-val  (if (number? current-val)
                         (str current-val unit)
                         "--")]
    [:div {:class "flex flex-col justify-between p-4 min-h-[110px] rounded-2xl bg-slate-900 border border-slate-800 shadow-xl select-none"}
     [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase"} title]
     [:div {:class "text-3xl font-mono font-extrabold text-slate-100 my-1"}
      formatted-val]
     [:div {:class "w-full bg-slate-800 h-1.5 rounded-full overflow-hidden"}
      [:div {:class (str "h-full transition-all duration-500 "
                         (case current-status
                           :critical "bg-rose-500"
                           :warning  "bg-amber-400"
                           "bg-emerald-400"))
             :style {:width (if (number? current-val)
                              (str (min 100 (max 0 current-val)) "%")
                              "0%")}}]]]))

(comment
  (render-element {:id :cpu-stat
                        :type :stat
                        :title "CPU LOAD"
                        :metric-key :cpu-load
                        :value "0%"
                        :status :ok}))

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
  [:div {:class "hidden sm:flex flex-shrink-0 items-center gap-2 mb-4 overflow-x-auto"}
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
