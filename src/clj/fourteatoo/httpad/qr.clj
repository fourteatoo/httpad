(ns fourteatoo.httpad.qr
  (:import [com.google.zxing BarcodeFormat]
           [com.google.zxing.qrcode QRCodeWriter]
           [com.google.zxing.client.j2se MatrixToImageWriter]))

(defn print-terminal-qr [url]
  (let [writer (QRCodeWriter.)
        matrix (.encode writer url BarcodeFormat/QR_CODE 25 25)]
    (println "\nScan with mobile device:")
    (dotimes [y (.getHeight matrix)]
      (dotimes [x (.getWidth matrix)]
        (print (if (.get matrix x y) "██" "  ")))
      (println))))
