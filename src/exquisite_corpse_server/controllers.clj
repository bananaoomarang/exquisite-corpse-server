(ns exquisite-corpse-server.controllers
  (:require
   [chord.http-kit :refer [with-channel]]
   [clj-uuid :as uuid]
   [clojure.core.async :refer [<! >! put! close! chan go go-loop timeout mult tap untap alt!]]

   [exquisite-corpse-server.models :refer :all]))

(defn post-story [story]
  (let [saved-story (create-story story)]
    { :status 201 :body saved-story }))

(defn get-story [id]
  (let [story (read-story id)]
    (if (= story :not-found)
      { :status 404 }

      { :body story })))

(defn patch-story [id line]
  (let [story (update-story id line)]
    { :body story }))

(defn get-random-story []
  (let [story (read-story)]
    { :body story }))

(defn get-active-stories []
  (let [stories (list-active-stories)]
    { :body stories }))

(defn get-top-stories [finished?]
  (let [stories (list-top-stories finished?)]
    { :body stories }))

(defn get-websocket [req]
  (let [story-id (-> req :params :id)
        user-id (.toString (uuid/v4))
        room (update-room-user-count story-id 1)
        user-count (:user-count room)
        ch (:ch room)
        ch-mult (:ch-mult room)
        tap-chan (chan)]

    (tap ch-mult tap-chan)

    (println (format "Opened connection from %s, user-id %s for story %s"
             (:remote-addr req)
             user-id
             story-id))

    (with-channel req ws-ch
      {:format :transit-json}

      (go
        (>! ch {:type :user-joined
                :user-id user-id
                :user-count user-count})

        (loop []
          (alt!
            tap-chan ([message] (if message
                                  (do
                                    (if (= user-id (:user-id message))
                                      (when (= :user-joined (:type message))
                                        (>! ws-ch (assoc message :type :it-you)))

                                      (>! ws-ch message))
                                    (recur))

                                  (do
                                    (close! ws-ch))))
            ws-ch ([ws-message] (if ws-message
                                  (do
                                    (>! ch {:type :user-action
                                            :message (:message ws-message)
                                            :user-id user-id})
                                    (recur))

                                  (do
                                    (untap ch-mult tap-chan)
                                    (>! ch {:type :user-left
                                            :user-id user-id
                                            :user-count (:user-count (update-room-user-count story-id -1))})
                                    (when (room-empty? story-id)
                                      (remove-room story-id)))))))))))
