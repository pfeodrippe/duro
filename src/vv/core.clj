(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]
            [vv.io]
            [vv.parser])
  (:import
   (jnr.ffi LibraryLoader Pointer Struct)))

(definterface Eita
  (^jnr.ffi.Pointer create_module [])
  (^int process_command
   [^jnr.ffi.Pointer top ^int command ^long command_value])
  (^jnr.ffi.Pointer read_module [^jnr.ffi.Pointer top]))

#_(System/load "/Users/feodrippe/dev/verilog-ex/obj_dir/libfob16.dylib")

(def libeita
  (.load (LibraryLoader/create Eita) "fob16"))

(comment

  (def top (.create_module libeita))

  (time
   (->
    (doto libeita
      (.process_command top 0 2r0110)
      (.process_command top 1 110)
      (.process_command top 2 100))
    (.process_command top 3 0)))

  [(.getInt (.read_module libeita top) 0)
   (.getInt (.read_module libeita top) 4)]


  ())

;; ALU
(comment

  ;; JNR
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
                "/Users/feodrippe/dev/verilog-ex/obj_dir/libfob16.dylib")]
    (time
     (every? (fn [{pc-result "ALUResult"
                   zero "Zero"
                   a "A"
                   b "B"}]
               (let [expected-result (bit-or a b)]
                 (and (= pc-result expected-result)
                      (if (zero? expected-result) (= zero 1) (= zero 0)))))
             (doall
              (for [i (range 500)]
                (let [input {"ALUControl" 2r0001
                             "A" (* 2 i)
                             "B" (- (* 4 i) 50)}]
                  (merge input (vv.io/eval jnr-io input))))))))

  ;; FILE
  #_(let [{:keys [:inputs :outputs]} (vv.parser/module-interface
                                    "obj_dir/VALU32Bit.xml")
        file-based (vv.io/file-based-io
                    {:request-file "caramba.txt"
                     :response-file "verilator-writer.txt"
                     :request->out-id (->> inputs
                                           (map-indexed
                                            (fn [i input]
                                              [input (str i)]))
                                           (into {}))
                     :in-id->response (->> outputs
                                           (map-indexed
                                            (fn [i output]
                                              [(str i) output]))
                                           (into {}))})]
    (time
     (every? (fn [{pc-result "ALUResult"
                   a "A"
                   b "B"}]
               (= pc-result (- a b)))
             (doall
              (for [i (range 200)]
                (let [input {"ALUControl" 2r0110
                             "A" (* 2 i)
                             "B" (* 4 i)}]
                  (merge input (vv.io/eval file-based input))))))))

  ())
