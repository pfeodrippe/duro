(ns duro.core-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.vcd]
   [duro.verilator :as verilator]))

;; some helper functions
(defn- ticker
  [top clk]
  (if (core/tracing? top)
    (let [counter (atom 0)]
      (fn tick
        ([] (tick {}))
        ([data]
         (swap! counter inc)
         (doto top
           (duro.io/eval {})
           (duro.core/dump-values (- (* 10 @counter) 2))
           (duro.io/eval (assoc data clk 1))
           (duro.core/dump-values (* 10 @counter)))
         (let [out (duro.io/eval top {clk 0})]
           (duro.core/dump-values top (+ 5 (* 10 @counter)))
           out))))
    (fn tick
      ([] (tick {}))
      ([data]
       (duro.io/eval top {})
       (duro.io/eval top (assoc data clk 1))
       (duro.io/eval top {clk 0})))))

(defn- inputter
  [top]
  (fn [data]
    (duro.io/input top data)))

(defn one?
  [v]
  (= v 1))

;; tests
(deftest zipcpu-div-test
  (with-module module "zipcpu/rtl/core/div.v" {:mod-debug? false
                                               :trace? true
                                               :trace-path "janoa.vcd"}
    ;; setup
    (let [{:keys [:top :interfaces]} module
          tick (ticker top :div.i/i_clk)
          input (inputter top)]
      (letfn [(init []
                (tick {:div.i/i_clk 0})
                (tick {:div.i/i_reset 1}))
              (request-div [n d signed?]
                (let [out (tick {:div.i/i_reset 0
                                 :div.i/i_wr 1
                                 :div.i/i_signed (if signed? 1 0)
                                 :div.i/i_numerator n
                                 :div.i/i_denominator d})]
                  (assert (one? (:div.o/o_busy out))
                          "o_busy should be `1` soon after div request")
                  (assert (zero? (:div.o/o_valid out))
                          "o_valid should be `0` soon after div request"))
                (input {:div.i/i_wr 0
                        :div.i/i_signed 0
                        :div.i/i_numerator 0
                        :div.i/i_denominator 0}))
              (wait-div-result []
                (loop [out (tick)
                       i 0]
                  (cond
                    (> i 33)
                    (throw (ex-info "div took longer than 34 cycles, this should never happen!"
                                    {:i i :out out}))

                    (zero? (:div.o/o_valid out))
                    (do (assert (one? (:div.o/o_busy out))
                                "o_busy should be `1` while a valid result does not appears")
                        (recur (tick) (inc i)))

                    :else out)))]
        (init)

        ;; division tests
        (doseq [[n d signed?] (concat
                               [[36 3 false]
                                [12 4 false]
                                [9 10 false]
                                [64 20 false]
                                [12 -3 true]
                                [0 -4 true]
                                [0 0 false]
                                [1 0 true]]
                               (mapv (fn [i]
                                       [(bit-shift-left 1 30) i true])
                                     (range 32)))]
          (testing {:n n :d d :signed? signed?}
            (do (request-div n d signed?)
                (let [out (wait-div-result)]
                  (testing "correct quotient"
                    (if (zero? d)
                      (is (one? (:div.o/o_err out)))
                      (do
                        (assert (zero? (:div.o/o_err out)))
                        (is (= (quot n d) (:div.o/o_quotient out))))))
                  (assert (zero? (:div.o/o_busy out))
                          "after div result, o_busy should be `0`")))))))))
