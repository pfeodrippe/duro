(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]))

(defn command
  ([k]
   (command k 0))
  ([k v]
   (let [cmd (str
              ({:pc-write "3"
                :pc-next "0"
                :clk "1"
                :eval "2"}
               k)
              ":"
              v)]
     (str cmd
          (str/join
           (repeat (- 32 (count cmd)) " "))))))

#_(doseq [i (range 1)]
  (spit "jjj.txt"
        "0:2"))



(defn tick
  ([i old-state rise-m]
   (tick old-state i rise-m {}))
  ([i old-state rise-m fall-m]
   (vec
    (concat
     [(command :clk 0)
      (command :eval)
      (command :clk 1)]
     (some->> (first (data/diff rise-m old-state))
              (mapv (fn [[op arg]] (command op arg))))
     [(command :eval)]))))

(comment

  (time
   (doseq [i (range 10)]
     (doseq [c (tick i {} {:pc-next (* i 1)
                           :pc-write 1})]
       (spit "caramba.txt"
             (str c "\n")
             :append true))))

  #_[(command :clk 0)
     (command :eval)
     (command :clk 1)
     (command :pc-next (* i 2))
     (command :pc-write 1)
     (command :eval)]

  ())

;; ALU
(defn command-alu
  ([k]
   (command-alu k 0))
  ([k v]
   (let [op (case k
              :alu-control "0"
              :a "1"
              :b "2"
              :eval "3")
         cmd (str op ":" v)]
     (str cmd
          (str/join
           (repeat (- 32 (count cmd)) " "))))))

(defn tick-alu
  ([i old-state rise-m]
   (tick-alu old-state i rise-m {}))
  ([i old-state rise-m fall-m]
   (vec
    (concat
     []
     (some->> (first (data/diff rise-m old-state))
              (mapv (fn [[op arg]] (command-alu op arg))))
     [(command-alu :eval)]))))

(comment

  (time
   (doseq [i (range 5)]
     (doseq [c (tick-alu i {} {:alu-control 6
                               :a 1
                               :b i})]
       (spit "caramba.txt"
             (str c "\n")
             :append true))))

  ())
