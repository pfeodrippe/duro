(ns vv.io
  (:require [clojure.string :as str]))

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
        (doseq [c (vec (conj
                        (mapv
                         (fn [[op arg]]
                           (parse-file-based-request request->out-id op arg))
                         input-data)
                        (parse-file-based-request request->out-id :eval)))]
          (spit request-file (str c "\n") :append true))
        (while (empty? (slurp response-file)))
        (parse-file-based-response in-id->response (slurp response-file))
        (finally (spit response-file "")))))

(defn file-based-io
  [params]
  (spit (:response-file params) "")
  (map->FileBasedIO params))
