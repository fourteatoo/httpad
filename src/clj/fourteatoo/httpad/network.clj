(ns fourteatoo.httpad.network
  (:import [java.net NetworkInterface Inet4Address]))

(defn get-lan-ip []
  (try
    (let [interfaces (enumeration-seq (NetworkInterface/getNetworkInterfaces))]
      (first
       (for [iface interfaces
             :when (and (.isUp iface)
                        (not (.isLoopback iface))
                        (not (.isPointToPoint iface)))
             addr (enumeration-seq (.getInetAddresses iface))
             :when (and (instance? Inet4Address addr)
                        (not (.isLoopbackAddress addr)))]
         (.getHostAddress addr))))
    (catch Exception _ nil)))

(defn get-lan-ip-via-socket []
  (try
    (with-open [socket (java.net.DatagramSocket.)]
      ;; 8.8.8.8 is used purely to query the OS routing table
      (.connect socket (java.net.InetAddress/getByName "8.8.8.8") 10002)
      (.. socket getLocalAddress getHostAddress))
    (catch Exception _ nil)))

(defn get-ip-address []
  (or (get-lan-ip-via-socket)
      (get-lan-ip)
      "127.0.0.1"))

