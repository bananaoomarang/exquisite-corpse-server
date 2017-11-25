(ns exquisite-corpse-server.core
  (:require [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :as reload]
            [ring-debug-logging.core :refer [wrap-with-logger]]

            [exquisite-corpse-server.controllers :refer :all])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))

(defroutes app-routes
  (GET "/" [] "API IS GOOOOO")
  (GET "/story" [] (get-random-story))
  (GET "/story/:id" [id] (get-story id))
  (POST "/story" { body :body } (post-story body))
  (PATCH "/story/:id" req (patch-story (-> req :params :id) (-> req :body :line)))
  (GET "/chord/:id" req (get-websocket req))
  (route/not-found "Not Found"))

(def in-dev?
  (= (System/getenv "RING_DEV") "true"))

(def app
  (-> app-routes
      (wrap-json-body { :keywords? true })
      (wrap-cors #".*")
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-defaults api-defaults)
      (wrap-with-logger)))

(defn -main []
  (let [handler (if in-dev?
                  (reload/wrap-reload (site #'app))
                  (site app))]
    (run-server handler {:port 3000})))
