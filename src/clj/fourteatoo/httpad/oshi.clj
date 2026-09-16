(ns fourteatoo.httpad.oshi
  (:import [oshi SystemInfo]))

;; Cache the singleton reference
(defonce ^:private system-info (SystemInfo.))
(defonce ^:private hal (.getHardware system-info))
(defonce ^:private os (.getOperatingSystem system-info))

(defn cpu-temperature
  "Returns current CPU temperature in °C."
  []
  (-> hal .getSensors .getCpuTemperature))

(defn cpu-load
  "Returns global CPU load percentage over a specified sampling period in ms.
   Defaults to 1000ms."
  ([] (cpu-load 1000))
  ([sample-ms]
   (* (-> hal .getProcessor (.getSystemCpuLoad sample-ms)) 100.0)))

(defn memory-stats
  "Returns memory usage stats as a map in Megabytes."
  []
  (let [mem (.getMemory hal)
        total (.getTotal mem)
        avail (.getAvailable mem)
        bytes->mb #(double (/ % 1024 1024))]
    {:total-mb (bytes->mb total)
     :available-mb (bytes->mb avail)
     :used-mb (bytes->mb (- total avail))
     :used-pct (* (/ (double (- total avail)) total) 100.0)}))

(defn system-overview
  "Returns a high-level summary map of system metrics."
  []
  {:cpu {:temp-c (cpu-temperature)
         :load-pct (cpu-load 500)}
   :memory (memory-stats)
   :os (str os)})

(defn filesystem-stats
  "Returns volume information, ignoring restricted virtual mounts."
  []
  (let [fs (.getFileSystem os)]
    (keep (fn [store]
            (try
              (let [total (.getTotalSpace store)
                    usable (.getUsableSpace store)
                    used (- total usable)
                    bytes->gb #(double (/ % 1024 1024 1024))]
                {:volume (.getVolume store)
                 :mount (.getMount store)
                 :type (.getType store)
                 :total-gb (bytes->gb total)
                 :used-gb (bytes->gb used)
                 :free-gb (bytes->gb usable)
                 :used-pct (if (pos? total)
                             (/ (Math/round (* (/ (double used) total) 10000.0)) 100.0)
                             0.0)})
              (catch Exception _
                ;; Ignore restricted mounts
                nil)))
          (.getFileStores fs))))
