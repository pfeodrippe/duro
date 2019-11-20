(ns vv.verilator
  (:require
   [clojure.zip :as zip]
   [clojure.xml :as xml]
   [clojure.data.zip.xml :refer [xml-> xml1-> attr attr= tag= text]]
   [clojure.string :as str]
   [medley.core :as medley]
   [me.raynes.fs :as fs]
   [clojure.java.shell :as sh]))

(defn- gen-top-member
  [k]
  (str "top->" (name k)))

(defn- gen-local-reference
  [module-name sig]
  (-> (str (name module-name) "__DOT__" (name sig))
      (str/replace #"\." "__DOT__")))

(defn- gen-top-local-member
  [module-name sig]
  (-> (str "top->" (gen-local-reference module-name sig))
      (str/replace #"\." "__DOT__")))

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

(defn- gen-local-signal-cases
  [interfaces f]
  (concat
   ["switch (sig) {"]
   (concat
    (map-indexed
     (fn [i [n signals]]
       (->> (map-indexed (f i n) (apply concat (vals signals)))
            (str/join " \\\n")))
     (medley/remove-vals :top-module? interfaces)))
   ["}"]))

(defn gen-local-signal-cases-inputs
  [interfaces verilator-top-header]
  (->> (gen-local-signal-cases
        interfaces
        (fn [i n]
          (fn [j sig]
            (if (str/includes? verilator-top-header
                               (str (gen-local-reference n sig) ","))
              (->> [(str "case " (+ (bit-shift-left i 16) j) ":")
                    (str (gen-top-local-member n sig) " = arg;")
                    "break;"]
                   (str/join " \\\n"))
              ""))))
       (cons "#define GENERATED_SUBMODULE_SIGNAL_INPUTS")
       (str/join " \\\n")))

(defn gen-local-signal-cases-outputs
  [interfaces verilator-top-header]
  (->> (gen-local-signal-cases
        interfaces
        (fn [i n]
          (fn [j sig]
            ;; Checks if the signal exists at generated header file
            ;; by verilator.
            ;; e.g. `VL_SIG8(system__DOT__thecpu__DOT__doalu__DOT__k,0,0);`
            ;; NOTE: the `,` is important.
            (if (str/includes? verilator-top-header
                               (str (gen-local-reference n sig) ","))
              (->> [(str "case " (+ (bit-shift-left i 16) j) ":")
                    (str "return " (gen-top-local-member n sig) ";")]
                   (str/join " \\\n"))
              ""))))
       (cons "#define GENERATED_SUBMODULE_SIGNAL_OUTPUTS")
       (str/join " \\\n")))

(defn gen-top-header-string
  [interfaces {:keys [:mod-debug?]}]
  (->> (medley/filter-vals :top-module? interfaces)
       (mapv
        (fn [[module {:keys [:inputs :outputs :local-signals]}]]
          (->> (concat
                [(str "#include "  "\"V" (name module) ".h\"")
                 (str "#define TOP_CLASS " "V" (name module))
                 (str "#define INPUT_SIZE " (count inputs))
                 (str "#define OUTPUT_SIZE " (count outputs))
                 (str "#define LOCAL_SIGNAL_SIZE " (count local-signals))
                 "#define GENERATED_LOCAL_SIGNAL_INPUTS 0;"
                 "#define GENERATED_LOCAL_SIGNAL_OUTPUTS 0;"
                 "#define GENERATED_SUBMODULE_SIGNAL_INPUTS 0;"
                 "#define GENERATED_SUBMODULE_SIGNAL_OUTPUTS return 0;"
                 (gen-inputs inputs)
                 (gen-outputs outputs)
                 (gen-input-enum inputs)
                 (gen-output-enum outputs)]
                (when mod-debug?
                  [(gen-local-signal-inputs (name module) local-signals)
                   (gen-local-signal-outputs (name module) local-signals)
                   (gen-local-signal-enum local-signals)]))
               (str/join "\n\n"))))
       first))

(defn gen-submodules-header-string
  [interfaces verilator-top-header]
  (->> (medley/remove-vals :top-module? interfaces)
       (mapv
        (fn [[module {:keys [:inputs :outputs :local-signals]}]]
          (->> [(gen-local-signal-cases-inputs interfaces verilator-top-header)
                (gen-local-signal-cases-outputs interfaces verilator-top-header)]
               (str/join "\n\n"))))
       (str/join "\n")))

(defn- parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(defn extract-module-signals
  [zipper module-name]
  (let [module-preds [:verilator_xml :netlist :module]
        input-attr-preds [:var (attr= :vartype "logic")
                          (attr= :dir "input") (attr :name)]
        output-attr-preds [:var (attr= :vartype "logic")
                           (attr= :dir "output") (attr :name)]
        local-signals-attr-preds [:var
                                  (fn [loc]
                                    (and (nil? (attr loc :dir))
                                         (not (empty? (attr loc :vartype)))))
                                  (attr :name)]
        inputs (apply xml-> zipper
                      (concat
                       module-preds
                       [(attr= :name module-name)]
                       input-attr-preds))
        outputs (apply xml-> zipper
                       (concat
                        module-preds
                        [(attr= :name module-name)]
                        output-attr-preds))
        local-signals (apply xml-> zipper
                             (concat
                              module-preds
                              [(attr= :name module-name)]
                              local-signals-attr-preds))]
    {:inputs inputs
     :outputs outputs
     :local-signals local-signals}))

(defn- get-top-module-name
  [interfaces]
  (name (ffirst (medley/filter-vals :top-module? interfaces))))

(defn read-module-xml
  [path]
  ;; TODO: refactor it
  (let [zipper (parse-str (slurp path))
        name->hier (reduce (fn [acc i]
                             (let [name (apply xml->
                                               (concat
                                                [zipper
                                                 :verilator_xml
                                                 :cells
                                                 :cell]
                                                (conj (vec (repeat i zip/next))
                                                      (attr :submodname))))
                                   hier (apply xml->
                                               (concat
                                                [zipper
                                                 :verilator_xml
                                                 :cells
                                                 :cell]
                                                (conj (vec (repeat i zip/next))
                                                      (attr :hier))))]
                               (if (seq name)
                                 (conj acc [(keyword (first name))
                                            (keyword (first hier))])
                                 (reduced acc))))
                           []
                           (range))
        type-table (->> (xml-> zipper
                               :verilator_xml
                               :netlist
                               :typetable
                               :basicdtype
                               (juxt (attr :fl)
                                     (attr :id)
                                     (attr :name)
                                     (attr :left)
                                     (attr :right)))
                        (partition 5)
                        (mapv #(zipmap [:fl :id :name :left :right] %))
                        (group-by :id)
                        (medley/map-vals first))]
    (->> name->hier
         (map-indexed (fn [i [n hier]]
                        (if (zero? i)   ; top module?
                          [hier (-> (extract-module-signals zipper (name n))
                                    (assoc :top-module? true))]
                          [hier (extract-module-signals zipper (name n))])))
         (into {}))))

#_(read-module-xml
   "/var/folders/xl/0gx4mcfd1qv1wvcxvcntqzfw0000gn/T/vv1574259829767-3231516971/mod.xml")

#_
(def xxx (read-verilog-interface
          "zipcpu/rtl/zipsystem.v"
          {:module-dirs ["zipcpu/rtl" "zipcpu/rtl/core"
                         "zipcpu/rtl/peripherals" "zipcpu/rtl/ex"]
           :mod-debug? true}))

#_(gen-local-signal-cases-inputs
   (read-verilog-interface
    "ALU32Bit.v"
    {:mod-debug? true}))

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
  ([mod-path {:keys [:mod-debug?] :as options}]
   (let [dir (bean (fs/temp-dir "vv"))
         interfaces (read-verilog-interface mod-path options)
         top-path (str (:path dir) "/top.cpp")
         lib-name (format "lib%s.dylib" (rand-str 5))
         lib-path (str (:path dir) "/" lib-name)
         _ (fs/copy "template.cpp" top-path)
         ;; generate verilator files
         _ (println
            (apply sh/sh
                   (concat
                    ["verilator" "-Wno-STMTDLY"
                     "--cc" mod-path
                     "-Mdir" (:path dir)
                     "--exe" top-path]
                    (build-verilator-args options)
                    (when mod-debug? ["--public-flat-rw"]))))
         header-str (cond-> (gen-top-header-string interfaces options)
                      mod-debug?
                      (str "\n\n"
                           (gen-submodules-header-string
                            interfaces
                            (slurp
                             (str (:path dir)
                                  "/V" (get-top-module-name interfaces) ".h")))))]
     (spit (str (:path dir) "/generated_template.h") header-str)
     ;; make verilator
     (println
        (apply sh/sh
               ["make" "-j"
                "-C" (:path dir)
                "-f" (str "V" (get-top-module-name interfaces) ".mk")
                (str "V" (get-top-module-name interfaces))]))
     ;; create dynamic lib
     (println
      (sh/with-sh-dir (:path dir)
        (apply sh/sh
               ["bash" "-c"
                (format "gcc -shared -o %s *.o -lstdc++" lib-name)])))
     {:interfaces interfaces
      :top-interface (->> (vals interfaces)
                          (filter :top-module?)
                          first)
      :lib-path lib-path
      :lib-folder (:path dir)})))

(def memo-gen-dynamic-lib (memoize gen-dynamic-lib))
