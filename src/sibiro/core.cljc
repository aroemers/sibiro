(ns sibiro.core
  "Core namespace for sibiro 2.0."
  (:require [clojure.string :as str]
            [sibiro.middleware :as middleware]
            [sibiro.tag-matching :as tag]))

(defn not-found-handler
  "Create a request handler that always returns a not-found response
  with the given body."
  [body]
  (constantly {:status 404 :headers {} :body body}))

(defn wrap-routing
  "Wrap the given handler by applying all middleware from the middleware
  namespace around it. This supports the default routes datastructure.
  Routes parameter must be derefable, e.g. a var."
  [handler routes]
  (-> handler
      (middleware/wrap-handle-route)
      (middleware/wrap-match-method)
      (middleware/wrap-assoc-tag)
      (middleware/wrap-match-uri)
      (middleware/wrap-routes routes)))

(defn tag-matcher
  "Creates a function that returns the URL for the given tagged route.
  Data that is not used for the path is added as a query string,
  unless with-query? is false (default is true). Routes parameter must
  be derefable, e.g. a var."
  [routes]
  (fn url-for-tag
    ([tag]
     (url-for-tag tag nil true))
    ([tag data]
     (url-for-tag tag data true))
    ([tag data with-query?]
     (let [tags (tag/find-tags-memoized @routes)]
       (tag/url-for-tag* tags tag data with-query?)))))
