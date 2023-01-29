(ns user
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [core :as core :refer [->entity ->reverse-entity
                                   kebab-case->camelCase-keys with-superlifter]]
            [criterium.core :as c]
            [superlifter.api :as api]))

(def data (-> (io/resource "data.edn")
              slurp
              edn/read-string))

(def authors-map (->entity data :authors))
(def books-by-author (->reverse-entity data :books :authors))

(api/def-superfetcher FetchAuthors [ids]
  (fn [many _env]
    (->> (map :ids many)
         (map #(map authors-map %))
         kebab-case->camelCase-keys)))

(api/def-superfetcher FetchBooks [user-id]
  (fn [many _env]
    (->> (map :user-id many)
         (map #(get books-by-author %))
         kebab-case->camelCase-keys)))

(defn- resolve-book-authors
  [ctx _args book]
  (with-superlifter
    ctx
    (api/enqueue! :Book/authors (->FetchAuthors (:authors book)))))

(defn- resolve-author-books
  [ctx _args author]
  (with-superlifter
    ctx
    (api/enqueue! :Author/books (->FetchBooks (:id author)))))

(defn- resolve-books
  [_context _args _value]
  (:books data))


(defn load-schema []
  (-> "schema/schema.graphqls"
      io/resource
      slurp
      parse-schema
      (util/inject-resolvers
       {:Query/books resolve-books
        :Book/authors resolve-book-authors
        :Author/books resolve-author-books})
      schema/compile))


(defn execute-sample-query [schema ctx]
  (lacinia/execute
   schema
   "query {
      books {
        ...BookFragment
      }
    }

    fragment BookFragment on Book {
      id
      title
      subject
      published
      authors {
       ...AuthorFragment
      }
    }

    fragment AuthorFragment on Author {
      id
      firstName
      lastName
      from
      until
      books {
        ...AuthorBookFragment
      }
    }

    fragment AuthorBookFragment on Book {
      id
      title
      subject
      published
    }" nil ctx))

(def schema (load-schema))

(comment
  (def superlifter
    (api/start!
     {:buckets {:Book/authors
                {:triggers
                 {:queue-size {:threshold 10}
                  :interval {:interval 100}}}
                :Author/books
                {:triggers
                 {:queue-size {:threshold 10}
                  :interval {:interval 100}}}}
      :urania-opts {:env {:db nil}}}))

  (c/with-progress-reporting
    (c/bench (execute-sample-query schema {:superlifter superlifter})))

  (execute-sample-query schema {:superlifter superlifter})

  (api/stop! superlifter))