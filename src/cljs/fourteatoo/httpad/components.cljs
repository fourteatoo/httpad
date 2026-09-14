(ns fourteatoo.httpad.components
  (:require [reagent.core :as r]
            [fourteatoo.httpad.ws :as ws]
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

;; --- Linear Progress Bar + Status ---
(defmethod render-element :bar
  [section-id {:keys [title metric-key levels unit] :or {unit "%"} :as item}]
  (let [metric-val     (get-in @state/state [:telemetry metric-key])
        current-val    (or metric-val 0)
        current-status (resolve-status current-val levels)
        formatted-val  (if (number? metric-val)
                         (str metric-val unit)
                         "--")
        max-threshold  (if (seq levels)
                         (apply max (keys levels))
                         100)
        pct-fill       (if (number? metric-val)
                         (min 100 (max 0 (* (/ current-val max-threshold) 100)))
                         0)]
    [action-wrapper item section-id
     [:div {:class "flex flex-col justify-between min-h-[90px] w-full"}
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase"} title]
      [:div {:class "text-3xl font-mono font-extrabold text-slate-100 my-1"}
       formatted-val]
      [:div {:class "w-full bg-slate-800 h-1.5 rounded-full overflow-hidden"}
       [:div {:class (str "h-full transition-all duration-500 "
                          (case current-status
                            :critical "bg-rose-500"
                            :warning  "bg-amber-400"
                            "bg-emerald-400"))
              :style {:width (str pct-fill "%")}}]]]]))


(defn polar->cartesian [cx cy radius angle-deg]
  (let [rad (* (- angle-deg 90) (/ (.-PI js/Math) 180))]
    [(+ cx (* radius (.cos js/Math rad)))
     (+ cy (* radius (.sin js/Math rad)))]))

(defn describe-arc [x y radius start-angle end-angle]
  (let [[sx sy] (polar->cartesian x y radius end-angle)
        [ex ey] (polar->cartesian x y radius start-angle)
        large-arc (if (<= (- end-angle start-angle) 180) "0" "1")]
    (str "M " sx " " sy " A " radius " " radius " 0 " large-arc " 0 " ex " " ey)))


;; --- Radial Gauge + Status ---

;; --- Radial Gauge (Cleaned Layout & Corrected Brackets) ---
(defmethod render-element :gauge
  [section-id {:keys [title metric-key levels unit] :or {unit "%"} :as item}]
  (let [metric-val     (get-in @state/state [:telemetry metric-key])
        current-val    (or metric-val 0)
        current-status (resolve-status current-val levels)
        
        pct            (min 100 (max 0 current-val))
        ;; Arc sweeps from -90 deg (left) to +90 deg (right)
        angle          (- (* (/ pct 100) 180) 90)
        
        stroke-color   (case current-status
                         :critical "#f43f5e"
                         :warning  "#fbbf24"
                         "#34d399")
        formatted-val  (if (number? metric-val) (str metric-val unit) "--")]

    [action-wrapper item section-id
     [:div {:class "flex flex-col items-center justify-between min-h-[140px] w-full pt-1"}
      
      ;; Category / Title
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase self-start mb-1"} 
       title]
      
      ;; Arc + Center Readout Container
      [:div {:class "relative flex flex-col items-center justify-center my-auto w-full"}
       
       ;; SVG ViewBox cropped tightly (100x44) to remove blank bottom space
       [:svg {:viewBox "0 0 100 44" :class "w-40 h-20 overflow-visible"}
        ;; Background Track
        [:path {:d (describe-arc 50 40 34 -90 90)
                :fill "none"
                :stroke "#1e293b"
                :stroke-width "7"
                :stroke-linecap "round"}]
        ;; Active Value Arc
        (when (> pct 0)
          [:path {:d (describe-arc 50 40 34 -90 angle)
                  :fill "none"
                  :stroke stroke-color
                  :stroke-width "7"
                  :stroke-linecap "round"
                  :class "transition-all duration-500"}])]

       ;; Numeric Value centered inside the arc curve (inside the relative div)
       [:div {:class "absolute bottom-0 flex items-center justify-center text-center"}
        [:span {:class "text-2xl font-mono font-extrabold text-slate-100 tracking-tight leading-none"}
         formatted-val]]]]]))


#_
(defmethod render-element :gauge
  [section-id {:keys [title metric-key levels unit] :or {unit "%"} :as item}]
  (let [metric-val     (get-in @state/state [:telemetry metric-key])
        current-val    (or metric-val 0)
        current-status (resolve-status current-val levels)
        
        pct            (min 100 (max 0 current-val))
        angle          (- (* (/ pct 100) 180) 90)
        
        stroke-color   (case current-status
                         :critical "#f43f5e"
                         :warning  "#fbbf24"
                         "#34d399")]

    [action-wrapper item section-id
     [:div {:class "flex flex-col items-center justify-between min-h-[140px] w-full"}
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase self-start"} title]
      
      [:div {:class "relative flex items-center justify-center my-1"}
       [:svg {:viewBox "0 0 100 55" :class "w-36 h-20"}
        [:path {:d (describe-arc 50 50 40 -90 90)
                :fill "none"
                :stroke "#1e293b"
                :stroke-width "8"
                :stroke-linecap "round"}]
        (when (> pct 0)
          [:path {:d (describe-arc 50 50 40 -90 angle)
                  :fill "none"
                  :stroke stroke-color
                  :stroke-width "8"
                  :stroke-linecap "round"
                  :class "transition-all duration-500"}])]]
       
      [:div {:class "absolute bottom-0 text-center"}
       [:span {:class "text-2xl font-mono font-extrabold text-slate-100"}
        (if (number? metric-val) (str metric-val unit) "--")]]
      
      [:span {:class "text-[10px] font-mono text-slate-500"}
       (str "STATUS: " (name current-status))]]]))

;; --- Multi-action Stepper Control ---
(defmethod render-element :stepper
  [section-id {:keys [title desc icon actions] :as item}]
  [action-wrapper item section-id
   [:div {:class "flex flex-col justify-between min-h-[120px] w-full"}
    
    ;; Header area with title, description, and icon
    [:div {:class "flex justify-between items-start w-full mb-3"}
     [:div {:class "pr-1 min-w-0"}
      [:h2 {:class "text-xs sm:text-sm font-bold text-slate-100 truncate"} title]
      (when (seq desc)
        [:p {:class "text-[10px] sm:text-xs text-slate-400 truncate"} desc])]
     (when (seq icon)
       [:span {:class "text-lg sm:text-xl opacity-85 flex-shrink-0 ml-1 pr-4"} icon])]

    ;; Sub-action buttons grid
    [:div {:class "grid grid-cols-2 gap-1.5 w-full mt-auto"}
     (for [act actions
           :let [parent-id (keyword (:id item))
                 act-id    (keyword (or (:id act) (:action act)))
                 cmd-path  (if parent-id
                             [section-id parent-id act-id]
                             [section-id act-id])]]
       ^{:key (str act-id)}
       [:button
        {:on-click (fn [e]
                     (.stopPropagation e) ; Stops triggering the outer card's main :cmd
                     (ws/send-action! cmd-path))
         :class "flex items-center justify-center gap-1.5 py-3 sm:py-3.5 min-h-[48px] bg-slate-800 hover:bg-slate-750 active:bg-blue-600 active:scale-95 rounded-xl border border-slate-700/80 text-slate-100 transition-all select-none touch-manipulation w-full"}
        (when (:icon act) [:span {:class "text-lg sm:text-xl"} (:icon act)])
        (when (:label act) [:span {:class "font-mono font-bold text-xs"} (:label act)])])]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn section-view [{:keys [id title columns buttons]}]
  (let [sec-id      (keyword id)
        global-cols (get-in @state/state [:layout :grid-cols] 6)
        cols        (or columns global-cols)]
    [:section {:class "col-span-full"}
     (when (seq title)
       [:div {:class "sticky top-0 z-10 -mx-4 px-4 py-2.5 bg-slate-950/85 backdrop-blur-md border-b border-slate-800/80 flex items-center gap-3 select-none mb-3"}
        [:span {:class "text-xs font-bold tracking-wider uppercase text-slate-400"} title]
        [:div {:class "h-[1px] flex-grow bg-slate-800/80"}]])

     ;; Grid container with dynamic column count & gap
     [:div {:class "grid"
            :style {:grid-template-columns (str "repeat(" cols ", minmax(0, 1fr))")
                    :gap "1rem"}}
      (for [b buttons
            :let [b-key (or (:id b) (:title b))]]
        ^{:key (str b-key)}
        [render-element sec-id b])]]))

#_
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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




(defn scroll-container-to-index!
  "Programmatically scrolls the main mobile snap container to the tab index."
  [idx]
  (when (and idx (>= idx 0))
    (when-let [main-el (.querySelector js/document "main")]
      (let [container-width (.-clientWidth main-el)
            target-left     (* idx container-width)]
        (.scrollTo main-el #js {:left target-left :behavior "smooth"})))))



(defn- header-component []
  [:header {:class "flex items-center justify-between mb-4 flex-shrink-0"}
   [:div
    [:h1 {:class "text-base font-bold tracking-widest text-slate-200 uppercase"} "HTTPAD"]
    [:p {:class "text-[11px] text-slate-500 font-mono"} "a web-based macropad"]]
   [grid-scaler][status-pill]])

(defn dashboard []
  (let [main-ref (atom nil)
        prev-tab (atom nil)]
    (r/create-class
     {:displayName "Dashboard"

      :component-did-mount
      (fn [_]
        (reset! prev-tab (:active-tab @state/state))
        (when-let [el @main-ref]
          (.addEventListener el "scrollend"
            (fn [_]
              (let [scroll-left (.-scrollLeft el)
                    width       (.-clientWidth el)]
                (when (> width 0)
                  (let [idx      (js/Math.round (/ scroll-left width))
                        sections (:sections @state/state)
                        sec      (nth sections idx nil)]
                    (when-let [sec-id (some-> sec :id keyword)]
                      (when-not (= sec-id (:active-tab @state/state))
                        (reset! prev-tab sec-id)
                        (swap! state/state assoc :active-tab sec-id))))))))))

      :component-did-update
      (fn [_]
        (let [sections    (:sections @state/state)
              current-tab (:active-tab @state/state)]
          (when (and current-tab (not= current-tab @prev-tab))
            (reset! prev-tab current-tab)
            (let [sec-ids (mapv #(keyword (:id %)) sections)
                  idx     (.indexOf sec-ids current-tab)]
              (when (and (>= idx 0) @main-ref)
                (let [target-left (* idx (.-clientWidth @main-ref))]
                  (.scrollTo @main-ref #js {:left target-left :behavior "smooth"})))))))

      :reagent-render
      (fn []
        (let [sections  (:sections @state/state)
              active-id (or (:active-tab @state/state)
                            (some-> (first sections) :id keyword))]
          [:div {:class "min-h-screen h-screen flex flex-col justify-between bg-slate-950 text-slate-100 p-4"}
           [header-component]
           [tab-header sections active-id #(swap! state/state assoc :active-tab %)]

           [:main {:ref #(reset! main-ref %)
                   :class "flex-1 w-full flex overflow-x-auto snap-x snap-mandatory scroll-smooth no-scrollbar"}
            (for [sec sections
                  :let [sec-id (keyword (:id sec))]]
              ^{:key (str sec-id)}
              [:div {:class "w-full min-w-full flex-shrink-0 snap-center px-1"}
               [section-view sec]])]]))})))
