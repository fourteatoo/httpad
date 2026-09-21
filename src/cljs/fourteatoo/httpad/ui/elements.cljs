(ns fourteatoo.httpad.ui.elements
  (:require [reagent.core :as r]
            [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.util :as u]))

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

(defn- get-telemetry [path]
  (get-in @state/state
          (concat [:telemetry]
                  (if (vector? path)
                    path
                    [path]))))

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
  [section-id {:keys [title metric-path unit] :or {unit ""} :as item}]
  (let [zoom-id       (get-in @state/state [:layout :zoom-level] :medium)
        metric-val   (get-telemetry metric-path)
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

(defmethod render-element :slider
  [section-id {:keys [id metric-path unit col-span row-span class title delay-ms]
               min-val :min
               max-val :max
               step-val :step
               :or   {min-val 0 max-val 100 step-val 1 unit "%" delay-ms 500}
               :as   item}]
  (let [zoom-id        (get-in @state/state [:layout :zoom-level] :medium)
        cmd-path       [section-id (keyword id)]

        active?        (r/atom false)
        track-el       (atom nil)
        dragging-val   (r/atom min-val)

        timer-id       (atom nil)
        start-pos      (atom nil)
        move-threshold 8

        calc-val (fn [e]
                   (when-let [el @track-el]
                     (let [rect    (.getBoundingClientRect el)
                           x       (- (.-clientX e) (.-left rect))
                           ;; js/Math.max and js/Math.min avoid clojure.core function calls
                           pct     (js/Math.max 0.0 (js/Math.min 1.0 (/ x (.-width rect))))
                           raw     (+ min-val (* pct (- max-val min-val)))
                           stepped (* (js/Math.round (/ raw step-val)) step-val)]
                       stepped)))

        dispatch-value! (fn [v]
                          (ws/send-action! cmd-path [v]))

        clear-timer! (fn []
                       (when @timer-id
                         (js/clearTimeout @timer-id)
                         (reset! timer-id nil)))

        handle-down (fn [e]
                      (let [x (.-clientX e)
                            y (.-clientY e)
                            target (.-target e)
                            pointer-id (.-pointerId e)]
                        (reset! start-pos {:x x :y y})
                        (clear-timer!)

                        (reset! timer-id
                                (js/setTimeout
                                 (fn []
                                   (reset! active? true)
                                   (when (exists? js/navigator.vibrate)
                                     (js/navigator.vibrate 30))
                                   (try
                                     (.setPointerCapture target pointer-id)
                                     (catch js/Error _))
                                   (let [metric-val (get-telemetry metric-path)
                                         v          (or (calc-val e) metric-val min-val)]
                                     (reset! dragging-val v)
                                     (dispatch-value! v)))
                                 delay-ms))))

        handle-move (fn [e]
                      (if @active?
                        (do
                          (.stopPropagation e)
                          (when-let [v (calc-val e)]
                            (when (not= v @dragging-val)
                              (reset! dragging-val v)
                              (dispatch-value! v))))

                        (when (and @timer-id @start-pos)
                          (let [dx (js/Math.abs (- (.-clientX e) (:x @start-pos)))
                                dy (js/Math.abs (- (.-clientY e) (:y @start-pos)))]
                            (when (or (> dx move-threshold) (> dy move-threshold))
                              (clear-timer!))))))

        handle-up   (fn [e]
                      (clear-timer!)
                      (when @active?
                        (.stopPropagation e)
                        (try (.releasePointerCapture (.-target e) (.-pointerId e))
                             (catch js/Error _))
                        (reset! active? false)))

        val-size    (case zoom-id
                      :small       "text-lg"
                      :medium      "text-2xl"
                      :large       "text-3xl"
                      :extra-large "text-4xl sm:text-5xl"
                      "text-2xl")

        bar-height  (case zoom-id
                      :small       "h-1.5"
                      :medium      "h-2"
                      :large       "h-3"
                      :extra-large "h-4"
                      "h-2")

        title-size  (case zoom-id
                      :small       "text-[9px]"
                      :medium      "text-[10px]"
                      :large       "text-xs"
                      :extra-large "text-sm"
                      "text-[10px]")]

    (fn [section-id item]
      (let [telemetry-val (get-telemetry metric-path)
            current-val   (or telemetry-val min-val)
            display-val   (if @active? @dragging-val current-val)
            formatted-val (if (number? display-val)
                            (str display-val unit)
                            "--")
            ;; Using JavaScript interop js/Math prevents any lingering clojure.core symbol conflicts
            pct-fill      (if (number? display-val)
                            (js/Math.min 100 (js/Math.max 0 (* (/ (- display-val min-val) (- max-val min-val)) 100)))
                            0)]
        [action-wrapper (assoc item :cmd nil) section-id
         [:div {:ref            #(reset! track-el %)
                :class          (str "flex flex-col justify-between h-full w-full select-none cursor-pointer transition-all "
                                     (if @active? "touch-none scale-[1.02]" "touch-auto"))
                :on-pointer-down handle-down
                :on-pointer-move handle-move
                :on-pointer-up   handle-up
                :on-pointer-cancel handle-up}

          [:div {:class "flex items-center justify-between w-full"}
           [:span {:class (str title-size " font-bold tracking-wider text-slate-500 uppercase")} title]
           (when @active?
             [:span {:class (str title-size " font-mono text-amber-400 font-bold animate-pulse")} "ADJUSTING"])]

          [:div {:class (str val-size " font-mono font-extrabold my-1 transition-colors "
                             (if @active? "text-amber-400" "text-slate-100"))}
           formatted-val]

          [:div {:class (str "w-full bg-slate-800 rounded-full overflow-hidden transition-all "
                             (if @active? "h-3" bar-height))}
           [:div {:class (str "h-full transition-all "
                              (if @active? "bg-amber-400" "bg-blue-500"))
                  :style {:width (str pct-fill "%")}}]]]]))))

(defmethod render-element :bar
  [section-id {:keys [title metric-path levels unit] :or {unit "%"} :as item}]
  (let [zoom-id        (get-in @state/state [:layout :zoom-level] :medium)
        metric-val     (get-telemetry metric-path)
        current-val    (or metric-val 0)
        current-status (resolve-status current-val levels)
        formatted-val  (if (number? metric-val)
                         (str metric-val unit)
                         "--")
        max-threshold  (or (:max item)
                           (when (seq levels)
                             (apply max (vals levels)))
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
  [section-id {:keys [title metric-path levels unit] :or {unit "%"} :as item}]
  (let [metric-val     (get-telemetry metric-path)
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
