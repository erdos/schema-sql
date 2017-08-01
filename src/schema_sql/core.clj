(ns schema-sql.core
  (:require [schema.core :as sc :refer [defschema]]
            [camel-snake-kebab.core :refer [->snake_case]]))


;; https://www.cis.upenn.edu/~bcpierce/courses/629/jdkdocs/guide/jdbc/getstart/mapping.doc.html
(def scalar-type->sql
  {java.lang.String   "VARCHAR"
   java.math.BigDecimal "DECIMAL"
   java.lang.Long     "BIGINT"
   java.lang.Integer  "INTEGER"
   java.lang.Boolean  "BIT"
   java.lang.Byte     "TINYINT"
   java.lang.Float    "REAL"
   java.lang.Double   "DOUBLE"

   java.util.Date "DATE"

   java.sql.Date      "DATE"
   java.sql.Time      "TIME"
   java.sql.Timestamp "TIMESTAMP"})

(defn to_snake_case [s] (->snake_case s))

(defn table-name [k]
  (some-> k sc/schema-name name to_snake_case str))

(defn- key-name [k] (to_snake_case (name k)))

(defn- get-maybe
  "Returns value stored in maybe"
  [s] (when (instance? schema.core.Maybe s) (:schema s)))

(defn- get-enum
  "Returns set of items in an enum"
  [s] (when (instance? schema.core.EnumSchema s) (:vs s)))

;(sc/optional-key? (sc/optional-key {}))
;; (get-enum (sc/enum :A :B :C))

(defn get-nullable-scalar-keys [schema]
  (set (map key (filter (comp scalar-type->sql get-maybe val) schema))))

(defn get-scalar-keys [schema]
  (set (filter (comp scalar-type->sql schema) (filter keyword? (keys schema)))))

(declare create-schema-command)

(defn key-type? [x] (or (keyword? x) (symbol? x) (string? x)))

(defn- deep-merge [xs]
  (apply merge-with merge {} xs))
;; (deep-merge [{:a {:b 1}} {:a {:c 2}}])


(defn create-kvpair-command [schema-name
                             prefix-name
                             key-object
                             value-object]
  (cond

    ;(and (keyword? key-object) (sc/optional-key? key-object) (contains? scalar-type->sql (sc/explicit-schema-key)))

    ;; value is a maybe
    (and (key-type? key-object) (contains? scalar-type->sql (get-maybe value-object)))
    {schema-name {(str (some-> prefix-name (str "_")) (key-name key-object)) [(get-maybe value-object) :nullable]}}

    (and (key-type? key-object) (contains? scalar-type->sql value-object))
    {schema-name {(str (some-> prefix-name (str "_")) (key-name key-object)) [value-object]}}


    (and (key-type? key-object) (map? (get-maybe value-object)) (some? (table-name (get-maybe value-object))))
    (create-schema-command (table-name (get-maybe value-object)) nil (get-maybe value-object))

    ;; nevnelkuli subschema
    (and (key-type? key-object) (map? value-object) (nil? (table-name value-object)))
    (create-schema-command schema-name (str (some-> prefix-name (str "_")) (key-name key-object)) value-object)

    ;; nevesitett subschema
    (and (key-type? key-object) (map? value-object) (some? (sc/schema-name value-object)))
    (deep-merge [{schema-name {"id" [Long]}
                  (table-name value-object) {(str schema-name "_id") [Long]}}
                 (create-schema-command (table-name value-object) nil value-object)])


    ;; XXX: runs on tests but not nice solution
    (and (key-type? key-object) (vector? value-object) (= 1 (count value-object)) (some? (table-name (first value-object))))
    (deep-merge [{schema-name {"id" [Long]}
                  (table-name (first value-object)) {(str schema-name "_id") [Long]}}
                 (create-schema-command schema-name (key-name key-object) (first value-object))])


                                        ; nevnelkuli vektor subschema
    (and (key-type? key-object) (vector? value-object) (= 1 (count value-object)) (nil? (table-name value-object)))
    (create-schema-command schema-name (str (some-> prefix-name (str "_")) (key-name key-object)) value-object)

                                        ; nevesitett vektor
    (and (key-type? key-object) (vector? value-object) (= 1 (count value-object)) (some? (table-name value-object)))
    (create-schema-command (table-name value-object) nil value-object)



                                        ; :else (assert false (str "Unexpected " key-object value-object))
    ;:else {:$unexpected! {[schema-name prefix-name key-object] value-object}}
    ))

;(table-name MobyPolicy)

(defn create-schema-command
  ([schema] (create-schema-command (table-name schema) nil schema))
  ([schema-name prefix-name schema]
   (assert (string? schema-name) (str "Not string: " (pr-str schema-name) (type schema-name)))
   (cond

     (and (class? schema) (seq prefix-name))
     {schema-name {prefix-name schema}}

     ;; TODO: test this
     (and (class? schema) (empty? prefix-name))
     {schema-name {"$value" schema}}

     ;; ezzel ki kell vmit talalni
     (and (get-maybe schema) (empty? (table-name (get-maybe schema))))
     (create-schema-command (str schema-name "_" prefix-name) nil (get-maybe schema))

     ;; implicit vektor
     (and (vector? schema) (seq prefix-name) (empty? (table-name schema)))
     (create-schema-command (str schema-name "_" prefix-name) nil (first schema))

     ;; TODO: itt kitalalni, hogy hogy lesz osszekotve a ket tabla.
     (and (map? schema) (some? (sc/schema-name schema)))
     (deep-merge (for [[k v] schema]
                   (create-kvpair-command (table-name schema) nil k v)))

     (and (map? schema) (nil? (sc/schema-name schema)))
     (deep-merge (for [[k v] schema]
                   (create-kvpair-command schema-name prefix-name k v)))

     ;; for debug
     ;; :default (assert false "Unexpected schemma case. ")
     ;:else {:$unexpected {[schema-name prefix-name] schema}}
     )))

(defn schema-map->sql [table-name columns]
  (->> (for [[column-name [column-type flag]] columns]
         (str column-name " " (scalar-type->sql column-type)
              (when-not (#{:nullable} flag) " NOT NULL")))
       (clojure.string/join ",\n\t")
       (format "CREATE TABLE %s(\n\t%s\n);" table-name)))

(defn schema-command->sql [schema-command]
  (clojure.string/join
   "\n" (for [[k vs] schema-command]
          (schema-map->sql k vs))))


(defn schema->sql [schema] (-> schema create-schema-command schema-command->sql))
;; (table-name (str (sc/schema-name MobyPolicy)))

;; TODO: handle optional keys
;; TODO: handle any keys.
;; TODO: handle collections
;; TODO: handle related types (when given with var)  or submaps
;; TODO: handle recursive types (later)
;; TODO: handle schema extensions (:type, etc)

;(require '[fins.default-config :refer [MobyPolicy]])
;(clojure.pprint/pprint (schema->sql-map MobyPolicy))


;; TODO:
;; maybe, enum, eq, pred, conditional, (if), cond-pre, constrained

;; TODO: do not create docs for same schema twice

(comment

  (defschema Partner {:name sc/Str
                      :id sc/Num
                      :role sc/Str})

  (defschema DateInterval {:startDate java.util.Date,
                           :endDate (sc/maybe java.util.Date)})
  (defschema Policy {:term DateInterval
                     :ref sc/Str
                     :partners [Partner]
                     :active sc/Bool})

  (-> Policy schema->sql println)

  )
