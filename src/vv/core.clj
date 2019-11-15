(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]
            [vv.io]
            [vv.parser])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

#_(System/load "/Users/feodrippe/dev/verilog-ex/obj_dir/libfob10.dylib")

(definterface Eita
  (^int eita [^int x])
  (^jnr.ffi.Pointer create_module [])
  (^long process_command
   [^jnr.ffi.Pointer top ^int command ^long command_value]))

(def libeita
  (.load (LibraryLoader/create Eita) "fob10"))

(comment

  (time (.eita libeita 334))

  (def top (.create_module libeita))

  (.process_command libeita top 0 2r0110)
  (.process_command libeita top 1 10)
  (.process_command libeita top 2 2)
  (.process_command libeita top 3 0)


  ())

;; ALU
(comment

  (let [{:keys [:inputs :outputs]} (vv.parser/module-interface
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
