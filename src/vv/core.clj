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

  (let [{:keys [:top-interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib "ALU32Bit.v")

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs :local-signals]} top-interface
        jnr-io (vv.io/jnr-io
                {:request->out-id (->> inputs
                                       (map-indexed
                                        (fn [i input]
                                          [(keyword "alu" input) i]))
                                       (into {}))
                 :in-id->response (->> outputs
                                       (map-indexed
                                        (fn [i output]
                                          [i (keyword "alu" output)]))
                                       (into {}))
                 :local-signal->id (->> local-signals
                                        (map-indexed
                                         (fn [i output]
                                           [(keyword "alu.local" output) i]))
                                        (into {}))}
                lib-path)]
    (profile {}
             (every? (fn [{pc-result :alu/ALUResult
                           zero :alu/Zero
                           a :alu/A
                           b :alu/B}]
                       (let [expected-result (- a b)]
                         (and (= pc-result expected-result)
                              (if (zero? expected-result) (= zero 1) (= zero 0)))))
                     (try
                       (doall
                        (for [i (range 600000)]
                          (let [input {:alu/ALUControl 2r0110
                                       :alu/A (* 2 i)
                                       :alu/B (- (* 4 i) 50)}]
                            (merge input (p :vvv (vv.io/eval jnr-io input))))))
                       (finally
                         (vv.io/jnr-io-destroy jnr-io))))))

  (let [{:keys [:interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib "ProgramCounter.v")

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
             (try
               (doall
                (for [i (range 20)]
                  (let [input {:PCNext i
                               :PCWrite 1
                               :Reset 0
                               :Clk 1}]
                    (vv.io/eval jnr-io {:Clk 0})
                    (merge input (p :vvv (vv.io/eval jnr-io input))))))
               (finally
                 (vv.io/jnr-io-destroy jnr-io)))))

  (let [{:keys [:interface :lib-path :lib-folder]}
        (verilator/memo-gen-dynamic-lib "zipcpu/rtl/core/div.v")

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
                lib-path)
        reset (fn []
                (vv.io/eval jnr-io {:i_reset 1}))
        tick (fn [input]
               (vv.io/eval jnr-io {:i_reset 0})
               (vv.io/eval jnr-io (assoc input :i_clk 1))
               (vv.io/eval jnr-io {:i_clk 0}))]
    (profile
     {}
     (try
       (reset)
       (tick {:i_wr 1
              :i_signed 0
              :i_numerator 14
              :i_denominator 7})
       (loop [output (tick {:i_wr 0
                            :i_signed 0
                            :i_numerator 0
                            :i_denominator 0})
              i 0]
         (if (zero? (:o_valid output))
           (do (println output)
               (recur (tick {})
                      (inc i)))
           output))
       #_(doall
          (for [i (range 6)]
            (let [input {:i_op 2r00
                         :i_reset 0
                         :i_stb 1
                         :i_a i
                         :i_b (* 3 i)
                         :i_clk 1}]
              (vv.io/eval jnr-io {:i_clk 0})
              (merge input (p :vvv (vv.io/eval jnr-io input))))))
       (finally
         (vv.io/jnr-io-destroy jnr-io)))))

  (let [{:keys [:top-interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib
         "zipcpu/rtl/zipsystem.v"
         {:module-dirs ["zipcpu/rtl" "zipcpu/rtl/core"
                        "zipcpu/rtl/peripherals" "zipcpu/rtl/ex"]
          :mod-debug? true})

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs :local-signals]} top-interface
        jnr-io (vv.io/jnr-io
                {:request->out-id (->> inputs
                                       (map-indexed
                                        (fn [i input]
                                          [(keyword "zip.i" input) i]))
                                       (into {}))
                 :in-id->response (->> outputs
                                       (map-indexed
                                        (fn [i output]
                                          [i (keyword "zip.o" output)]))
                                       (into {}))
                 :local-signal->id (->> local-signals
                                        (map-indexed
                                         (fn [i s]
                                           [(keyword "zip.l" s) i]))
                                        (into {}))}
                lib-path)
        reset (fn []
                (vv.io/eval jnr-io {:zip.i/i_reset 1}))
        tick (fn [input]
               (vv.io/eval jnr-io {:zip.i/i_reset 0})
               (vv.io/eval jnr-io (assoc input :zip.i/i_clk 1))
               (vv.io/eval jnr-io {:zip.i/i_clk 0}))]
    (try
      (reset)
      (let [cmd-reg 0
            cmd-halt (bit-shift-left 1 10)
            cmd-reset (bit-shift-left 1 6)
            cpu-s-pc 15
            cmd-data 4
            lgramlen 28
            rambase (bit-shift-left 1 lgramlen)
            ramlen (bit-shift-left 1 lgramlen)
            ramwords (bit-shift-left ramlen 2)
            wb-write (fn [a v other]
                       (vv.io/eval jnr-io
                                   {:zip.i/i_dbg_cyc 1
                                    :zip.i/i_dbg_stb 1
                                    :zip.i/i_dbg_we 1
                                    :zip.i/i_dbg_addr (bit-and (bit-shift-right a 2) 1)
                                    :zip.i/i_dbg_data v}
                                   other)
                       (vv.io/eval jnr-io
                                   {:zip.i/i_dbg_stb 0}
                                   other))]
        (wb-write cmd-reg
                  (bit-or cmd-halt cmd-reset 15)
                  {:zip.l/cpu_halt 1}))
      (finally
        (vv.io/jnr-io-destroy jnr-io))))

  ())
