(ns duro.core-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.verilator :as verilator]))

(defn- ticker
  [top]
  (fn tick
    ([] (tick {}))
    ([data]
     (doto top
       (duro.io/eval {})
       (duro.io/eval (assoc data :div.i/i_clk 1)))
     (duro.io/eval top {:div.i/i_clk 0}))))

(defn- resetter
  [top]
  (fn []
    (tick top {:div.i/i_reset 1})))

(defn- inputter
  [top]
  (fn [data]
    (duro.io/input top data)))

(deftest zipcpu-div-test
  (with-module top "zipcpu/rtl/core/div.v" {}
    (let [[tick reset input]
          ((juxt ticker resetter inputter) top)]
      (letfn [(init []
                (tick {:div.i/i_clk 0})
                (reset))
              (request-div [n d signed?]
                (let [out (tick {:div.i/i_reset 0
                                 :div.i/i_wr 1
                                 :div.i/i_signed (if signed? 1 0)
                                 :div.i/i_numerator n
                                 :div.i/i_denominator d})]
                  (testing "o_busy should be `1` after div request"
                    (is (= (:div.o/o_busy out) 1)))
                  (testing "o_valid should be `0` after div request"
                    (is (= (:div.o/o_valid out) 0))))
                (input {:div.i/i_wr 0
                        :div.i/i_signed 0
                        :div.i/i_numerator 0
                        :div.i/i_denominator 0}))
              (div-result []
                (loop [out (tick)
                       i 0]
                  (cond
                    (> i 31)
                    (throw (ex-info "div took longer than 32 cycles, this should never happen"
                                    {:i i :out out}))

                    (zero? (:div.o/o_valid out))
                    (do (testing "o_busy should be `1` while a valid result does not appears"
                          (is (= (:div.o/o_busy out) 1)))
                        (recur (tick) (inc i)))

                    :else out)))]
        (init)
        (request-div 36 3 true)
        (let [out (div-result)]
          (testing "correct quotient"
            (is (= (:div.o/o_quotient out)
                   (/ 36 3))))
          (testing "after div result, o_busy should be `0`"
            (is (zero? (:div.o/o_busy out)))))))))
