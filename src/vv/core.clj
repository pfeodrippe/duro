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

(defn parse-alu-out
  [s]
  (->> (str/split s #" ")
       (mapv #(str/split % #":"))
       (mapv (fn [[id v]]
               [(case id
                  "0" :alu/pc-result
                  "1" :alu/zero)
                (Integer/parseInt v)]))
       (into {})))

(defn run-eval!
  [m]
  (try
    (doseq [c (vec (conj (mapv (fn [[op arg]]
                                 (command-alu op arg))
                               m)
                         (command-alu :eval)))]
      (spit "caramba.txt"
            (str c "\n")
            :append true))
    (while (empty? (slurp "verilator-writer.txt")))
    (parse-alu-out (slurp "verilator-writer.txt"))
    (finally (spit "verilator-writer.txt" ""))))

(comment

  (do
    (spit "verilator-writer.txt" "")
    (time
     (every? (fn [{:keys [:alu/pc-result
                          :a
                          :b]}]
               (= pc-result (bit-or a b)))
             (doall
              (for [i (range 100)]
                (let [input {:alu-control 2r0001
                             :a (* 2 i)
                             :b (* 4 i)}]
                  (merge input (run-eval! input))))))))

  ())
