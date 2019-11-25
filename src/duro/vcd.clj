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
  (format "$var %s %d %c %s $end"
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
  [wire->id-code wire-values]
  (->> (mapcat (fn [[ts logic-values]]
                 [(str "#" ts)
                  (->> (mapv (fn [[k v]]
                               (str
                                "b" (Integer/toBinaryString v)
                                " " (wire->id-code k)))
                             logic-values)
                       (str/join "\n"))])
               wire-values)
       (str/join "\n")))

(comment

  (let [{:keys [:date :version :comment :time-scale :module-name]}
        {:date "abx"
         :version "123"
         :comment "Juju"
         :time-scale "1ps"
         :module-name "top"}

        header (->> (concat
                     (gen-date date)
                     (gen-version version)
                     (gen-comment comment)
                     (gen-time-scale time-scale)
                     (gen-scope
                      module-name
                      [(gen-var :wire 2 \s :data)
                       (gen-var :wire 1 \a :dado)]))
                    (str/join "\n")
                    (gen-definitions))

        timeline (gen-timeline {:data \s
                                :dado \a}
                               [[12 {:data 2r11
                                     :dado 1}]
                                [24 {:data 2r00
                                     :dado 0}]
                                [30 {:dado 1}]])]
    (spit "uba3.vcd" (str header timeline)))

  ())
