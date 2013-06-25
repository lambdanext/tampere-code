(ns tron.core
  (:require [quil.core :as q]))

(def size "size of the square arena" 60)
(def scale 10)
(def sleep-length "time in ms between turns" 200)

(def arena
  (mapv vec (partition size
              (repeatedly (* size size) #(ref nil)))))

(defn blank-arena []
  (dosync
    (doseq [row arena r row]
      (ref-set r nil))))

(defn setup []
  (q/color-mode :hsb)
  (q/smooth)
  (q/frame-rate 10))

(defn draw []
  (q/background 0)
  (dosync 
    (doseq [x (range 0 size)
            y (range 0 size)]
      (when-let [hue (some-> arena (get-in [x y]) deref deref)]
        (q/fill (q/color hue 255 255))
        (q/rect (* scale x) (* scale y) scale scale)))))

(q/defsketch tron
  :title "TRON"
  :setup setup
  :draw draw
  :size [(* scale size) (* scale size)])

(defn valid-pos? [[i j]]
  (and (< -1 i size) (< -1 j size)))

(def legal-moves #{[0 1] [1 0] [0 -1] [-1 0]})

(defn valid-move? [from to]
  (contains? legal-moves (map - to from)))

(def ^:private bots-gen (atom 0))

(defn stop! [] (swap! bots-gen inc))

(defn biker [arena strategy]
  (let [look (fn [pos] (if (valid-pos? pos)
                         (some-> arena (get-in pos) deref deref)
                         :wall))
        gen @bots-gen] 
    (fn self [{:keys [state hueref] :as agt-state}]
	    (dosync
	      (let [t (java.lang.System/currentTimeMillis)
              state' (strategy look state)
              t (- (java.lang.System/currentTimeMillis) t)
              pos' (:pos state')
              moved (when (and (< t sleep-length) 
                            (valid-move? (:pos state) pos')
                            (nil? (look pos')))
                      (ref-set (get-in arena (:pos state')) hueref))]
         (if (and (= gen @bots-gen) moved)
	        (do
	          (Thread/sleep (- sleep-length t))
	          (send-off *agent* self)
	          (assoc agt-state :state state'))
	        (let [hue @hueref]
            (ref-set hueref nil)
	          (println "arghhh" hue)
	          (assoc agt-state :dead true))))))))

(defn spawn-biker
  ([strategy]
    (spawn-biker strategy (rand-int 255)))
  ([strategy hue]
    (send-off (agent {:state {:pos [(rand-int size)
                                    (rand-int size)]}
                      :hueref (ref hue)})
      (biker arena strategy))))

