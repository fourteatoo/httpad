(ns fourteatoo.httpad.process
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]))

(defn start-process-stream
  "Executes `cmd-args` vector and returns a map {:keys [out-ch stop process]}.
   Lines from process stdout are continuously published onto `out-ch`.
   Calling (stop) closes streams, terminates the process, and closes the channel."
  [cmd-args & {:keys [ch-buffer] :or {ch-buffer 32}}]
  (let [process (-> (ProcessBuilder. ^java.util.List cmd-args)
                    (.redirectErrorStream true)
                    .start)
        reader  (io/reader (.getInputStream process))
        out-ch  (async/chan ch-buffer)
        running (atom true)]
    ;; Reader loop running on a dedicated future
    (future
      (try
        (doseq [line (line-seq reader)
                :while @running]
          (when-not (async/put! out-ch line)
            ;; Stop reading if downstream consumer closed the channel
            (reset! running false)))
        (catch Exception e
          (when @running
            (println "Process stream error (" (first cmd-args) "):" (.getMessage e))))
        (finally
          (reset! running false)
          (.close reader)
          (async/close! out-ch))))
    {:out-ch out-ch
     :process process
     :stop (fn []
             (when @running
               (reset! running false)
               (.destroy process)
               (async/close! out-ch)))}))
