(ns duro.vcd
  (:require [clojure.string :as str]))

(defn gen-section
  [sec body]
  [sec body "$end"])

(defn gen-date
  [body]
  (section "$date" body))

(defn gen-version
  [body]
  (section "$version" body))

(defn gen-comment
  [body]
  (section "$comment" body))

(defn gen-time-scale
  [body]
  (section "$timescale" body))

(defn gen-var
  [type bit-size id-code reference]
  (format "$var %s %d %d %s $end"
          (name type) bit-size id-code (name reference)))

(defn gen-scope
  [module-name body]
  (concat
   [(format "$scope module %s $end" module-name)]
   body
   ["$upscope $end"]))

(defn gen-definitions
  [body]
  (str body "\n$enddefinitions $end\n"))

(defn gen-timeline
  [wire-info wire-values]
  (->> (mapcat (fn [[ts logic-values]]
                 [(str "#" ts)
                  (->> (mapv (fn [[k v]]
                               (str
                                "b" (Integer/toBinaryString v)
                                " " (:id-code (wire-info k))))
                             logic-values)
                       (str/join "\n"))])
               wire-values)
       (str/join "\n")))

(defn create-vcd-file
  [file-path wire-values]
  (let [date (str (new java.util.Date))
        version "Duro dump."
        comment "Generated automatically from duro simulation."
        time-scale "1ps"
        module-name "top"
        wire-info {:data {:bit-size 2
                          :id-code \s}
                   :dado {:bit-size 1
                          :id-code \a}}
        wire-values [[12 {:data 2r11
                          :dado 1}]
                     [24 {:data 2r00
                          :dado 0}]
                     [30 {:dado 1}]]
        header (->> (concat
                     (gen-date date)
                     (gen-version version)
                     (gen-comment comment)
                     (gen-time-scale time-scale)
                     (gen-scope
                      module-name
                      (mapv (fn [[wire {:keys [:bit-size :id-code]}]]
                              (gen-var :wire bit-size id-code wire))
                            wire-info)))
                    (str/join "\n")
                    (gen-definitions))
        timeline (gen-timeline wire-info wire-values)]
    (spit file-path (str header timeline))))

(comment

  (let [date (str (new java.util.Date))
        version "Duro dump."
        comment "Generated automatically from duro simulation."
        time-scale "1ps"
        module-name "top"
        wire-info {:data {:bit-size 2
                          :id-code 22}
                   :dado {:bit-size 1
                          :id-code 21}}
        wire-values [[12 {:data 2r11
                          :dado 1}]
                     [24 {:data 2r00
                          :dado 0}]
                     [30 {:dado 1}]]
        header (->> (concat
                     (gen-date date)
                     (gen-version version)
                     (gen-comment comment)
                     (gen-time-scale time-scale)
                     (gen-scope
                      module-name
                      (mapv (fn [[wire {:keys [:bit-size :id-code]}]]
                              (gen-var :wire bit-size id-code wire))
                            wire-info)))
                    (str/join "\n")
                    (gen-definitions))
        timeline (gen-timeline wire-info wire-values)]
    (spit "uba3.vcd" (str header timeline)))

  ())
