(defproject exquisite-corpse-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.6.0"]
                 [com.novemberain/monger "3.1.0"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-devel "1.6.2"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [http-kit "2.2.0"]
                 [danlentz/clj-uuid "0.1.7"]
                 [jarohen/chord "0.8.1"]
                 [bananaoomarang/ring-debug-logging "1.1.0"]
                 [org.clojure/core.async "0.3.443"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-ancient "0.6.14"]]
  :ring {:handler exquisite-corpse-server.core/app}
  :main exquisite-corpse-server.core
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.1"]]}})
