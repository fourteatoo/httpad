(ns fourteatoo.httpad.util)

(defn haptic!
  "Triggers a short haptic vibration pulse if supported by the client platform."
  ([] (haptic! 12)) ; Default 12ms crisp click feedback
  ([ms]
   (when (exists? js/navigator.vibrate)
     (.vibrate js/navigator ms))))
