(ns vv.io
  (:refer-clojure :exclude [eval])
  (:require
   [clojure.string :as str]
   [clojure.set :as set]
   [taoensso.tufte :as tufte :refer (defnp p profiled profile)])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

(defprotocol VerilatorIO
  (eval [this input-data] [this input-data local-signal])
  (set-submodule-local-signal [this sig arg])
  (get-submodule-local-signal [this sig]))

(definterface NativeLibInterface
  (^jnr.ffi.Pointer create_module [])
  (^jnr.ffi.Pointer get_input_pointer [])
  (^jnr.ffi.Pointer get_output_pointer [])
  (^jnr.ffi.Pointer get_local_signal_pointer [])
  (^jnr.ffi.Pointer get_eval_flags_pointer [])
  (^int set_submodule_local_signal [^jnr.ffi.Pointer top ^int sig ^int arg])
  (^int get_submodule_local_signal [^jnr.ffi.Pointer top ^int sig])
  (^int eval [^jnr.ffi.Pointer top])
  (^int eval_with_debug [^jnr.ffi.Pointer top]))

(defrecord JnrIO
    [native-lib top input-ptr output-ptr local-signal-ptr
     eval-flags-ptr request->out-id in-id->response local-signal->id]
    VerilatorIO
    (eval [this input-data]
      (eval this input-data {}))
    (eval [_this input-data local-signal]
      (try
        (doseq [[op arg] input-data]
          (p :put-int
             (.putInt ^jnr.ffi.Pointer input-ptr (* (request->out-id op) 4) arg)))
        (doseq [[op arg] local-signal]
          (p :put-int
             (.putInt ^jnr.ffi.Pointer local-signal-ptr (* (local-signal->id op) 4) arg)))
        (p :eval
           (.putInt ^jnr.ffi.Pointer eval-flags-ptr 0 1))
        ;; read data
        (p :parse-out
           (p :waiting (while (not= (.getInt ^jnr.ffi.Pointer eval-flags-ptr 0) 0)))
           (merge
            (reduce (fn [acc v]
                      (assoc acc
                             (val v)
                             (.getInt ^jnr.ffi.Pointer output-ptr (* (key v) 4))))
                    {}
                    in-id->response)
            (some->> (seq local-signal->id)
                     set/map-invert
                     (reduce (fn [acc v]
                               (assoc acc
                                      (val v)
                                      (.getInt ^jnr.ffi.Pointer local-signal-ptr (* (key v) 4))))
                             {}))))))
    (set-submodule-local-signal [_this sig arg]
      (.set_submodule_local_signal native-lib top sig arg))
    (get-submodule-local-signal [_this sig]
      (.get_submodule_local_signal native-lib top sig)))

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
        local-signal-ptr (.get_local_signal_pointer native-lib)
        eval-flags-ptr (.get_eval_flags_pointer native-lib)]
    (future (clojure.pprint/pprint
             {:FUTURE>>>>>>>>> @(future (.eval_with_debug native-lib top))}))
    (map->JnrIO (assoc params
                       :top top
                       :native-lib native-lib
                       :input-ptr input-ptr
                       :output-ptr output-ptr
                       :local-signal-ptr local-signal-ptr
                       :eval-flags-ptr eval-flags-ptr))))

(defn jnr-io-destroy
  [{:keys [:eval-flags-ptr]}]
  (.putInt eval-flags-ptr 4 0))
