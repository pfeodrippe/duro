(ns duro.vcd
  (:require [clojure.string :as str]))

(defn gen-section
  [sec body]
  [sec body "$end"])

(defn gen-date
  [body]
  (section "$date" [body]))

(defn gen-version
  [body]
  (section "$version" [body]))

(defn gen-comment
  [body]
  (section "$comment" [body]))

(defn gen-time-scale
  [body]
  (section "$timescale" [body]))

(defn gen-var
  [type bit-size id-code reference]
  (section
   "$var"
   [(name type) bit-size id-code (name reference)]))

(defn gen-scope
  [module-name body]
  ["$scope" "module" module-name "end"
   body
   "$upscope $end"])

(defn gen-definitios
  [body]
  [body "$enddefinitions"])

(comment

  (let [{:keys [:date :version :comment :time-scale]}
        {:date "abx"
         :version "123"
         :comment "Juju"
         :time-scale "1ps"}]
    (->> (concat
          (gen-date date)
          (gen-version version)
          (gen-comment comment)
          (gen-time-scale time-scale))
         (str/join "\n")
         print))
  []

  ())
