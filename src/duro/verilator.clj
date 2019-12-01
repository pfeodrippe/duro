(ns duro.verilator
  (:require
   [clojure.zip :as zip]
   [clojure.xml :as xml]
   [clojure.data.zip.xml :refer [xml-> xml1-> attr attr= tag= text]]
   [clojure.string :as str]
   [medley.core :as medley]
   [me.raynes.fs :as fs]
   [clojure.java.shell :as sh]
   [clojure.java.io :as io]))

(defn- gen-top-member
  [k]
  (str "top->" (name k)))

(defn- gen-local-reference
  [module-name sig]
  (-> (str (if (seq (name module-name))
             (str (name module-name) "__DOT__")
             "")
           (name (:name sig)))
      (str/replace #"\." "__DOT__")))

(defn- gen-top-local-member
  [module-name sig]
  (str "top->" (gen-local-reference module-name sig)))

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
       (mapv #(str (gen-top-member (:name %)) " = " (gen-input (:name %)) ";"))
       (cons "#define GENERATED_INPUTS")
       (str/join " \\\n")))

(defn- gen-outputs
  [outputs]
  (->> outputs
       (mapv #(str (gen-output (:name %)) " = " (gen-top-member (:name %)) ";"))
       (cons "#define GENERATED_OUTPUTS")
       (str/join " \\\n")))

(defn- gen-input-enum
  [inputs]
  (str "enum Input {\n"
       (->> (mapv #(str "input_" (:name %)) inputs)
            (str/join ",\n"))
       "\n};"))

(defn- gen-output-enum
  [outputs]
  (str "enum Output {\n"
       (->> (mapv #(str "output_" (:name %)) outputs)
            (str/join ",\n"))
       "\n};"))

(defn- wrap-in-switch
  [coll]
  (concat
   ["switch (sig) {"]
   coll
   ["}"]))

(defn- gen-local-signal-cases
  [interfaces f]
  (concat
   ["switch (sig) {"]
   (mapv
    (fn [[n {:keys [:index] :as signals}]]
      (->> (apply concat ((juxt :inputs :outputs :local-signals) signals))
           (map-indexed (f index n))
           (remove empty?)
           (str/join " \\\n")))
    interfaces)
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
       (cons "#define GENERATED_LOCAL_SIGNAL_INPUTS")
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
       (cons "#define GENERATED_LOCAL_SIGNAL_OUTPUTS")
       (str/join " \\\n")))

(defn gen-independent-signal-cases
  [independent-signals]
  (->>
   [(->> (map-indexed
          (fn [i [sig {:keys [:representation :id]}]]
            (->> [(str "case " id ":")
                  (str (gen-top-local-member "" {:name representation}) " = arg;")
                  "break;"]
                 (str/join " \\\n")))
          independent-signals)
         wrap-in-switch
         (cons "#define GENERATED_INDEPENDENT_SIGNAL_INPUTS")
         (str/join " \\\n"))
    (->> (map-indexed
          (fn [i [sig {:keys [:representation :id]}]]
            (->> [(str "case " id ":")
                  (str "return " (gen-top-local-member "" {:name representation}) ";")
                  "break;"]
                 (str/join " \\\n")))
          independent-signals)
         wrap-in-switch
         (cons "#define GENERATED_INDEPENDENT_SIGNAL_OUTPUTS")
         (str/join " \\\n"))]
   (str/join " \n\n")))

(defn gen-array-signal-cases-inputs
  [interfaces verilator-top-header]
  (->> (gen-local-signal-cases
        interfaces
        (fn [i n]
          (fn [j sig]
            (if (str/includes? verilator-top-header
                               (str (gen-local-reference n sig) "["))
              (->> [(str "case " (+ (bit-shift-left i 16) j) ":")
                    (str (gen-top-local-member n sig) "[idx] = arg;")
                    "break;"]
                   (str/join " \\\n"))
              ""))))
       (cons "#define GENERATED_ARRAY_SIGNAL_INPUTS")
       (str/join " \\\n")))

(defn gen-array-signal-cases-outputs
  [interfaces verilator-top-header]
  (->> (gen-local-signal-cases
        interfaces
        (fn [i n]
          (fn [j sig]
            (if (str/includes? verilator-top-header
                               (str (gen-local-reference n sig) "["))
              (->> [(str "case " (+ (bit-shift-left i 16) j) ":")
                    (str "return " (gen-top-local-member n sig) "[idx];")]
                   (str/join " \\\n"))
              ""))))
       (cons "#define GENERATED_ARRAY_SIGNAL_OUTPUTS")
       (str/join " \\\n")))

(defn gen-top-header-string
  [interfaces]
  (->> (medley/filter-vals :top-module? interfaces)
       (mapv
        (fn [[module {:keys [:inputs :outputs]}]]
          (->> (concat
                [(str "#include "  "\"V" (name module) ".h\"")
                 (str "#define TOP_CLASS " "V" (name module))
                 (str "#define INPUT_SIZE " (count inputs))
                 (str "#define OUTPUT_SIZE " (count outputs))
                 ;; defaults
                 "#define GENERATED_LOCAL_SIGNAL_INPUTS 0;"
                 "#define GENERATED_LOCAL_SIGNAL_OUTPUTS 0;"
                 "#define GENERATED_ARRAY_SIGNAL_INPUTS 0;"
                 "#define GENERATED_ARRAY_SIGNAL_OUTPUTS 0;"
                 "#define GENERATED_INDEPENDENT_SIGNAL_INPUTS 0;"
                 "#define GENERATED_INDEPENDENT_SIGNAL_OUTPUTS 0;"
                 ;; gen inputs and outputs
                 (gen-inputs inputs)
                 (gen-outputs outputs)
                 (gen-input-enum inputs)
                 (gen-output-enum outputs)])
               (str/join "\n\n"))))
       first))

(defn gen-submodules-header-string
  [interfaces verilator-top-header independent-signals]
  (->> (concat
        [(gen-local-signal-cases-inputs interfaces verilator-top-header)
         (gen-local-signal-cases-outputs interfaces verilator-top-header)
         (gen-array-signal-cases-inputs interfaces verilator-top-header)
         (gen-array-signal-cases-outputs interfaces verilator-top-header)]
        [(gen-independent-signal-cases independent-signals)])
       (str/join "\n\n")))

(defn- parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(defn extract-module-signals
  [zipper module-name type-table]
  (let [module-preds [:verilator_xml :netlist :module]
        input-attr-preds [:var (attr= :vartype "logic")
                          (attr= :dir "input")
                          (juxt (attr :name) (attr :dtype_id))]
        output-attr-preds [:var (attr= :vartype "logic")
                           (attr= :dir "output")
                           (juxt (attr :name) (attr :dtype_id))]
        local-signals-attr-preds [:var
                                  (fn [loc] (nil? (attr loc :dir)))
                                  (juxt (attr :name) (attr :dtype_id))]
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
                              local-signals-attr-preds))
        zip #(->> (partition 2 %)
                  (mapv (fn [[name dtype-id]]
                          {:name name
                           :type (type-table dtype-id)})))]
    {:inputs (zip inputs)
     :outputs (zip outputs)
     :local-signals (zip local-signals)}))

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
        basic-table (->> (xml-> zipper
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
                         (mapv #(zipmap [:type :fl :id :name :left :right]
                                        (cons :basicdtype %)))
                         (group-by :id)
                         (medley/map-vals first))
        array-table (->> (xml-> zipper
                                :verilator_xml
                                :netlist
                                :typetable
                                :unpackarraydtype
                                (juxt (attr :fl)
                                      (attr :id)
                                      (attr :sub_dtype_id)
                                      #(xml-> (zip/next %)
                                              :range
                                              :const
                                              (attr :name))))
                         (partition 4)
                         (mapv #(zipmap [:type :fl :id :sub_dtype_id :range]
                                        (cons :unpackarraydtype %)))
                         (mapv
                          (fn [m]
                            (-> m
                                (update :range
                                        (fn [[lo hi]]
                                          (let [;; 32'hxxxx
                                                [_ vlo] (str/split lo #"'")
                                                ;; 32'shxxxx
                                                [_ vhi] (str/split hi #"'")]
                                            [(Integer/parseInt (subs vlo 1) 16)
                                             (Integer/parseInt (subs vhi 2) 16)]))))))
                         (group-by :id)
                         (medley/map-vals first)
                         (medley/map-vals
                          #(merge % (select-keys (basic-table (:sub_dtype_id %))
                                                 [:left :right]))))
        type-table (merge basic-table array-table)]
    (->> name->hier
         (map-indexed (fn [i [n hier]]
                        (if (zero? i)   ; top module?
                          [hier (-> (extract-module-signals zipper (name n)
                                                            type-table)
                                    (assoc :top-module? true
                                           :index i))]
                          [hier (-> (extract-module-signals zipper (name n)
                                                            type-table)
                                    (assoc :index i))])))
         (into {}))))

