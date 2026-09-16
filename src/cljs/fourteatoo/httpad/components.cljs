(ns fourteatoo.httpad.components
  (:require [reagent.core :as r]
            [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.util :as u]))

(defn status-pill []
  (let [connected? (:ws-connected? @state/state)]
    [:div {:class "flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900 border border-slate-800 shadow-inner"}
     [:div {:class (str "w-2.5 h-2.5 rounded-full "
                        (if connected? "bg-emerald-400 animate-pulse" "bg-rose-500"))}]
     [:span {:class "text-[11px] font-semibold text-slate-400 uppercase tracking-wider"}
      (if connected? "Connected" "Reconnecting")]]))

(defn action-wrapper
  "Wraps a widget with dynamic col/row spans, zoom sizing, and optional state/action indicator dots."
  [{:keys [id cmd state class col-span row-span]} section-id content]
  (let [zoom-id  (get-in @state/state [:layout :zoom-level] :medium)
        has-cmd? (boolean cmd)
        cmd-path (when has-cmd? [section-id (keyword id)])
        col-span (or col-span 1)
        row-span (or row-span 1)
        ;; State evaluation logic
        state-key     (:key state)
        raw-val       (when state-key (get-in @state/state [:telemetry state-key]))
        mapping       (:mapping state)
        ;; Resolve mapped status keyword (e.g., :on or :off) or fallback to raw value
        mapped-status (get mapping raw-val raw-val)
        ;; Determine indicator dot styling based on presence of cmd & state
        dot-classes (cond
                      ;; 1. Active state = :on -> Amber glow
                      (= mapped-status :on)
                      "bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)]"

                      ;; 2. Active state = :off -> Slate/Gray dot
                      (= mapped-status :off)
                      "bg-slate-600/60 shadow-none"

                      ;; 3. No state config, but clickable -> Default Blue dot
                      has-cmd?
                      "bg-blue-500/50 shadow-[0_0_8px_rgba(59,130,246,0.5)]"

                      ;; 4. Non-interactive & no state -> No dot
                      :else nil)

        padding  (case zoom-id
                   :small       "p-2.5"
                   :medium      "p-3.5 sm:p-4"
                   :large       "p-4 sm:p-5"
                   :extra-large "p-5 sm:p-6"
                   "p-4")
        min-h    (case zoom-id
                   :small       "min-h-[70px]"
                   :medium      "min-h-[90px]"
                   :large       "min-h-[120px]"
                   :extra-large "min-h-[150px]"
                   "min-h-[90px]")]
    [:div
     {:on-click (when has-cmd? #(ws/send-action! cmd-path))
      :style {:grid-column (str "span " col-span " / span " col-span)
              :grid-row    (str "span " row-span " / span " row-span)}
      :class (str "relative flex flex-col justify-between rounded-2xl bg-slate-900 border border-slate-800 shadow-xl select-none transition-all "
                  padding " " min-h " "
                  (if has-cmd?
                    "active:scale-95 active:border-blue-500 touch-manipulation cursor-pointer"
                    "cursor-default")
                  " " class)}
     
     ;; Render status dot when either an action or state tracking is configured
     (when dot-classes
       [:div {:class (str "absolute top-2.5 right-2.5 w-2 h-2 rounded-full transition-all duration-300 "
                          dot-classes)}])
     
     content]))

(defmulti render-element (fn [section-id item] (or (:type item) :button)))

(defmethod render-element :button
  [section-id {:keys [title desc icon] :as item}]
  (let [zoom-id    (get-in @state/state [:layout :zoom-level] :medium)
        title-size (case zoom-id
                     :small       "text-[11px]"
                     :medium      "text-xs sm:text-sm"
                     :large       "text-sm sm:text-base"
                     :extra-large "text-base sm:text-lg"
                     "text-xs sm:text-sm")
        icon-size  (case zoom-id
                     :small       "text-xl"
                     :medium      "text-2xl sm:text-3xl"
                     :large       "text-3xl sm:text-4xl"
                     :extra-large "text-4xl sm:text-5xl"
                     "text-3xl")]
    [action-wrapper item section-id
     [:div {:class "flex flex-col justify-between h-full w-full"}
      [:div {:class "flex items-center w-full mb-1.5"}
       [:span {:class icon-size} icon]]
      [:div
       [:h2 {:class (str title-size " font-bold text-slate-100 leading-tight")} title]
       (when (and (seq desc) (not= zoom-id :small))
         [:p {:class "text-[10px] sm:text-xs text-slate-400 mt-0.5 line-clamp-1"} desc])]]]))

(defmethod render-element :metric
  [section-id {:keys [title metric-key unit] :or {unit ""} :as item}]
  (let [zoom-id       (get-in @state/state [:layout :zoom-level] :medium)
        metric-val   (get-in @state/state [:telemetry metric-key])
        formatted-val (if (number? metric-val) (str metric-val unit) "--")
        val-size     (case zoom-id
                       :small       "text-lg"
                       :medium      "text-2xl"
                       :large       "text-3xl"
                       :extra-large "text-4xl sm:text-5xl"
                       "text-2xl")]
    [action-wrapper item section-id
     [:div {:class "flex flex-col justify-between h-full w-full"}
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase"} title]
      [:div {:class (str val-size " font-mono font-extrabold text-slate-100 my-auto")}
       formatted-val]]]))

(defn resolve-status
  "Finds the applicable status keyword by checking metric-val 
   against descending thresholds in the levels map (e.g., {:ok 0 :warning 75 :critical 90})."
  [metric-val levels]
  (if (and (number? metric-val) (seq levels))
    (let [sorted-levels (sort-by val > levels)]
      (or (some (fn [[status-key limit]]
                  (when (>= metric-val limit)
                    status-key))
                sorted-levels)
          :ok))
    :ok))

(comment
  (resolve-status 100 {:ok 0 :warning 75 :critical 100 :max 2000}))

(defmethod render-element :bar
  [section-id {:keys [title metric-key levels unit] :or {unit "%"} :as item}]
  (let [zoom-id        (get-in @state/state [:layout :zoom-level] :medium)
        metric-val     (get-in @state/state [:telemetry metric-key])
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
                         0)
        
        ;; Scaling parameters based on zoom level
        val-size       (case zoom-id
                         :small       "text-lg"
                         :medium      "text-2xl"
                         :large       "text-3xl"
                         :extra-large "text-4xl sm:text-5xl"
                         "text-2xl")
        
        bar-height     (case zoom-id
                         :small       "h-1"
                         :medium      "h-1.5"
                         :large       "h-2"
                         :extra-large "h-3"
                         "h-1.5")
        
        title-size     (case zoom-id
                         :small       "text-[9px]"
                         :medium      "text-[10px]"
                         :large       "text-xs"
                         :extra-large "text-sm"
                         "text-[10px]")]
    [action-wrapper item section-id
     [:div {:class "flex flex-col justify-between h-full w-full"}
      [:span {:class (str title-size " font-bold tracking-wider text-slate-500 uppercase")} title]
      [:div {:class (str val-size " font-mono font-extrabold text-slate-100 my-1")}
       formatted-val]
      [:div {:class (str "w-full bg-slate-800 rounded-full overflow-hidden " bar-height)}
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

(defn gauge-view [pct angle stroke-color formatted-val]
  ;; Relative container locks the absolute text label to this gauge block only
  [:div {:class "relative w-full flex flex-col items-center justify-center my-auto overflow-hidden"}
   [:svg {:viewBox "0 0 100 44"
          :preserveAspectRatio "xMidYMid meet"
          :class "w-full h-auto max-h-20 block overflow-visible"}
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

   ;; Numeric Value anchored to the base of the cropped 100x44 viewBox
   [:div {:class "absolute bottom-0 flex items-center justify-center text-center"}
    [:span {:class "text-xl font-mono font-extrabold text-slate-100 tracking-tight leading-none"}
     formatted-val]]])

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
     ;; Removed min-h-[140px] so the card matches the grid's natural row height
     [:div {:class "flex flex-col items-center justify-between h-full w-full pt-1 overflow-hidden"}
      
      ;; Category / Title
      [:span {:class "text-[10px] font-bold tracking-wider text-slate-500 uppercase self-start mb-1"} 
       title]
      
      ;; Arc + Center Readout Container
      [gauge-view pct angle stroke-color formatted-val]]]))

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
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (swap! state/state assoc-in [:layout :zoom-level] :medium))

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

(defn section-view [{:keys [id title buttons]}]
  (let [sec-id  (keyword id)
        zoom-id (get-in @state/state [:layout :zoom-level] :medium)
        preset  (get-zoom-preset zoom-id)
        min-px  (:min-width preset)]
    [:section {:class "w-full col-span-full mb-6 snap-none overflow-x-hidden"}
     (when (seq title)
       ;; Removed -mx-4 px-4 to prevent sub-pixel layout width expansion
       [:div {:class "sticky top-0 z-10 py-2 bg-slate-950/90 backdrop-blur-md border-b border-slate-800/80 flex items-center gap-3 select-none mb-3"}
        [:span {:class "text-xs font-bold tracking-wider uppercase text-slate-400"} title]
        [:div {:class "h-[1px] flex-grow bg-slate-800/80"}]])

     [:div {:class "grid snap-none w-full"
            :style {:grid-template-columns (str "repeat(auto-fill, minmax(" min-px "px, 1fr))")
                    :gap "0.75rem"}}
      (for [b buttons
            :let [b-key (or (:id b) (:title b))]]
        ^{:key (str b-key)}
        [render-element sec-id b])]]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn section-dots
  "Renders static pagination dots for mobile screens."
  [sections active-id]
  [:div {:class "flex sm:hidden justify-center items-center gap-2 py-3 flex-shrink-0 w-full z-10"}
   (for [sec sections
         :let [sec-id (keyword (:id sec))
               active? (= sec-id active-id)]]
     ^{:key (str sec-id)}
     [:div {:class (str "rounded-full transition-all duration-200 "
                        (if active?
                          "w-2.5 h-2.5 bg-blue-500"
                          "w-2 h-2 bg-slate-700"))}])])

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
   [zoom-scaler][status-pill]])


(defn cycle-tab! [direction]
  (let [sections (:sections @state/state)
        sec-ids  (mapv #(keyword (:id %)) sections)
        curr-id  (or (:active-tab @state/state) (first sec-ids))
        idx      (.indexOf sec-ids curr-id)
        cnt      (count sec-ids)]
    (when (and (seq sec-ids) (>= idx 0))
      (let [next-idx (case direction
                       :left  (max 0 (dec idx))
                       :right (min (dec cnt) (inc idx)))
            target   (nth sec-ids next-idx)]
        (when-not (= target curr-id)
          (swap! state/state assoc :active-tab target))))))

(defn scroll-to-tab! [main-el tab-id sections]
  (when main-el
    (let [sec-ids (mapv #(keyword (:id %)) sections)
          idx     (.indexOf sec-ids tab-id)]
      (when (>= idx 0)
        (let [target-left (* idx (.-clientWidth main-el))]
          (.scrollTo main-el #js {:left target-left :behavior "smooth"}))))))

(defn dashboard []
  (let [main-el-atom (atom nil)
        
        handle-scroll
        (fn [e]
          (let [target      (.-target e)
                scroll-left (.-scrollLeft target)
                width       (.-clientWidth target)]
            (when (> width 0)
              (let [idx      (js/Math.round (/ scroll-left width))
                    sections (:sections @state/state)
                    sec      (nth sections idx nil)]
                (when-let [sec-id (some-> sec :id keyword)]
                  (when-not (= sec-id (:active-tab @state/state))
                    (swap! state/state assoc :active-tab sec-id)))))))

        handle-tab-click
        (fn [tab-id sections]
          (swap! state/state assoc :active-tab tab-id)
          (scroll-to-tab! @main-el-atom tab-id sections))

        handle-keydown
        (fn [e]
          (case (.-key e)
            "ArrowLeft"  (do (.preventDefault e) 
                             (cycle-tab! :left)
                             (scroll-to-tab! @main-el-atom (:active-tab @state/state) (:sections @state/state)))
            "ArrowRight" (do (.preventDefault e) 
                             (cycle-tab! :right)
                             (scroll-to-tab! @main-el-atom (:active-tab @state/state) (:sections @state/state)))
            nil))]

    (r/create-class
     {:displayName "Dashboard"

      :component-did-mount
      (fn [_]
        (js/window.addEventListener "keydown" handle-keydown))

      :component-will-unmount
      (fn [_]
        (js/window.removeEventListener "keydown" handle-keydown))

      :reagent-render
      (fn []
        (let [sections  (:sections @state/state)
              active-id (or (:active-tab @state/state)
                            (some-> (first sections) :id keyword))]
          [:div {:class "h-[100dvh] flex flex-col justify-between bg-slate-950 text-slate-100 p-4 pb-6 overflow-hidden"}
           [header-component]
           [tab-header sections active-id #(handle-tab-click % sections)]

           [:main {:ref #(reset! main-el-atom %)
                   :on-scroll handle-scroll
                   :class "flex-1 min-h-0 w-full flex overflow-x-auto snap-x snap-mandatory scroll-smooth no-scrollbar overflow-y-hidden"}
            (for [sec sections
                  :let [sec-id (keyword (:id sec))]]
              ^{:key (str sec-id)}
              [:div {:class "w-full min-w-full h-full flex-shrink-0 snap-center snap-always overflow-y-auto overflow-x-hidden overscroll-y-contain no-scrollbar"}
               [section-view sec]])]

           [section-dots sections active-id]]))})))

#_
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
          ;; Lock total root view to exactly viewport height
          [:div {:class "h-[100dvh] flex flex-col justify-between bg-slate-950 text-slate-100 p-4 pb-6 overflow-hidden"}
           [header-component]
           [tab-header sections active-id #(swap! state/state assoc :active-tab %)]

           ;; Horizontal snap container (takes all remaining height, zero flex-shrink)
           [:main {:ref #(reset! main-ref %)
                   :class "flex-1 min-h-0 w-full flex overflow-x-auto snap-x snap-mandatory scroll-smooth no-scrollbar overflow-y-hidden"}
            (for [sec sections
                  :let [sec-id (keyword (:id sec))]]
              ^{:key (str sec-id)}
              [:div {:class "w-full min-w-full h-full flex-shrink-0 snap-center snap-always overflow-y-auto overflow-x-hidden overscroll-y-contain no-scrollbar"}
               [section-view sec]])]

           ;; Fixed at the bottom of the screen
           [section-dots sections active-id]]))})))
