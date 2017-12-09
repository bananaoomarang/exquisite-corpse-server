(ns exquisite-corpse-server.generators
  (:require
   [clojure.spec.gen.alpha :as gen])
  (:import org.bson.types.ObjectId)))

;; Feel like there must be a better way
;; to write this… But it works.
(defn object-id-generator []
  (gen/fmap (fn [_] (ObjectId.))
            (gen/vector (gen/char-alpha) 1)))

(defn object-id-str-generator []
  (gen/fmap #(.toString %)
            (object-id-generator)))
