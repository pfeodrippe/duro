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
  (^int process_command
   [^jnr.ffi.Pointer top ^int command ^long command_value])
  (^jnr.ffi.Pointer read_module [^jnr.ffi.Pointer top])
  (^int eval [^jnr.ffi.Pointer top])
  (^jnr.ffi.Pointer get_output_pointer []))

(defrecord JnrIO
    [native-lib top output-ptr request->out-id in-id->response]
    VerilatorIO
    (eval [this input-data]
      (try
        (doseq [[op arg] input-data]
          (p op (.process_command native-lib top (request->out-id op) arg)))
        (p :eval (.eval native-lib top))
        ;; read data
        (p :parse-out
           (->> in-id->response
                (mapv (fn [[id attr]]
                        [attr (p :get-int
                                 (.getInt ^jnr.ffi.Pointer output-ptr (* id 4)))]))
                (into {}))))))

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
        output-ptr (.get_output_pointer native-lib)]
    (map->JnrIO (assoc params
                       :top top
                       :native-lib native-lib
                       :output-ptr output-ptr))))
