(ns exquisite-corpse-server.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [monger.core :as mg]
            [monger.collection :as mc])
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

(defroutes app-routes
  (GET "/" [] "API IS GOOOOO")
  (GET "/story" [] (handle-get-random))
  (GET "/story/:id" [id] (handle-get id))
  (POST "/story" { body :body } (handle-post body))
  (PATCH "/story/:id" request (handle-patch (-> request :params :id) (-> request :body :nextLine)))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-cors #".*")
      (wrap-json-body { :keywords? true })
      (wrap-json-response)
      (wrap-defaults api-defaults)))
