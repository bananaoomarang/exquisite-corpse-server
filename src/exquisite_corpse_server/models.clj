(ns exquisite-corpse-server.models
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [clojure.core.async :refer [<! >! put! close! chan go go-loop timeout mult tap untap alt!]])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))
(defonce rooms (atom {}))

(def default-story { :lines ["Once upon a time…"]})

(defn- normalize-story [story]
  { :id    (.toString (:_id story))
    :lines (:lines story) })

(defn create-story
  ([]
   (create-story default-story))

  ([story]
   (let [doc (mc/insert-and-return db "stories" story)]

     (normalize-story doc))))

(defn read-story
  ([]
   (let [docs (mc/aggregate db "stories" [{:$sample { :size 1 }}])
         doc  (first docs)]
     (normalize-story doc)))

  ([id]
   (let [oid (ObjectId. id)
         doc (mc/find-one-as-map db "stories" { :_id oid })]

     (if (nil? doc)
       :not-found
       (normalize-story doc)))))

(defn update-story [id line]
  (let [oid (ObjectId. id)]

    (mc/update-by-id db "stories" oid { :$push { :lines line }})
    (read-story id)))

(defn get-random-room-id []
  (rand-nth (keys @rooms)))

(defn get-room
  ([]
   (get-room (get-random-room-id)))

  ([story-id]
   (let [room (get @rooms story-id)]
     (if room
       (do
         (println "FOUND ROOM :)")
         (println room)
         room)

       ;; Otherwise make the room
       (let [ch      (chan)
             ch-mult (mult ch)]
         (println "Creating room...")
         (swap! rooms assoc story-id {:ch ch :ch-mult ch-mult})
         (get-room story-id))))))
