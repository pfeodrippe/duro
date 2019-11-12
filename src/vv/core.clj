(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]
            [vv.io]))

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
(comment

  (time
   (let [file-based (vv.io/init-file-based-io
                     {:request-file "caramba.txt"
                      :response-file "verilator-writer.txt"
                      :request->out-id {:alu-control "0"
                                        :a "1"
                                        :b "2"
                                        :eval "3"}
                      :in-id->response {"0" :alu/pc-result
                                        "1" :alu/zero}})]
     (every? (fn [{:keys [:alu/pc-result
                          :a
                          :b]}]
               (= pc-result (- a b)))
             (doall
              (for [i (range 1000)]
                (let [input {:alu-control 2r0110
                             :a (* 2 i)
                             :b (* 4 i)}]
                  (merge input (run-eval! input))))))))

  ())
