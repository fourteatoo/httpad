(ns fourteatoo.httpad.qr
  (:import [com.google.zxing BarcodeFormat]
           [com.google.zxing.qrcode QRCodeWriter]
           [com.google.zxing.client.j2se MatrixToImageWriter]))


(defn print-large-qr [url]
  (let [writer (QRCodeWriter.)
        matrix (.encode writer url BarcodeFormat/QR_CODE 25 25)]
    (dotimes [y (.getHeight matrix)]
      (dotimes [x (.getWidth matrix)]
        (print (if (.get matrix x y) "██" "  ")))
      (println))))

(comment
  (print-large-qr "http://10.0.0.111:8080/index.html"))


(defn print-small-qr [url]
  (let [writer (QRCodeWriter.)
        matrix (.encode writer url BarcodeFormat/QR_CODE 25 25)
        w      (.getWidth matrix)
        h      (.getHeight matrix)]
    ;; Process two vertical rows at a time
    (doseq [y (range 0 h 2)]
      (doseq [x (range w)]
        (let [top    (.get matrix x y)
              bottom (if (< (inc y) h) (.get matrix x (inc y)) false)]
          (print (cond
                   (and top bottom) "█" ; Both filled
                   top              "▀" ; Top filled, bottom empty
                   bottom           "▄" ; Top empty, bottom filled
                   :else            " ")))) ; Both empty
      (println))))

(comment
  (print-compact-qr "http://10.0.0.111:8080/index.html"))

