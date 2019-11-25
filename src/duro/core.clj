(ns duro.core
  (:require
   [clojure.string :as str]
   [clojure.data :as data]
   [duro.io]
   [duro.verilator :as verilator]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)]))

(tufte/add-basic-println-handler!
  {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]
                        :format-id-fn name}})

(defn create-module
  ([mod-path]
   (create-module mod-path {}))
  ([mod-path options]
   (let [{:keys [:top-interface :top-module-name :lib-path
                 :lib-folder :interfaces]}
         (verilator/gen-dynamic-lib mod-path options)

         {:keys [:inputs :outputs :local-signals]} top-interface
         wires (->> interfaces
                   (mapv
                    (fn [[n {:keys [:index] :as signals}]]
                      (clojure.pprint/pprint {:signals signals})
                      (map-indexed
                       (fn [i [t input]]
                         [(keyword (str (name n) "." t) (:name input))
                          {:bit-size (or (some-> (get-in input [:type :left])
                                                 Integer/parseInt
                                                 inc)
                                         1)
                           :id (+ (bit-shift-left index 16) i)}])
                       (apply concat
                              ((juxt #(mapv (fn [v]
                                              (vector "i" v))
                                            (:inputs %))
                                     #(mapv (fn [v]
                                              (vector "o" v))
                                            (:outputs %))
                                     #(mapv (fn [v]
                                              (vector "l" v))
                                            (:local-signals %)))
                               signals)))))
                   (apply concat)
                   (into {}))
         request->out-id (->> inputs
                              (map-indexed
                               (fn [i input]
                                 [(keyword (str top-module-name ".i")
                                           (:name input)) i]))
                              (into {}))
         in-id->response (->> outputs
                              (map-indexed
                               (fn [i output]
                                 [i (keyword (str top-module-name ".o")
                                             (:name output))]))
                              (into {}))
         top (duro.io/jnr-io
              {:request->out-id request->out-id
               :in-id->response in-id->response
               :wires wires}
              lib-path)]
     {:top top
      :interfaces interfaces})))

