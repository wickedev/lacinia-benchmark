(ns core
  (:require [clojure.walk :as walk]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [superlifter.api :as api])
  (:import [com.google.common.base CaseFormat]))

(defn ->lacinia-promise [sl-result]
  (let [l-prom (resolve/resolve-promise)]
    (api/unwrap #(resolve/deliver! l-prom %) sl-result)
    l-prom))

(defmacro with-superlifter [ctx body]
  `(api/with-superlifter (get-in ~ctx [:superlifter])
     (->lacinia-promise ~body)))

(defn- kebab-case->camelCase [k]
  (keyword (.to CaseFormat/LOWER_HYPHEN
                CaseFormat/LOWER_CAMEL (name k))))

(def ^:private memoize-kebab-case->camelCase
  (memoize kebab-case->camelCase))

(defn kebab-case->camelCase-keys
  [m]
  (let [f (fn [[k v]]
            [(memoize-kebab-case->camelCase k) v])]
    ;; only apply to maps
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn ->entity
  [data entity-name]
  (->> (get data entity-name)
       (reduce #(assoc %1 (:id %2) %2) {})))

(defn ->reverse-entity
  [data entity-name field-name]
  (let [group-by-author (->> (get data entity-name)
                             (map (fn [root]
                                    (map (fn [field]
                                           [field root])
                                         (get root field-name))))
                             (apply concat)
                             (group-by first))]
    (-> group-by-author
        (update-vals (fn [books]
                       (map second books))))))
