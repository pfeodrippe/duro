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

(defn- outputter
  [top]
  (fn []
    (duro.io/output top)))

(defn- one?
  [v]
  (= v 1))

;; tests
(deftest zipcpu-div-test
  (with-module module "zipcpu/rtl/core/div.v" {:mod-debug? false
                                               :trace? true
                                               :trace-path "janoa.vcd"}
    (let [{:keys [:top]} module
          tick (ticker top :div.i/i_clk)
          input (inputter top)]
      ;; setup
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
            (request-div n d signed?)
            (let [out (wait-div-result)]
              (testing "correct quotient"
                (if (zero? d)
                  (is (one? (:div.o/o_err out)))
                  (do
                    (assert (zero? (:div.o/o_err out)))
                    (is (= (quot n d) (:div.o/o_quotient out))))))
              (assert (zero? (:div.o/o_busy out))
                      "after div result, o_busy should be `0`"))))))))

;; TODO: it isn't working correctly... have to check more
#_(deftest zipcpu-zipmmu-test
  (with-module module "zipcpu/bench/rtl/zipmmu_tb.v"
    {:module-dirs ["zipcpu/rtl/peripherals"
                   "zipcpu/bench/rtl"]
     :mod-debug? true
     :trace? true
     :trace-path "janoa.vcd"
     :top-identifier :mmu}
    (let [{:keys [:top]} module
          tick (ticker top :mmu.i/i_clk)
          input (inputter top)
          output (outputter top)
          R_CONTROL 0
          R_STATUS 4
          CONTEXT 0x0fef7]
      (letfn [(init []
                (tick {:mmu.i/i_reset 1
                       :mmu.i/i_ctrl_cyc_stb 0
                       :mmu.i/i_gie 0
                       :mmu.i/i_exe 0
                       :mmu.i/i_wbm_cyc 0
                       :mmu.i/i_wbm_stb 0})
                (input {:mmu.i/i_reset 0}))
              (wb-tick []
                (tick {:mmu.i/i_ctrl_cyc_stb 0
                       :mmu.i/i_wbm_cyc 0
                       :mmu.i/i_wbm_stb 0
                       :mmu.i/i_reset 0}))
              (setup-read [a]
                (input {:mmu.i/i_ctrl_cyc_stb 0
                        :mmu.i/i_wbm_cyc 0
                        :mmu.i/i_wbm_stb 0
                        :mmu.i/i_wb_we 0
                        :mmu.i/i_wb_addr (bit-shift-right a 2)})
                (tick {:mmu.i/i_ctrl_cyc_stb 1})
                (input {:mmu.i/i_ctrl_cyc_stb 0})
                (tick)
                (assert (zero? (:mmu.o/o_rtn_ack (output))))
                (try (:mmu.o/o_rtn_data (output))
                     #_(duro.io/get-local-signal top :mmu.mut.o/o_rtn_data)
                     (finally (tick))))
              (setup-write [a v]
                (input {:mmu.i/i_ctrl_cyc_stb 0
                        :mmu.i/i_exe 0
                        :mmu.i/i_wbm_cyc 0
                        :mmu.i/i_wbm_stb 0
                        :mmu.i/i_wb_we 1
                        :mmu.i/i_wb_addr (bit-shift-right a 2)
                        :mmu.i/i_wb_data v
                        :mmu.i/i_wb_sel 15})
                (tick {:mmu.i/i_ctrl_cyc_stb 1})
                (input {:mmu.i/i_ctrl_cyc_stb 0})
                (assert (zero? (:mmu.o/o_rtn_err (output))))
                (assert (zero? (:mmu.o/o_rtn_miss (output))))
                (tick)
                (assert (zero? (:mmu.o/o_rtn_ack (output))))
                (tick))]
        (init)
        (wb-tick)
        (wb-tick)
        (setup-read R_CONTROL)
        (setup-read R_STATUS)
        (setup-write R_CONTROL CONTEXT)
        (setup-read R_CONTROL)))
    (update module :top dissoc :wire-values)))

(deftest zipcpu-mpy-test
  (with-module module "zipcpu/rtl/core/cpuops.v"
    {:module-dirs ["zipcpu/rtl/core"
                   "zipcpu/rtl"]
     :mod-debug? true
     :trace? true
     :trace-path "mpy.vcd"
     :top-identifier :mpy
     :independent-signals
     {:mpy.cpuops.thempy/mpypipe
      {:type :basicdtype
       :bit-size 2
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.mpypipe"}
      :muma/la
      {:type :basicdtype
       :bit-size 2
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.r_sgn"}
      :muma/la2
      {:type :basicdtype
       :bit-size 64
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.r_smpy_result"}
      :muma/la3
      {:type :basicdtype
       :bit-size 64
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.r_umpy_result"}
      :muma/la4
      {:type :basicdtype
       :bit-size 32
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.r_mpy_a_input"}
      :muma/la5
      {:type :basicdtype
       :bit-size 32
       :representation
       "cpuops.thempy.IMPY.MPN1.MPN2.MPY3CK.r_mpy_b_input"}}}
    (let [{:keys [:top]} module
          tick (ticker top :mpy.i/i_clk)
          input (inputter top)
          output (outputter top)]
      (letfn [(init []
                (tick {:mpy.i/i_reset 1
                       :mpy.i/i_stb 0})
                (input {:mpy.i/i_reset 0})
                (tick)
                (tick)
                (tick))
              (clear-ops []
                (input {:mpy.i/i_stb 0
                        :mpy.i/i_op 0})
                (loop [out (tick)
                       i 0]
                  (cond
                    ;; 10 is arbitrary number
                    (>= i 10)
                    (throw (ex-info "clear-ops took longer than 10 cycles"
                                    {:i i :out out}))

                    (or (one? (:mpy.o/o_busy out))
                        (one? (:mpy.o/o_valid out)))
                    (recur (tick) (inc i))

                    :else (tick))))
              (op [operation a b]
                (when (:mpy.o/o_valid (output))
                  (clear-ops))
                (tick {:mpy.i/i_stb 1
                       :mpy.i/i_op operation
                       :mpy.i/i_a a
                       :mpy.i/i_b b})
                (input {:mpy.i/i_stb 0
                        :mpy.i/i_a 0
                        :mpy.i/i_b 0})
                (loop [out (output)
                       i 0]
                  (cond
                    (>= i 3)
                    (throw (ex-info "multiplication should take 3 cycles to occur"
                                    {:i i
                                     :operation operation
                                     :a a
                                     :b b
                                     :out out}))

                    (zero? (:mpy.o/o_valid out))
                    (recur (tick) (inc i))

                    :else (:mpy.o/o_c out))))
              (mul-test [a b]
                (clear-ops)
                (op 2r1011 a b)
                (tick)
                (tick)
                #_(tick))]
        (init)
        (mul-test 3 5)
        (output)))
    #_(update module :top dissoc :wire-values)))
