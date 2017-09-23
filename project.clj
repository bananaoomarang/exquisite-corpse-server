(defproject exquisite-corpse-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [com.novemberain/monger "3.1.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [http-kit "2.2.0"]
                 [com.taoensso/sente "1.11.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler exquisite-corpse-server.core/app}
  :main exquisite-corpse-server.core
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
