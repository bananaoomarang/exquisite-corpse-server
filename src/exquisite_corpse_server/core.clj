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
            [monger.core :as mg]
            [clj-uuid :as uuid]
            [monger.collection :as mc]
            [chord.http-kit :refer [with-channel]]
            [clojure.core.async :refer [<! >! put! close! chan go go-loop timeout mult tap untap alt!]])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))

(defn send-messages [ws-ch]
  (go-loop []
    (<! (timeout 5000))
    (>! ws-ch {:sneaky "json"})
    (recur)))

(defn receive-messages [ws-ch]
  (go-loop []
    (let [{:keys [message]} (<! ws-ch)]
      (if message
        (do
          (println message)
          (recur))
        (do
          (close! ws-ch)
          (println "DISCONNECTED"))))))

(defn handle-websocket [{:keys [ch ch-mult]} req]
  (let [tap-chan (chan)
        user-id (uuid/v4)]
    
    (tap ch-mult tap-chan)

    (println (format "Opened connection from %s, user-id %s"
             (:remote-addr req)
             user-id))

    (with-channel req ws-ch
      {:format :json}

      (go
        (>! ch {:type :user-joined
                      :user-id user-id})

        (loop []
          (alt!
            tap-chan ([message] (if message
                                  (do
                                    (>! ws-ch message)
                                    (recur))

                                  (close! ws-ch)))
            ws-ch ([ws-message] (if ws-message
                                  (do
                                    (>! ch {:type :user-action
                                            :message (:message ws-message)
                                            :user-id user-id})
                                    (recur))

                                  (do
                                    (untap ch-mult tap-chan)
                                    (>! ch {:type :user-left
                                            :user-id user-id}))))))))))

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

(let [ch      (chan)
      ch-mult (mult ch)]
  (defroutes app-routes
    (GET "/" [] "API IS GOOOOO")
    (GET "/story" [] (handle-get-random))
    (GET "/story/:id" [id] (handle-get id))
    (POST "/story" { body :body } (handle-post body))
    (PATCH "/story/:id" request (handle-patch (-> request :params :id) (-> request :body :nextLine)))
    (GET "/chord" [] (partial handle-websocket {:ch ch :ch-mult ch-mult}))
    (route/not-found "Not Found")))

(def in-dev?
  (= (System/getenv "RING_DEV") "true"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-cors #".*")
      (wrap-json-body { :keywords? true })
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)))

(defn -main []
  (let [handler (if in-dev?
                  (reload/wrap-reload (site #'app))
                  (site app))]
    (run-server handler {:port 3000})))
