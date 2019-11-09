(ns vv.core
  (:require [clojure.string :as str]))

(spit "example.txt"
      "\njesus"
      :append true)

(defn command
  ([k]
   (command k 0))
  ([k v]
   (str
    ({:pc-write "3"
      :pc-next "0"
      :clk "1"
      :eval "2"}
     k)
    ":"
    v)))

#_(doseq [i (range 1)]
  (spit "jjj.txt"
        "0:2"))

(doseq [c [(command :clk 0)
           (command :pc-next 15)
           (command :pc-write 1)
           (command :clk 1)
           (command :eval)]]
  (clojure.pprint/pprint c)
  (spit "jjj.txt"
        (str c "\n")))
