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
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(defn handle-get [id]
  (let [doc (mc/find-one-as-map db "stories" { :_id (ObjectId. id)})]
    { :body { :id (.toString (:_id doc)) :story (:story doc) }}))

(defn test-fast-server>user-pushes
  "Quickly pushes 100 events to all connected users. Note that this'll be
  fast+reliable even over Ajax!"
  []
  (doseq [uid (:any @connected-uids)]
    (doseq [i (range 100)]
      (chsk-send! uid [:some/thing (str "hello " i "!!")]))))

(defn handle-get-random []
  (chsk-send! (:any @connected-uids) [:some/thing (str "hello!!")])
  (println "SENT")
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
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
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
