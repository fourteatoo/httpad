(ns fourteatoo.httpad.config
  (:require [cprop.core :refer [load-config]]
            [mount.core :refer [defstate]]))

(defn- user-home []
  (System/getProperty "user.home"))

(defn key-by [f m]
  (into {} (map (juxt f identity) m)))

(defn extract-action-index [sections]
  (->> (map (fn [sec]
              [(:id sec)
               (->> (map (fn [button]
                           [(:id button)
                            (let [actions (if (:actions button)
                                            (->> (map (fn [a]
                                                        [(:id a) (:cmd a)])
                                                      (:actions button))
                                                 (into {}))
                                            {})]
                              (if (:cmd button)
                                (assoc actions :default (:cmd button))
                                actions))])
                         (:buttons sec))
                    (into {}))])
            sections)
       (into {})))

(defstate config
  :start (let [override-file (str (user-home) "/.httpad")
               cfg (load-config
                    :resource "config.edn"
                    :file override-file)]
           (assoc cfg :action-index (extract-action-index (:sections cfg)))))
