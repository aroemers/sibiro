(ns sibiro.core
  "Core namespace for sibiro 2.0."
  (:require [clojure.string :as str]))

(defprotocol RouteMatcher
  (match-routes [routes request]))

(defprotocol KeyMatcher
  (match-key [key request]))


(extend-type clojure.lang.Var
  RouteMatcher
  (match-routes [routes request]
    (match-routes @routes request)))

(extend-type clojure.lang.PersistentVector
  RouteMatcher
  (match-routes [routes request]
    (first (keep (fn [sub-routes]
                   (match-routes sub-routes request))
                 routes))))

(extend-type clojure.lang.PersistentArrayMap
  RouteMatcher
  (match-routes [routes request]
    (first (keep (fn [[matcher sub-routes]]
                   (when-let [updated-request (match-key matcher request)]
                     (match-routes sub-routes updated-request)))
                 routes))))

(extend-type clojure.lang.Fn
  RouteMatcher
  (match-routes [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      {:path-params path-params
       :handler     routes
       :middleware  middleware}))

  KeyMatcher
  (match-key [key request]
    (update request :middleware conj key)))

(extend-type clojure.lang.Keyword
  RouteMatcher
  (match-routes [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      {:path-params path-params
       :handler     routes
       :middleware  middleware}))

  KeyMatcher
  (match-key [key {:keys [request-method] :as request}]
    (when (or (= key request-method)
              (= key :any))
      request)))

(extend-type java.lang.String
  KeyMatcher
  (match-key [key {:keys [uri] :as request}]
    (when (or (clojure.string/starts-with? uri (str "/" key "/"))
              (= uri (str "/" key)))
      (assoc request :uri (subs uri (inc (count key)))))))


(defn handler 
  "Create handler for the given routes structure. Can be a var."
  [routes]
  (fn [request]
    (if-let [{:keys [handler path-params middleware]} (match-routes routes request)]
      (let [with-middleware (reduce #(%2 %1) handler middleware)]
        (with-middleware request))
      {:status 400 :body "not found"})))
