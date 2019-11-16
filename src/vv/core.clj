(ns vv.core
  (:require
   [clojure.string :as str]
   [clojure.data :as data]
   [vv.io]
   [vv.parser]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)]))

(tufte/add-basic-println-handler!
  {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]
                        :format-id-fn name}})

(comment

  (let [{:keys [:inputs :outputs]} (vv.parser/module-interface
                                    "obj_dir/VALU32Bit.xml")
        jnr-io (vv.io/jnr-io
                {:request->out-id (->> inputs
                                       (map-indexed
                                        (fn [i input]
                                          [input i]))
                                       (into {}))
                 :in-id->response (->> outputs
                                       (map-indexed
                                        (fn [i output]
                                          [i output]))
                                       (into {}))}
                "/Users/feodrippe/dev/verilog-ex/obj_dir/libfob34.dylib")]
    (profile {}
             (every? (fn [{pc-result "ALUResult"
                           zero "Zero"
                           a "A"
                           b "B"}]
                       (p :test
                        (let [expected-result (- a b)]
                          (and (= pc-result expected-result)
                               (if (zero? expected-result) (= zero 1) (= zero 0))))))
                     (doall
                      (for [i (range 600000)]
                        (let [input {"ALUControl" 2r0110
                                     "A" (* 2 i)
                                     "B" (- (* 4 i) 50)}]
                          (merge input (vv.io/eval jnr-io input))))))))

  ())