(defn- build-verilator-args
  [{:keys [:module-dirs]}]
  (mapcat (fn [dir]
            ["-y" dir])
          module-dirs))

(defn- read-verilog-interface
  ([mod-path]
   (read-verilog-interface mod-path {}))
  ([mod-path options]
   (let [dir (bean (fs/temp-dir "duro"))
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
  ([mod-path {:keys [:mod-debug? :independent-signals] :as options}]
   (let [dir {:path (str (System/getProperty "user.home")
                         "/.duro-simulation/"
                         mod-path)}
         _ (fs/mkdirs (:path dir))
         interfaces (read-verilog-interface mod-path options)
         independent-signals (->> independent-signals
                                  (map-indexed
                                   (fn [i [k params]]
                                     [k (assoc params :id
                                               (+ (bit-shift-left
                                                   (count interfaces)
                                                   16)
                                                  i))]))
                                  (into {}))
         top-path (str (:path dir) "/top.cpp")
         lib-name (format "lib%s.dylib" (rand-str 5))
         lib-path (str (:path dir) "/" lib-name)
         _ (fs/copy (io/resource "duro/template/verilator-top-module.cpp")
                    top-path)
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
         header-str (cond-> (gen-top-header-string interfaces)
                      mod-debug?
                      (str "\n\n"
                           (gen-submodules-header-string
                            interfaces
                            (slurp
                             (str (:path dir)
                                  "/V" (get-top-module-name interfaces) ".h"))
                            independent-signals)))]
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
      :top-module-name (get-top-module-name interfaces)
      :top-interface (->> (vals interfaces)
                          (filter :top-module?)
                          first)
      :independent-signals independent-signals
      :lib-path lib-path
      :lib-folder (:path dir)})))
