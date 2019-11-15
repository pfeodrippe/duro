(ns vv.io
  (:require [clojure.string :as str])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

(defprotocol VerilatorIO
  (eval [this input-data]))

(defrecord JnrIO
    [native-lib top request->out-id in-id->response]
    VerilatorIO
    (eval [this input-data]
      (try
        (doseq [[op arg] input-data]
          (.process_command native-lib top (request->out-id op) arg))
        ;; eval
        (.process_command native-lib top (count request->out-id) 0)
        ;; read data
        (->> in-id->response
             (mapv (fn [[id attr]]
                     [attr (.getInt (.read_module native-lib top) (* id 4))]))
             (into {})))))

(definterface NativeLibInterface
  (^jnr.ffi.Pointer create_module [])
  (^int process_command
   [^jnr.ffi.Pointer top ^int command ^long command_value])
  (^jnr.ffi.Pointer read_module [^jnr.ffi.Pointer top]))

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
        top (.create_module native-lib)]
    (map->JnrIO (assoc params :top top :native-lib native-lib))))
