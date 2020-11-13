(ns sibiro.core
  "Core namespace for sibiro 2.0."
  (:require [clojure.string :as str]))

(defn- map-vals [f m]
  (reduce-kv (fn [a k v] (assoc a k (f v))) {} m))

(defprotocol RouteMatcher
  (match-routes [routes request])
  (find-routes [routes]))

(defprotocol KeyMatcher
  (match-key [key request])
  (find-key [key request]))

(extend-type clojure.lang.Var
  RouteMatcher
  (match-routes [routes request]
    (match-routes @routes request))
  (find-routes [routes]
    (find-routes @routes)))

(extend-type clojure.lang.PersistentVector
  RouteMatcher
  (match-routes [routes request]
    (first (keep (fn [sub-routes]
                   (match-routes sub-routes request))
                 routes)))
  (find-routes [routes]
    (apply merge (map find-routes routes)))

  KeyMatcher
  (match-key [key request]
    (when-let [value (second (str/split (:uri request) #"/"))]
      (-> request
          (update :path-params assoc (first key) value)
          (update :uri subs (inc (count value))))))
  (find-key [key request]
    {:uri         (str "/" (first key) (:uri request))
     :path-params (assoc (:path-params request) (first key) true)}))

(extend-type clojure.lang.PersistentArrayMap
  RouteMatcher
  (match-routes [routes request]
    (first (keep (fn [[matcher sub-routes]]
                   (when-let [updated-request (match-key matcher request)]
                     (match-routes sub-routes updated-request)))
                 routes)))
  (find-routes [routes]
    (reduce-kv (fn [a k v]
                 (let [sub-routes (find-routes v)
                       prefixed   (map-vals #(find-key k %) sub-routes)]
                   (merge a prefixed)))
               {} routes)))

(extend-type clojure.lang.Fn
  RouteMatcher
  (match-routes [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      {:path-params path-params
       :handler     routes
       :middleware  middleware}))
  (find-routes [routes]
    {routes {:uri         ""
             :path-params {}}})

  KeyMatcher
  (match-key [key request]
    (update request :middleware conj key))
  (find-key [key request]
    request))

(extend-type clojure.lang.Keyword
  RouteMatcher
  (match-routes [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      {:path-params path-params
       :handler     routes
       :middleware  middleware}))
  (find-routes [routes]
    {routes {:uri         ""
             :path-params {}}})

  KeyMatcher
  (match-key [key {:keys [request-method] :as request}]
    (when (or (= key request-method)
              (= key :any))
      request))
  (find-key [key request]
    request))

(extend-type java.lang.String
  KeyMatcher
  (match-key [key {:keys [uri] :as request}]
    (when (or (clojure.string/starts-with? uri (str "/" key "/"))
              (= uri (str "/" key)))
      (assoc request :uri (subs uri (inc (count key))))))
  (find-key [key request]
    (update request :uri #(str "/" key %))))


(defn handler
  "Create handler for the given routes structure. Can be a var."
  [routes]
  (fn [request]
    (if-let [{:keys [handler path-params middleware]} (match-routes routes request)]
      (let [with-middleware (reduce #(%2 %1) handler middleware)]
        (with-middleware (assoc request :path-params path-params)))
      {:status 404 :body "not found"})))
