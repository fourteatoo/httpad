(defproject io.github.fourteatoo/httpad "0.1.0-SNAPSHOT"
  :description "Minimalist Web-Based Macropad"
  :url "https://github.com/fourteatoo/httpad"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.6"]
                 [http-kit/http-kit "2.8.1"]
                 [ring/ring-core "1.15.5"]
                 [ring/ring-codec "1.3.0"]
                 [cheshire/cheshire "6.2.0"]
                 [camel-snake-kebab "0.4.3"]
                 [mount "0.1.24"]
                 [cprop "0.1.21"]
                 [diehard "0.12.1"]                 
                 [spootnik/unilog "0.7.32"]
                 [org.clojure/tools.logging "1.3.1"]
                 ;; WARNING: Version 2.x breaks compatibility
                 [reagent "1.3.0"]
                 [thheller/shadow-cljs "3.5.1"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.5"]
                 ;; Tailwind CLI packed as a runnable Java jar dependency
                 [org.webjars.npm/tailwindcss "4.3.3"]
                 [org.clojure/clojurescript "1.12.145"]
                 [ring-transit "0.1.6"]
                 [com.cognitect/transit-cljs "0.8.280"]
                 [com.github.oshi/oshi-core "6.6.5"]
                 #_[org.slf4j/slf4j-simple "2.0.19"]]

  :plugins [#_[lein-cljsbuild "1.1.8"]
            [lein-shell "0.5.0"]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :main ^:skip-aot fourteatoo.httpad.core
  :aliases {"build-cljs" ["shell" "npx" "shadow-cljs" "release" "client"]}
  :profiles {:uberjar {:aot :all
                       :prep-tasks ["compile" ["shell" "npx" "shadow-cljs" "release" "client"]]
                       :jvm-opts ["-Xmx2g"
                                  "-Dclojure.compiler.direct-linking=true"
                                  "-Djdk.attach.allowAttachSelf"]}}

  #_#_:cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]
                :compiler {:main fourteatoo.httpad.client
                           :output-to "resources/public/js/main.js"
                           :output-dir "resources/public/js/out"
                           :optimizations :none
                           :source-map true}}
               {:id "min"
                :source-paths ["src/cljs"]
                :compiler {:main fourteatoo.httpad.client
                           :output-to "resources/public/js/main.js"
                           :output-dir "resources/public/js/out"
                           :asset-path "/js/out"
                           :optimizations :advanced
                           :pretty-print false}}]})
