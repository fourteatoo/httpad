(ns fourteatoo.httpad.network
  (:import [java.net NetworkInterface DatagramSocket Inet4Address InetSocketAddress])
  (:require [fourteatoo.httpad.log :as log]))

(defn get-lan-ip []
  (try
    (->> (NetworkInterface/getNetworkInterfaces)
         enumeration-seq
         (filter #(and (.isUp %) 
                       (not (.isLoopback %)) 
                       (not (.isPointToPoint %))))
         (mapcat #(enumeration-seq (.getInetAddresses %)))
         (filter #(and (instance? Inet4Address %) 
                       (not (.isLoopbackAddress %))))
         (map #(.getHostAddress %))
         first)
    (catch Exception _
      nil)))

(defn get-lan-ip-via-socket []
  (try
    (with-open [socket (DatagramSocket.)]
      ;; Use InetSocketAddress with raw IP byte array to avoid DNS resolution
      (let [target (InetSocketAddress. (Inet4Address/getByAddress (byte-array [8 8 8 8])) 10002)]
        (.connect socket target)
        (.. socket getLocalAddress getHostAddress)))
    (catch Exception _ nil)))

(defn get-ip-address []
  (or (get-lan-ip-via-socket)
      (get-lan-ip)
      "127.0.0.1"))
