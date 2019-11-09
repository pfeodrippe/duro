(ns vv.core
  (:require [clojure.string :as str]))

(spit "example.txt"
      "\njesus"
      :append true)

(doseq [i (range 1)]
  (spit "jjj.txt"
        (str/join (range 3))))
