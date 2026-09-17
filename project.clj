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
                 [thheller/shadow-cljs "3.5.3"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.5"]
                 ;; Tailwind CLI packed as a runnable Java jar dependency
                 [org.webjars.npm/tailwindcss "4.3.3"]
                 [org.clojure/clojurescript "1.12.145"]
                 [ring-transit "0.1.6"]
                 [com.cognitect/transit-cljs "0.8.280"]
                 [com.github.oshi/oshi-core "7.6.1"]
                 [com.google.zxing/core "3.5.4"]
                 [com.google.zxing/javase "3.5.4"]
                 [cljsjs/qrcode-generator "1.4.4-0"]]

  ;; WARNING: do not include the lein-cljsbuild plugin!  This project
  ;; uses shadow-cljs instead!
  :plugins [[lein-shell "0.5.0"]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :main ^:skip-aot fourteatoo.httpad.core
  :aliases {"npm-install"   ["shell" "npm" "install"]
            "build-cljs"    ["shell" "npx" "shadow-cljs" "release" "client"]
            "ancient-all"   ["do" ["ancient"] ["shell" "npx" "npm-check-updates"]]
            ;; Single entry-point for uberjar packaging:
            ;; Runs npm install -> shadow-cljs release -> standard lein uberjar
            "package"       ["do" ["npm-install"] ["build-cljs"] ["uberjar"]]}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Xmx2g"
                                  "-Dclojure.compiler.direct-linking=true"
                                  "-Djdk.attach.allowAttachSelf"]}})
