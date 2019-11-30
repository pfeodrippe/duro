(ns duro.io
  (:refer-clojure :exclude [eval])
  (:require
   [clojure.string :as str]
   [clojure.set :as set])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

(defprotocol VerilatorIO
  (input [this input-data])
  (output [this])
  (eval [this input-data])
  (set-local-signal [this sig arg] [this sig idx arg])
  (get-local-signal [this sig] [this sig idx]))

(definterface NativeLibInterface
  (^jnr.ffi.Pointer create_module [])
  (^jnr.ffi.Pointer get_input_pointer [])
  (^jnr.ffi.Pointer get_output_pointer [])
  (^jnr.ffi.Pointer get_eval_flags_pointer [])
  (^int set_local_signal [^jnr.ffi.Pointer top ^int sig ^int arg])
  (^int get_local_signal [^jnr.ffi.Pointer top ^int sig])
  (^int set_array_signal [^jnr.ffi.Pointer top ^int sig ^int idx ^int arg])
  (^int get_array_signal [^jnr.ffi.Pointer top ^int sig ^int idx])
  (^int eval [^jnr.ffi.Pointer top]))

(defrecord JnrIO [native-lib top input-ptr output-ptr
                  eval-flags-ptr request->out-id in-id->response
                  wires]
  VerilatorIO
  (input [_this input-data]
    (doseq [[op arg] input-data]
      (.putInt ^jnr.ffi.Pointer input-ptr (* (request->out-id op) 4) arg)))
  (output [_this]
    (reduce (fn [acc v]
              (assoc acc
                     (val v)
                     (.getInt ^jnr.ffi.Pointer output-ptr (* (key v) 4))))
            {}
            in-id->response))
  (eval [this input-data]
    (input this input-data)
    ;; signal to cpp code that it's allowed to eval
    (.putInt ^jnr.ffi.Pointer eval-flags-ptr 0 1)
    ;; cpp code will signal to us when eval is done
    (while (not= (.getInt ^jnr.ffi.Pointer eval-flags-ptr 0) 0))
    ;; read data
    (output this))
  (set-local-signal [this sig arg]
    (if (= (:type (wires sig)) :unpackarraydtype)
      (set-local-signal this sig 0 arg)
      (.set_local_signal native-lib top (:id (wires sig)) arg)))
  (get-local-signal [this sig]
    (if (= (:type (wires sig)) :unpackarraydtype)
      (get-local-signal this sig 0)
      (.get_local_signal native-lib top (:id (wires sig)))))
  (set-local-signal [_this sig idx arg]
    (.set_array_signal native-lib top (:id (wires sig)) idx arg))
  (get-local-signal [_this sig idx]
    (.get_array_signal native-lib top (:id (wires sig)) idx)))

(defn jnr-io
  [params lib-path]
  (try (System/load lib-path)
       (catch java.lang.UnsatisfiedLinkError _))
  (let [lib-name (-> lib-path
                     (str/split #"/")
                     last
                     (str/split #"\.")
                     first
                     (str/split #"lib")
                     last)
        native-lib (.load (LibraryLoader/create NativeLibInterface) lib-name)
        top (.create_module native-lib)
        input-ptr (.get_input_pointer native-lib)
        output-ptr (.get_output_pointer native-lib)
        eval-flags-ptr (.get_eval_flags_pointer native-lib)]
    (future (.eval native-lib top))
    (map->JnrIO (assoc params
                       :top top
                       :native-lib native-lib
                       :input-ptr input-ptr
                       :output-ptr output-ptr
                       :eval-flags-ptr eval-flags-ptr))))

(defn jnr-io-destroy
  [{:keys [:eval-flags-ptr]}]
  (.putInt eval-flags-ptr 4 0))
