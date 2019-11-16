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
  (str "input[input_" (name k) "]"))

(defn- gen-output
  [k]
  (str "output[output_" (name k) "]"))

(defn- gen-inputs
  [inputs]
  (->> inputs
       (mapv #(str (gen-top-member %) " = " (gen-input %) ";"))
       (cons "#define GENERATED_INPUTS")
       (str/join " \\\n")))

(defn- gen-outputs
  [outputs]
  (->> outputs
       (mapv #(str (gen-top-member %) " = " (gen-output %) ";"))
       (cons "#define GENERATED_OUTPUTS")
       (str/join " \\\n")))

(defn- gen-input-enum
  [inputs]
  (str "enum Input {\n"
       (->> (mapv #(str "input_" %) inputs)
            (str/join ",\n"))
       "\n};"))

(defn- gen-output-enum
  [outputs]
  (str "enum Output {\n"
       (->> (mapv #(str "output_" %) outputs)
            (str/join ",\n"))
       "\n};"))

(defn gen-header-string
  [{:keys [:inputs :outputs :module-name]}]
  (->> [(str "#include "  "\"V" module-name ".h\"")
        (gen-inputs inputs)
        (gen-outputs outputs)
        (gen-input-enum inputs)
        (gen-output-enum outputs)
        (str "#define TOP_CLASS " "V" module-name)]
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
                       (attr :name))
        module-name (first
                     (xml-> parsed-xml
                            :verilator_xml
                            :netlist
                            :module
                            (attr :name)))]
    {:inputs inputs
     :outputs outputs
     :module-name module-name}))

(defn- read-verilog-interface
  [mod-path]
  (let [dir (bean (fs/temp-dir "vv"))
        xml-path (str (:path dir) "/mod.xml")]
    (apply sh/sh
           ["verilator" "-Wno-STMTDLY"
            "--xml-output" xml-path
            "-Mdir" (:path dir)
            mod-path])
    (read-module-xml xml-path)))

(defn- rand-str [length]
  (->> (repeatedly #(char (+ (rand 26) 97)))
       (take length)
       (apply str)))

(defn gen-dynamic-lib
  [mod-path]
  (let [dir (bean (fs/temp-dir "vv"))
        interface (read-verilog-interface mod-path)
        header-str (gen-header-string interface)
        top-path (str (:path dir) "/top.cpp")
        lib-name (format "lib%s.dylib" (rand-str 5))
        lib-path (str (:path dir) "/" lib-name)]
    (fs/copy "template.cpp" top-path)
    (spit (str (:path dir) "/generated_template.h") header-str)
    ;; generate verilator files
    (apply sh/sh
           ["verilator" "-Wno-STMTDLY"
            "--cc" mod-path
            "-Mdir" (:path dir)
            "--exe" top-path])
    ;; make verilator
    (apply sh/sh
           ["make" "-j"
            "-C" (:path dir)
            "-f" (str "V" (:module-name interface) ".mk")
            (str "V" (:module-name interface))])
    ;; create dynamic lib
    (sh/with-sh-dir (:path dir)
      (apply sh/sh
             ["bash" "-c"
              (format "gcc -shared -o %s *.o -lstdc++" lib-name)]))
    {:interface interface
     :lib-path lib-path
     :lib-folder (:path dir)}))
