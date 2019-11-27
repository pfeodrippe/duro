(ns duro.vcd
  (:require
   [clojure.string :as str]
   [duro.io]))

(defn- gen-section
  [sec body]
  [sec body "$end"])

(defn- gen-date
  [body]
  (gen-section "$date" body))

(defn- gen-version
  [body]
  (gen-section "$version" body))

(defn- gen-comment
  [body]
  (gen-section "$comment" body))

(defn- gen-time-scale
  [body]
  (gen-section "$timescale" body))

(defn- gen-var
  [type bit-size id reference]
  (format "$var %s %d %d %s $end"
          (name type) bit-size id (name reference)))

(defn- gen-scope
  [module-name body]
  (concat
   [(format "$scope module %s $end" module-name)]
   body
   ["$upscope $end"]))

(defn- gen-definitions
  [body]
  (str body "\n$enddefinitions $end\n"))

(defn- gen-timeline
  [wire-info wire-values]
  (->> (mapcat (fn [[ts logic-values]]
                 [(str "#" ts)
                  (->> (mapv (fn [[k v]]
                               (str
                                "b" (Integer/toBinaryString v)
                                " " (:id (wire-info k))))
                             logic-values)
                       (str/join "\n"))])
               wire-values)
       (str/join "\n")))

(defn create-vcd-file
  [file-path wire-info wire-values]
  (let [date (str (new java.util.Date))
        version "Duro dump."
        comment "Generated automatically from duro simulation."
        time-scale "1ps"
        module-name "top"
        header (->> (concat
                     (gen-date date)
                     (gen-version version)
                     (gen-comment comment)
                     (gen-time-scale time-scale)
                     (gen-scope
                      module-name
                      (mapv (fn [[wire {:keys [:bit-size :id]}]]
                              (gen-var :wire bit-size id wire))
                            wire-info)))
                    (str/join "\n")
                    (gen-definitions))
        timeline (gen-timeline wire-info wire-values)]
    (spit file-path (str header timeline))))

(defn build-dump-fn
  [top]
  (let [wire-values (atom [])
        dump-fn #(swap! wire-values conj
                        [% (->> (keys (:wires top))
                                (mapv (fn [wire]
                                        [wire (duro.io/get-local-signal
                                               top wire)]))
                                (into {}))])]
    {:wire-values wire-values
     :dump-fn dump-fn}))
