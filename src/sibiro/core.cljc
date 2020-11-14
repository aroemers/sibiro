(ns sibiro.core
  "Core namespace for sibiro 2.0."
  (:require [clojure.string :as str]
            [sibiro.encoding :as encoding]))

(defn- map-vals [f m]
  (reduce-kv (fn [a k v] (assoc a k (f v))) {} m))

(defprotocol RouteMatcher
  (match-request [routes request])
  (find-routes [routes]))

(defprotocol KeyMatcher
  (match-key [key request])
  (find-key [key request]))

(extend-type clojure.lang.Var
  RouteMatcher
  (match-request [routes request]
    (match-request @routes request))
  (find-routes [routes]
    (find-routes @routes)))

(extend-type clojure.lang.PersistentVector
  RouteMatcher
  (match-request [routes request]
    (first (keep (fn [sub-routes]
                   (match-request sub-routes request))
                 routes)))
  (find-routes [routes]
    (apply merge (map find-routes routes)))

  KeyMatcher
  (match-key [key request]
    (when-let [value (second (str/split (:uri request) #"/"))]
      (-> request
          (update :path-params assoc (first key) (encoding/url-decode value))
          (update :uri subs (inc (count value))))))
  (find-key [key {:keys [uri path-params]}]
    {:uri         (str "/" (first key) uri)
     :path-params (assoc path-params (first key) true)}))

(extend-type clojure.lang.PersistentArrayMap
  RouteMatcher
  (match-request [routes request]
    (first (keep (fn [[matcher sub-routes]]
                   (when-let [updated-request (match-key matcher request)]
                     (match-request sub-routes updated-request)))
                 routes)))
  (find-routes [routes]
    (reduce-kv (fn [a k v]
                 (let [sub-routes (find-routes v)
                       prefixed   (map-vals #(find-key k %) sub-routes)]
                   (merge a prefixed)))
               {} routes)))

(extend-type clojure.lang.Fn
  RouteMatcher
  (match-request [routes {:keys [uri path-params middleware]}]
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
  (match-request [routes {:keys [uri path-params middleware]}]
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


(defn mk-handler
  "Create a request handler for the given routes structure. Can be a var."
  [routes]
  (fn [request]
    (if-let [{:keys [handler path-params middleware]} (match-request routes request)]
      (let [with-middleware (reduce #(%2 %1) handler middleware)]
        (with-middleware (assoc request :path-params path-params)))
      {:status 404 :body "not found"})))

(defn mk-reverser
  "Create a request maker for the given routes structure. Can be a var, but is not dynamic."
  [routes]
  (let [handlers (find-routes routes)]
    (fn reverser
      ([handler] (reverser handler nil))
      ([handler data]
       (when-let [{:keys [uri path-params]} (get handlers handler)]
         {:uri (reduce-kv (fn [uri param _]
                            (str/replace uri (str param) (encoding/url-encode (get data param))))
                          uri
                          path-params)
          :query-string (encoding/query-string (apply dissoc data (keys path-params)))})))))
