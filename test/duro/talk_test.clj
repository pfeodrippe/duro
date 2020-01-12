(ns duro.talk-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.vcd]
   [duro.verilator :as verilator]

   [com.billpiel.sayid.core :as sd]
   [com.billpiel.sayid.trace :as tr]))

(comment

  (sd/ws-clear-log!)
  (-> @sd/workspace :traced tr/audit-traces)
  (sd/ws-add-trace-ns! duro.verilator)
  (sd/ws-enable-all-traces!)
  (sd/ws-deref!)

  ())

(deftest not-test
  (with-module module "nao.v"
    {:trace? true
     :trace-path "naso.vcd"}
    (let [{:keys [:top]} module]
      (duro.core/dump-values top 0)
      (duro.io/eval top {:nao.i/x 0})
      (duro.core/dump-values top 10)
      (duro.io/eval top {:nao.i/x 1})
      (duro.core/dump-values top 20)
      (duro.core/dump-values top 30))))
