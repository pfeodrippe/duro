(ns duro.core-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.verilator :as verilator]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)]))

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
  (let [tick (ticker top)]
    (fn []
      (tick {:div.i/i_reset 1}))))

(defn- inputter
  [top]
  (fn [data]
    (duro.io/input top data)))

(defn one?
  [v]
  (= v 1))

(deftest zipcpu-div-test
  (with-module module "zipcpu/rtl/core/div.v" {:mod-debug? true}
    ;; Setup
    (let [{:keys [:top :interfaces]} module
          [tick reset input]
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
                  (testing "o_busy should be `1` soon after div request"
                    (is (one? (:div.o/o_busy out))))
                  (testing "o_valid should be `0` soon after div request"
                    (is (zero? (:div.o/o_valid out)))))
                (input {:div.i/i_wr 0
                        :div.i/i_signed 0
                        :div.i/i_numerator 0
                        :div.i/i_denominator 0}))
              (div-result []
                (loop [out (tick)
                       i 0]
                  (cond
                    (> i 33)
                    (throw (ex-info "div took longer than 34 cycles, this should never happen!"
                                    {:i i :out out}))

                    (zero? (:div.o/o_valid out))
                    (do (testing "o_busy should be `1` while a valid result does not appears"
                          (is (one? (:div.o/o_busy out))))
                        (recur (tick) (inc i)))

                    :else out)))]
        (init)

        ;; Tests
        (time
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
                                      (range 3)))]
           (testing {:n n :d d :signed? signed?}
             (do (request-div n d signed?)
                 (let [out (div-result)]
                   (testing "correct quotient"
                     (if (zero? d)
                       (is (one? (:div.o/o_err out)))
                       (do
                         (is (zero? (:div.o/o_err out)))
                         (is (= (quot n d) (:div.o/o_quotient out))))))
                   (testing "after div result, o_busy should be `0`"
                     (is (zero? (:div.o/o_busy out)))))))))
        top))))
