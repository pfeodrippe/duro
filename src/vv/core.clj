(ns vv.core
  (:require [clojure.string :as str]))

(spit "example.txt"
      "\njesus"
      :append true)

(defn command
  ([k]
   (command k 0))
  ([k v]
   (let [cmd (str
              ({:pc-write "3"
                :pc-next "0"
                :clk "1"
                :eval "2"}
               k)
              ":"
              v)]
     (str cmd
          (str/join
           (repeat (- 32 (count cmd)) " "))))))

#_(doseq [i (range 1)]
  (spit "jjj.txt"
        "0:2"))

(time
 (doseq [i (range 9421)]
   (doseq [c [(command :clk 0)
              (command :eval)
              (command :pc-next i)
              (command :pc-write 1)
              (command :clk 1)
              (command :eval)]]
     (spit "caramba.txt"
           (str c "\n")
           :append true))))