(defmacro with-module
  [module mod-path options & body]
  `(let [~module (create-module ~mod-path ~options)
         top# (:top ~module)]
     (try
       ~@body
       (finally
         (duro.io/jnr-io-destroy top#)))))

(comment

  (let [{:keys [:top-interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib "ALU32Bit.v")

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs :local-signals]} top-interface
        jnr-io (duro.io/jnr-io
                {:request->out-id (->> inputs
                                       (map-indexed
                                        (fn [i input]
                                          [(keyword "alu" input) i]))
                                       (into {}))
                 :in-id->response (->> outputs
                                       (map-indexed
                                        (fn [i output]
                                          [i (keyword "alu" output)]))
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
                            (merge input (p :durov (duro.io/eval jnr-io input))))))
                       (finally
                         (duro.io/jnr-io-destroy jnr-io))))))

  (let [{:keys [:top-interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib "ProgramCounter.v")

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs]} top-interface
        jnr-io (duro.io/jnr-io
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
                    (duro.io/eval jnr-io {:Clk 0})
                    (merge input (p :durov (duro.io/eval jnr-io input))))))
               (finally
                 (duro.io/jnr-io-destroy jnr-io)))))

  (let [{:keys [:top-interface :lib-path :lib-folder :interfaces]}
        (verilator/gen-dynamic-lib
         "zipcpu/rtl/core/div.v"
         {:mod-debug? false})

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs]} top-interface
        signal->id (->> interfaces
                        (mapv
                         (fn [[n {:keys [:index] :as signals}]]
                           (map-indexed
                            (fn [i [t input]]
                              [(keyword (str (name n) "." t) input)
                               (+ (bit-shift-left index 16) i)])
                            (apply concat
                                   ((juxt #(mapv (fn [v]
                                                   (vector "i" v))
                                                 (:inputs %))
                                          #(mapv (fn [v]
                                                   (vector "o" v))
                                                 (:outputs %))
                                          #(mapv (fn [v]
                                                   (vector "l" v))
                                                 (:local-signals %)))
                                    signals)))))
                        (apply concat)
                        (into {}))
        jnr-io (duro.io/jnr-io
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
        tick (fn [input]
               (duro.io/eval jnr-io {})
               (duro.io/eval jnr-io (assoc input :i_clk 1))
               (duro.io/eval jnr-io {:i_clk 0}))
        reset (fn []
                (tick {:i_reset 1}))
        set-signal #(duro.io/set-local-signal
                     jnr-io (signal->id %1) %2)
        get-signal #(duro.io/get-local-signal
                     jnr-io (signal->id %1))]
    (profile
     {}
     (try
       (tick {:i_clk 0})
       (reset)
       (tick {:i_reset 0})
       (tick {:i_wr 1
              :i_signed 0
              :i_numerator 33
              :i_denominator 3})
       (loop [output (tick {:i_wr 0
                            :i_signed 0
                            :i_numerator 0
                            :i_denominator 0})
              i 0]
         (cond
           (> i 50) (throw (ex-info "Errrr!!" {:i i
                                               :output output}))
           (zero? (:o_valid output)) (do (println output)
                                         (recur (tick {})
                                                (inc i)))
           :else output))
       (finally
         (duro.io/jnr-io-destroy jnr-io)))))

  (let [{:keys [:top-interface :lib-path :lib-folder]}
        (verilator/gen-dynamic-lib
         "zipcpu/rtl/core/dcache.v"
         {:module-dirs ["zipcpu/rtl" "zipcpu/rtl/core"
                        "zipcpu/rtl/peripherals" "zipcpu/rtl/ex"]
          :mod-debug? true})

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs]} top-interface
        jnr-io (duro.io/jnr-io
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
                (duro.io/eval jnr-io {:i_reset 1}))
        tick (fn [input]
               (duro.io/eval jnr-io {:i_reset 0})
               (duro.io/eval jnr-io (assoc input :i_clk 1))
               (duro.io/eval jnr-io {:i_clk 0}))]
    jnr-io)

  (let [{:keys [:top-interface :lib-path :lib-folder :interfaces]}
        (verilator/memo-gen-dynamic-lib
         "zipcpu/rtl/zipsystem.v"
         {:module-dirs ["zipcpu/rtl" "zipcpu/rtl/core"
                        "zipcpu/rtl/peripherals" "zipcpu/rtl/ex"]
          :mod-debug? true})

        _ (println :lib-folder lib-folder)
        {:keys [:inputs :outputs :local-signals]} top-interface
        signal->id (->> interfaces
                        (mapv
                         (fn [[n {:keys [:index] :as signals}]]
                           (map-indexed
                            (fn [i [t input]]
                              [(keyword (str (name n) "." t) input)
                               (+ (bit-shift-left index 16) i)])
                            (apply concat
                                   ((juxt #(mapv (fn [v]
                                                   (vector "i" v))
                                                 (:inputs %))
                                          #(mapv (fn [v]
                                                   (vector "o" v))
                                                 (:outputs %))
                                          #(mapv (fn [v]
                                                   (vector "l" v))
                                                 (:local-signals %)))
                                    signals)))))
                        (apply concat)
                        (into {}))
        jnr-io (duro.io/jnr-io
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
                (duro.io/eval jnr-io {:zip.i/i_reset 1}))
        tick (fn tick'
               ([input]
                (tick' input {}))
               ([input other]
                (duro.io/eval jnr-io {:zip.i/i_reset 0})
                (duro.io/eval jnr-io (assoc input :zip.i/i_clk 1))
                (duro.io/eval jnr-io {:zip.i/i_clk 0})))]
    (profile {}
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
                     wb-write (fn [a v]
                                (tick {:zip.i/i_dbg_cyc 1
                                       :zip.i/i_dbg_stb 1
                                       :zip.i/i_dbg_we 1
                                       :zip.i/i_dbg_addr
                                       (bit-and (bit-shift-right a 2) 1)

                                       :zip.i/i_dbg_data v})
                                (tick {:zip.i/i_dbg_stb 0})
                                (tick {:zip.i/i_dbg_cyc 0
                                       :zip.i/i_dbg_stb 0}))
                     get-local-signal #(duro.io/get-local-signal
                                        jnr-io (signal->id %))
                     set-local-signal #(duro.io/set-local-signal
                                        jnr-io (signal->id %1) %2)]
                 (set-local-signal :zipsystem.l/cpu_halt 0)
                 (wb-write cmd-reg (bit-or cmd-halt cmd-reset 15))
                 (wb-write cmd-data rambase)
                 (wb-write cmd-reg 15)
                 (loop [output (tick {})
                        i 0]
                   (if (<= i 100)
                     (do (println
                          {:cpu-ipc (get-local-signal :zipsystem.thecpu.l/ipc)
                           :cpu-upc (get-local-signal :zipsystem.thecpu.l/upc)
                           :alu-pc (get-local-signal :zipsystem.thecpu.l/alu_pc)})
                         (recur (tick {})
                                (inc i)))
                     output)))
               (finally
                 (duro.io/jnr-io-destroy jnr-io)))))

  ())
