(ns exquisite-corpse-server.models
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq]
            [clojure.core.async :refer [<! >! put! close! chan go go-loop timeout mult tap untap alt!]])
  (:import org.bson.types.ObjectId))

(def conn (mg/connect))
(def db   (mg/get-db conn "exquisite_stories"))
(defonce rooms (atom {}))

(def default-story {:max-line-count 10
                    :finished false
                    :lines [{:text "Once upon a time…"}]})

;; TODO use a spec!
(defn- normalize-story [story]
  (-> story
      (assoc :id (.toString (:_id story)))
      (dissoc :_id)))

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

(defn get-room-user-count [{:keys [id]}]
  (let [room (get @rooms id)]
    (if room
      (:user-count room)
      0)))

(defn list-top-stories
  ([] (list-top-stories true))

  ([finished?]
   (let [docs (mq/with-collection db "stories"
                (mq/find { :finished (if finished? { :$eq finished? } { :$ne true })})
                (mq/sort { :$natural -1 })
                (mq/limit 20))]

     (->> docs
          (map normalize-story)
          (map #(assoc % :user-count (get-room-user-count %)))))))

(defn mark-finished [id]
  (let [oid       (ObjectId. id)
        story     (read-story id)
        lines     (:lines story)
        max-lines (:max-line-count story)
        finished  (= max-lines (count lines))]
    (when finished
      (mc/update-by-id db "stories" oid { :$set { :finished true }}))))

(defn update-story [id line]
  (let [oid (ObjectId. id)]

    (mc/update-by-id db "stories" oid { :$push { :lines line }})
    (mark-finished id)
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
         (println "Creating room…")
         (swap! rooms assoc story-id {:ch ch :ch-mult ch-mult :user-count 0})
         (get-room story-id))))))

(defn update-room-user-count [story-id n]
  (let [old-room  (get-room story-id)
        old-count (:user-count old-room)
        new-count (+ n old-count)
        new-rooms (swap! rooms assoc-in [story-id :user-count] new-count)
        new-room  (get new-rooms story-id)]

    new-room))
