(ns duro.vcd)

(defn gen-section
  [sec body]
  [sec body "$end"])

(defn gen-date
  [body]
  (section "$date" [body]))

(defn gen-version
  [body]
  (section "$version" [body]))

(defn gen-comment
  [body]
  (section "$comment" [body]))

(defn gen-time-scale
  [body]
  (section "$timescale" [body]))

(defn gen-var
  [type bit-size id-code reference]
  (section
   "$var"
   [(name type) bit-size id-code (name reference)]))

(defn gen-scope
  [module-name body]
  ["$scope" "module" module-name "end"
   body
   "$upscope $end"])

(defn gen-definitios
  [body]
  [body "$enddefinitions"])
