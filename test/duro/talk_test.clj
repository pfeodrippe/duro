(ns duro.talk-test
  (:require
   [clojure.test :refer [testing is are deftest]]
   [duro.core :as core :refer [with-module]]
   [duro.io]
   [duro.vcd]
   [duro.verilator :as verilator]))

(deftest not-test
  (with-module module "nao.v"
    {}
    (let [{:keys [:top]} module]
      (duro.io/eval top {:nao.i/x 1}))))
