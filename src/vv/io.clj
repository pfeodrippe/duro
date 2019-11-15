(ns vv.io
  (:require [clojure.string :as str])
  (:import
   (jnr.ffi LibraryLoader Pointer)))

(defprotocol VerilatorIO
  (eval [this input-data]))

(defn- parse-file-based-request
  ([request->out-id k]
   (parse-file-based-request request->out-id k 0))
  ([request->out-id k v]
   (if-let [op (request->out-id k)]
     (let [cmd (str op ":" v)]
       (str cmd
            (str/join
             (repeat (- 32 (count cmd)) " "))))
     (throw (ex-info "`request->out-id` application to `k` is nil"
                     {:request->out-id request->out-id
                      :key k})))))

(defn- parse-file-based-response
  [in-id->response s]
  (->> (str/split s #" ")
       (mapv #(str/split % #":"))
       (mapv (fn [[id v]]
               [(in-id->response id)
                (Integer/parseInt v)]))
       (into {})))

(defrecord FileBasedIO
    [request-file response-file request->out-id in-id->response]
    VerilatorIO
    (eval [this input-data]
      (try
        (doseq [c (conj (mapv
                         (fn [[op arg]]
                           (parse-file-based-request request->out-id op arg))
                         input-data)
                        (parse-file-based-request
                         {"Eval" (count request->out-id)}
                         "Eval"))]
          (spit request-file (str c "\n") :append true))
        (while (empty? (slurp response-file)))
        (parse-file-based-response in-id->response (slurp response-file))
        (finally (spit response-file "")))))

(defn file-based-io
  [params]
  (spit (:response-file params) "")
  (map->FileBasedIO params))

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
