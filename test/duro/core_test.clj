(ns duro.core-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core]
   [duro.io]
   [duro.verilator :as verilator]))

(defn- tick
  [top input]
  (doto top
    (duro.io/eval {})
    (duro.io/eval (assoc input :div.i/i_clk 1)))
  (duro.io/eval top {:div.i/i_clk 0}))

(defn- reset
  [top]
  (tick top {:div.i/i_reset 1}))

(deftest zipcpu-div-test
  (let [{:keys [:top :outputs] :as xx}
        (core/create-module "zipcpu/rtl/core/div.v")]
    (try
      (println :top top)
      (doto top
        (tick {:div.i/i_clk 0})
        (reset)
        (tick {:div.i/i_reset 0})
        (tick {:div.i/i_wr 1
               :div.i/i_signed 0
               :div.i/i_numerator 33
               :div.i/i_denominator 3}))
      (loop [output (tick top {:div.i/i_wr 0
                                 :div.i/i_signed 0
                                 :div.i/i_numerator 0
                                 :div.i/i_denominator 0})
               i 0]
          (cond
            (>= i 32) (throw (ex-info "Errrr!!" {:i i :output output}))
            (zero? (:div.o/o_valid output)) (recur (tick top {}) (inc i))
            :else output))
      (finally
        (duro.io/jnr-io-destroy top)))))
