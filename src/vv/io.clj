(ns vv.io
  (:refer-clojure :exclude [eval])
  (:require
   [clojure.string :as str]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

(defprotocol VerilatorIO
  (eval [this input-data]))

(definterface NativeLibInterface
  (^jnr.ffi.Pointer create_module [])
  (^jnr.ffi.Pointer read_module [^jnr.ffi.Pointer top])
  (^jnr.ffi.Pointer get_input_pointer [])
  (^jnr.ffi.Pointer get_output_pointer [])
  (^jnr.ffi.Pointer get_eval_flags_pointer [])
  (^int eval [^jnr.ffi.Pointer top]))

(defrecord JnrIO
    [native-lib top input-ptr output-ptr
     eval-flags-ptr request->out-id in-id->response]
    VerilatorIO
    (eval [this input-data]
      (try
        (doseq [[op arg] input-data]
          (p :put-int
             (.putInt ^jnr.ffi.Pointer input-ptr (* (request->out-id op) 4) arg)))
        (p :eval
           (.putInt ^jnr.ffi.Pointer eval-flags-ptr 0 1))
        ;; read data
        (p :parse-out
           (p :waiting (while (not= (.getInt ^jnr.ffi.Pointer eval-flags-ptr 0) 0)))
           (reduce (fn [acc v]
                     (assoc acc
                            (val v)
                            (.getInt ^jnr.ffi.Pointer output-ptr (* (key v) 4))))
                   {}
                   in-id->response)))))

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
    (future (clojure.pprint/pprint
             {:FUTURE>>>>>>>>> @(future (.eval native-lib top))}))
    (map->JnrIO (assoc params
                       :top top
                       :native-lib native-lib
                       :input-ptr input-ptr
                       :output-ptr output-ptr
                       :eval-flags-ptr eval-flags-ptr))))

(defn jnr-io-destroy
  [{:keys [:eval-flags-ptr]}]
  (.putInt eval-flags-ptr 4 0))

(defn tick
  [io]
  (eval io {}))
