(ns vv.parser
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr attr= text]]))

(defn- parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(defn module-interface
  [path]
  (let [parsed-xml (parse-str (slurp path))
        inputs (xml-> parsed-xml
                      :verilator_xml
                      :netlist
                      :module
                      :var
                      (attr= :vartype "logic")
                      (attr= :dir "input")
                      (attr :name))
        outputs (xml-> parsed-xml
                       :verilator_xml
                       :netlist
                       :module
                       :var
                       (attr= :vartype "logic")
                       (attr= :dir "output")
                       (attr :name))]
    {:inputs inputs
     :outputs outputs}))
