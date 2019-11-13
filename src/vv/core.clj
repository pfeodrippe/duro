(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]
            [vv.io]
            [vv.parser]))

;; ALU
(comment

  (let [file-based (vv.io/file-based-io
                    {:request-file "caramba.txt"
                     :response-file "verilator-writer.txt"
                     :request->out-id {:alu-control "0"
                                       :a "1"
                                       :b "2"
                                       :eval "3"}
                     :in-id->response {"0" :alu/pc-result
                                       "1" :alu/zero}})]
    (time
     (every? (fn [{:keys [:alu/pc-result
                          :a
                          :b]}]
               (= pc-result (bit-and a b)))
             (doall
              (for [i (range 1000)]
                (let [input {:alu-control 2r0000
                             :a (* 2 i)
                             :b (* 4 i)}]
                  (merge input (vv.io/eval file-based input))))))))

  ())
