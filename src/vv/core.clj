(ns vv.core
  (:require [clojure.string :as str]
            [clojure.data :as data]
            [vv.io]
            [vv.parser])
  (:import
   (jnr.ffi LibraryLoader)))

(definterface LibUUID
  (^int uuid_generate_time (^{:tag java.nio.ByteBuffer Out true} uuid_t)))

(defn ^:private make-random-node
  []
  (let [bytes (byte-array 6)]
    (.nextBytes (java.security.SecureRandom.) bytes)
    (aset-byte bytes 5 (bit-or (aget bytes 5) 1))
    bytes))

(def libuuid
  (.load (LibraryLoader/create LibUUID) "uuid"))

(def node
  (make-random-node))

(def node-str
  (apply format "%02x:%02x:%02x:%02x:%02x:%02x" node))

(defn ^:private rewrite-node
  [uuid-t]
  (doseq [[ix byte] (map-indexed vector node)]
    (.put uuid-t (+ 10 ix) byte))
  uuid-t)

(defn ^:private pack
  [buffer]
  (let [msb (.getLong buffer)
        lsb (.getLong buffer)]
    (java.util.UUID. msb lsb)))

(comment

  (time (let [uuid-t (java.nio.ByteBuffer/allocate 16)]
     (.uuid_generate_time libuuid uuid-t)
     (pack (rewrite-node uuid-t))))

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
