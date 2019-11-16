(ns vv.core
  (:require
   [clojure.string :as str]
   [clojure.data :as data]
   [vv.io]
   [vv.verilator :as verilator]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)]))

(tufte/add-basic-println-handler!
  {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]
                        :format-id-fn name}})

(comment

  (let [{:keys [:interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib "ALU32Bit.v")

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs]} interface
        jnr-io (vv.io/jnr-io
                {:request->out-id (->> inputs
                                       (map-indexed
                                        (fn [i input]
                                          [(keyword input) i]))
                                       (into {}))
                 :in-id->response (->> outputs
                                       (map-indexed
                                        (fn [i output]
                                          [i (keyword output)]))
                                       (into {}))}
                lib-path)]
    (profile {}
             (every? (fn [{pc-result :ALUResult
                           zero :Zero
                           a :A
                           b :B}]
                       (let [expected-result (- a b)]
                         (and (= pc-result expected-result)
                              (if (zero? expected-result) (= zero 1) (= zero 0)))))
                     (try
                       (doall
                        (for [i (range 600000)]
                          (let [input {:ALUControl 2r0110
                                       :A (* 2 i)
                                       :B (- (* 4 i) 50)}]
                            (merge input (p :vvv (vv.io/eval jnr-io input))))))
                       (finally
                         (vv.io/jnr-io-destroy jnr-io))))))

  ())
