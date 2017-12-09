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

(defn create-message [type message user-id]
  { :type type
    :message message
   :user-id user-id })

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
        (>! ch (create-message :user-joined {:user-count user-count} user-id))

        (loop []
          (alt!
            tap-chan ([message] (if message
                                  (do
                                    (let [same-user?   (= user-id (:user-id message))
                                          user-joined? (= :user-joined (:type message))]

                                      (if same-user?
                                        (when user-joined? (>! ws-ch (assoc message :type :it-you)))
                                        (>! ws-ch message)))
                                    (recur))

                                  (close! ws-ch)))

            ws-ch ([ws-message] (if ws-message
                                  (do
                                    (>! ch (create-message :user-action (:message ws-message) user-id))
                                    (recur))

                                  (do
                                    (untap ch-mult tap-chan)
                                    (>! ch (create-message
                                            :user-left
                                            {:user-count (:user-count (update-room-user-count story-id -1))}
                                            user-id))
                                    (when (room-empty? story-id)
                                      (remove-room story-id)))))))))))
