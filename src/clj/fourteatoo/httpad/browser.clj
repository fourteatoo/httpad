(ns fourteatoo.httpad.browser
  (:import [java.awt Desktop Desktop$Action]
           [java.net URI]))


(defn open-browser [url]
  (try
    (if (and (Desktop/isDesktopSupported)
             (.isSupported (Desktop/getDesktop) Desktop$Action/BROWSE))
      (.browse (Desktop/getDesktop) (URI/create url))
      (println "Note: Desktop browsing is not supported on this environment."))
    (catch Exception e
      (println "Failed to automatically open browser:" (.getMessage e)))))
