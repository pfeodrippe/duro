(ns vv.verilator
  (:require
   [clojure.zip :as zip]
   [clojure.xml :as xml]
   [clojure.data.zip.xml :refer [xml-> xml1-> attr attr= text]]
   [clojure.string :as str]
   [me.raynes.fs :as fs]
   [clojure.java.shell :as sh]))

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

(defn- parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(defn read-module-xml
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

(defn read-verilog-interface
  [mod-path]
  (let [dir (bean (fs/temp-dir "vv"))
        xml-path (str (:path dir) "/mod.xml")]
    (apply sh/sh
           ["verilator" "-Wno-STMTDLY"
            "--xml-output" xml-path
            "-Mdir" (:path dir)
            mod-path])
    (module-interface xml-path)))
