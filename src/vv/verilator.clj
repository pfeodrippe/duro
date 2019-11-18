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

(defn- gen-top-local-member
  [module-name k]
  (str "top->" module-name "__DOT__" (name k)))

(defn- gen-input
  [k]
  (str "input[input_" (name k) "]"))

(defn- gen-output
  [k]
  (str "output[output_" (name k) "]"))

(defn- gen-local-signal
  [k]
  (str "local_signal[local_signal_" (name k) "]"))

(defn- gen-inputs
  [inputs]
  (->> inputs
       (mapv #(str (gen-top-member %) " = " (gen-input %) ";"))
       (cons "#define GENERATED_INPUTS")
       (str/join " \\\n")))

(defn- gen-outputs
  [outputs]
  (->> outputs
       (mapv #(str (gen-output %) " = " (gen-top-member %) ";"))
       (cons "#define GENERATED_OUTPUTS")
       (str/join " \\\n")))

(defn- gen-local-signal-inputs
  [module-name local-signals]
  (->> local-signals
       (mapv #(str (gen-top-local-member module-name %) " = " (gen-local-signal %) ";"))
       (cons "#define GENERATED_LOCAL_SIGNAL_INPUTS")
       (str/join " \\\n")))

(defn- gen-local-signal-outputs
  [module-name local-signals]
  (->> local-signals
       (mapv #(str (gen-local-signal %) " = " (gen-top-local-member module-name %) ";"))
       (cons "#define GENERATED_LOCAL_SIGNAL_OUTPUTS")
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

(defn- gen-local-signal-enum
  [local-signals]
  (str "enum LocalSignal {\n"
       (->> (mapv #(str "local_signal_" %) local-signals)
            (str/join ",\n"))
       "\n};"))

(defn gen-header-string
  [{:keys [:inputs :outputs :local-signals :module-name]}]
  (->> [(str "#include "  "\"V" module-name ".h\"")
        (str "#define TOP_CLASS " "V" module-name)
        (str "#define INPUT_SIZE " (count inputs))
        (str "#define OUTPUT_SIZE " (count outputs))
        (str "#define LOCAL_SIGNAL_SIZE " (count local-signals))
        (gen-inputs inputs)
        (gen-outputs outputs)
        (gen-local-signal-inputs module-name local-signals)
        (gen-local-signal-outputs module-name local-signals)
        (gen-input-enum inputs)
        (gen-output-enum outputs)
        (gen-local-signal-enum local-signals)]
       (str/join "\n\n")))

(defn- parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(defn read-module-xml
  [path]
  ;; TODO: refactor it
  (let [module-preds [:verilator_xml :netlist :module]
        input-attr-preds [:var (attr= :vartype "logic")
                          (attr= :dir "input") (attr :name)]
        output-attr-preds [:var (attr= :vartype "logic")
                           (attr= :dir "output") (attr :name)]
        local-signals-attr-preds [:var
                                  (fn [loc] (nil? (attr loc :dir)))
                                  (attr :name)]
        parsed-xml (parse-str (slurp path))
        module-name (or
                     (apply xml1-> parsed-xml
                            (concat
                             module-preds
                             [(attr= :topModule "1")
                              (attr :name)]))
                     (apply xml1-> parsed-xml
                            (concat
                             module-preds
                             [(attr :name)])))
        inputs (or (seq
                    (apply xml-> parsed-xml
                           (concat
                            module-preds
                            [(attr= :topModule "1")]
                            input-attr-preds)))
                   (apply xml-> parsed-xml
                          (concat
                           module-preds
                           input-attr-preds)))
        outputs (or (seq
                     (apply xml-> parsed-xml
                            (concat
                             module-preds
                             [(attr= :topModule "1")]
                             output-attr-preds)))
                    (apply xml-> parsed-xml
                           (concat
                            module-preds
                            output-attr-preds)))
        local-signals (or (seq
                           (apply xml-> parsed-xml
                                  (concat
                                   module-preds
                                   [(attr= :topModule "1")]
                                   local-signals-attr-preds)))
                          (apply xml-> parsed-xml
                                 (concat
                                  module-preds
                                  local-signals-attr-preds)))]
    {:inputs inputs
     :outputs outputs
     :local-signals local-signals
     :module-name module-name}))

(defn- build-verilator-args
  [{:keys [:module-dirs]}]
  (mapcat (fn [dir]
            ["-y" dir])
          module-dirs))

(defn- read-verilog-interface
  ([mod-path]
   (read-verilog-interface mod-path {}))
  ([mod-path options]
   (let [dir (bean (fs/temp-dir "vv"))
         xml-path (str (:path dir) "/mod.xml")]
     (println :xml-path xml-path)
     (println
      (apply sh/sh
             (concat
              ["verilator" "-Wno-STMTDLY"
               "--xml-output" xml-path
               "-Mdir" (:path dir)
               mod-path]
              (build-verilator-args options))))
     (read-module-xml xml-path))))

(defn- rand-str [length]
  (->> (repeatedly #(char (+ (rand 26) 97)))
       (take length)
       (apply str)))

(defn gen-dynamic-lib
  ([mod-path]
   (gen-dynamic-lib mod-path {}))
  ([mod-path {:keys [:mod-debug] :as options}]
   (let [dir (bean (fs/temp-dir "vv"))
         interface (read-verilog-interface mod-path options)
         header-str (gen-header-string interface)
         top-path (str (:path dir) "/top.cpp")
         lib-name (format "lib%s.dylib" (rand-str 5))
         lib-path (str (:path dir) "/" lib-name)]
     (fs/copy "template.cpp" top-path)
     (spit (str (:path dir) "/generated_template.h") header-str)
     ;; generate verilator files
     (println
      (apply sh/sh
             (concat
              ["verilator" "-Wno-STMTDLY"
               "--cc" mod-path
               "-Mdir" (:path dir)
               "--exe" top-path]
              (build-verilator-args options))))
     ;; make verilator
     (println
      (apply sh/sh
             ["make" "-j"
              "-C" (:path dir)
              "-f" (str "V" (:module-name interface) ".mk")
              (str "V" (:module-name interface))]))
     ;; create dynamic lib
     (println
      (sh/with-sh-dir (:path dir)
        (apply sh/sh
               ["bash" "-c"
                (format "gcc -shared -o %s *.o -lstdc++" lib-name)])))
     {:interface interface
      :lib-path lib-path
      :lib-folder (:path dir)})))

(def memo-gen-dynamic-lib (memoize gen-dynamic-lib))
