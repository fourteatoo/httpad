(ns fourteatoo.httpad.ui.dashboard
  (:require [reagent.core :as r]
            [fourteatoo.httpad.ws :as ws]
            [fourteatoo.httpad.state :as state]
            [fourteatoo.httpad.util :as u]
            [fourteatoo.httpad.ui.elements :as ele]
            [fourteatoo.httpad.ui.zoom :as zoom]
            [fourteatoo.httpad.ui.header :as head]))


(defn section-view [{:keys [id title buttons]}]
  (let [sec-id  (keyword id)
        zoom-id (get-in @state/state [:layout :zoom-level] :medium)
        preset  (zoom/get-zoom-preset zoom-id)
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
        [ele/render-element sec-id b])]]))

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


(defn- handle-scroll [e sections]
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
  (let [main-el-atom (atom nil)
        prev-tab-atom (atom nil)
        handle-scroll (fn [e]
                        (let [target      (.-target e)
                              scroll-left (.-scrollLeft target)
                              width       (.-clientWidth target)]
                          (when (> width 0)
                            (let [idx      (js/Math.round (/ scroll-left width))
                                  sections (:sections @state/state)
                                  sec      (nth sections idx nil)]
                              (when-let [sec-id (some-> sec :id keyword)]
                                (when-not (= sec-id (:active-tab @state/state))
                                  (reset! prev-tab-atom sec-id) ; Prevent feedback loop when user manually scrolls
                                  (swap! state/state assoc :active-tab sec-id)))))))

        handle-tab-click (fn [tab-id sections]
                           (reset! prev-tab-atom tab-id)
                           (swap! state/state assoc :active-tab tab-id)
                           (scroll-to-tab! @main-el-atom tab-id sections))

        handle-keydown (fn [e]
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
        (js/window.addEventListener "keydown" handle-keydown)
        ;; Initial scroll on mount if active-tab is already set
        (let [sections   (:sections @state/state)
              active-tab (:active-tab @state/state)]
          (when active-tab
            (reset! prev-tab-atom active-tab)
            (scroll-to-tab! @main-el-atom active-tab sections))))

      :component-did-update
      (fn [this]
        ;; Triggers whenever state/state changes and Reagent re-renders
        (let [sections   (:sections @state/state)
              active-tab (or (:active-tab @state/state)
                             (some-> (first sections) :id keyword))]
          ;; If active-tab changed externally (e.g. WebSocket focus change), scroll main container
          (when (and active-tab (not= active-tab @prev-tab-atom))
            (reset! prev-tab-atom active-tab)
            (scroll-to-tab! @main-el-atom active-tab sections))))

      :component-will-unmount
      (fn [_]
        (js/window.removeEventListener "keydown" handle-keydown))

      :reagent-render
      (fn []
        (let [sections  (:sections @state/state)
              active-id (or (:active-tab @state/state)
                            (some-> (first sections) :id keyword))]
          [:div {:class "h-[100dvh] flex flex-col justify-between bg-slate-950 text-slate-100 p-4 pb-6 overflow-hidden"}
           [head/header-component]
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
