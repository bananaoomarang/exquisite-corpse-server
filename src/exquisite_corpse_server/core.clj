(ns exquisite-corpse-server.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! put! close! go go-loop timeout]])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))

(defn handle-get [id]
  (let [doc (mc/find-one-as-map db "stories" { :_id (ObjectId. id)})]
    { :body { :id (.toString (:_id doc)) :story (:story doc) }}))

(defn handle-get-random []
  (let [docs (mc/aggregate db "stories" [{:$sample { :size 1 }}])
        doc  (first docs)]
    { :body
     { :id (-> doc :_id .toString)
      :story (:story doc)}}))

(defn handle-post [body]
  (let [doc (mc/insert-and-return db "stories" { :story (:story body) })]
    { :body { :id (.toString (:_id doc)) :story (:story doc)} }))

(defn handle-patch [id next-line]
  (mc/update-by-id db "stories" (ObjectId. id) { :$push { :story next-line}})
  (handle-get id))



(defn handle-websocket [req]
  (with-channel req ws-ch
    (go-loop [n 0]
      (prn n)
      (<! (timeout 1000))
      (>! ws-ch "Hey there client")
      (recur (inc n)))))

(defroutes app-routes
  (GET "/" [] "API IS GOOOOO")
  (GET "/story" [] (handle-get-random))
  (GET "/story/:id" [id] (handle-get id))
  (POST "/story" { body :body } (handle-post body))
  (PATCH "/story/:id" request (handle-patch (-> request :params :id) (-> request :body :nextLine)))
  (GET "/chord" req (handle-websocket req))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-cors #".*")
      (wrap-json-body { :keywords? true })
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)))

(defn -main []
  (run-server app {:port 3000}))
