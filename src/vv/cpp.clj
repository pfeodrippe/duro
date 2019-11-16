(ns vv.cpp
  (:require [clojure.string :as str]))

(defn- gen-top-member
  [k]
  (str "top->" (name k)))

(defn- gen-input
  [k]
  (str "input[" (name k) "]"))

(defn- gen-output
  [k]
  (str "output[" (name k) "]"))

(defn- gen-binding
  [v]
  (str (gen-top-member v) " = " (gen-output v) ";"))

(defn- gen-inputs
  [inputs]
  (->> inputs
       (mapv gen-binding)
       (cons "#define GENERATED_INPUTS")
       (str/join " \\\n")))

(defn- gen-outputs
  [outputs]
  (->> outputs
       (mapv gen-binding)
       (cons "#define GENERATED_OUTPUTS")
       (str/join " \\\n")))

(defn- gen-input-enum
  [inputs]
  (str "enum Input {\n"
       (->> inputs
            (str/join ",\n"))
       "\n}"))

(defn- gen-output-enum
  [outputs]
  (str "enum Output {\n"
       (->> outputs
            (str/join ",\n"))
       "\n}"))

(defn gen-header-string
  [{:keys [:inputs :outputs]}]
  (->> [(gen-inputs inputs)
        (gen-outputs outputs)
        (gen-input-enum inputs)
        (gen-output-enum outputs)]
       (str/join "\n\n")))
