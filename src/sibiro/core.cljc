(ns sibiro.core
  "Core namespace for sibiro 2.0."
  (:require [clojure.string :as str]
            [sibiro.encoding :as encoding]))

(defn- map-vals [f m]
  (reduce-kv (fn [a k v] (assoc a k (f v))) {} m))

(defrecord RouteMatch [handler path-params middleware])

(defrecord HandlerRequest [handler path-params uri])

(defprotocol RouteMatcher
  (find-match [routes ring-request]
    "Given a routes datastructure and a Ring request, return a
    RouteMatch record if it can be matched.")
  (find-handlers [routes]
    "Given a routes datastructure, return a map of all handlers to
    HandlerRequest records."))

(defprotocol KeyHandler
  (update-ring-request [key ring-request]
    "Given a map key and a Ring request, updates the request according to
    the type of the key.")
  (update-handler-request [key handler-request]
    "Given a map key and a HandlerRequest record, updates it according
    to the type of the key."))

(extend-type clojure.lang.IDeref
  RouteMatcher
  (find-match [routes ring-request]
    (find-match @routes ring-request))
  (find-handlers [routes]
    (find-handlers @routes))

  KeyHandler
  (update-ring-request [key ring-request]
    (update-ring-request @key ring-request))
  (update-handler-request [key handler-request]
    (update-handler-request @key handler-request)))

(extend-type clojure.lang.PersistentVector
  RouteMatcher
  (find-match [routes ring-request]
    (first (keep #(find-match % ring-request) routes)))
  (find-handlers [routes]
    (apply merge (map find-handlers routes)))

  KeyHandler
  (update-ring-request [[param-key param-coerce-fn] {:keys [uri] :as ring-request}]
    (when-let [value (second (str/split uri #"/"))]
      (let [coerced (cond-> (encoding/url-decode value) param-coerce-fn param-coerce-fn)]
        (-> ring-request
            (update :path-params assoc param-key coerced)
            (update :uri subs (inc (count value)))))))
  (update-handler-request [[param-key param-coerce-fn] {:keys [uri path-params] :as handler-request}]
    (assoc handler-request
           :uri         (str "/" param-key uri)
           :path-params (assoc path-params param-key param-coerce-fn))))

(extend-type clojure.lang.PersistentArrayMap
  RouteMatcher
  (find-match [routes ring-request]
    (first (keep (fn [[matcher sub-routes]]
                   (when-let [updated-request (update-ring-request matcher ring-request)]
                     (find-match sub-routes updated-request)))
                 routes)))
  (find-handlers [routes]
    (reduce-kv (fn [a k v]
                 (let [handler-reqs (find-handlers v)
                       updated      (map-vals #(update-handler-request k %) handler-reqs)]
                   (merge a updated)))
               {} routes)))

(extend-type clojure.lang.Fn
  RouteMatcher
  (find-match [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      (RouteMatch. routes path-params middleware)))
  (find-handlers [routes]
    {routes (HandlerRequest. routes {} "")})

  KeyHandler
  (update-ring-request [key ring-request]
    (update ring-request :middleware conj key))
  (update-handler-request [key handler-request]
    handler-request))

(extend-type clojure.lang.Keyword
  RouteMatcher
  (find-match [routes {:keys [uri path-params middleware]}]
    (when (or (= uri "") (= uri "/"))
      (RouteMatch. routes path-params middleware)))
  (find-handlers [routes]
    {routes (HandlerRequest. routes {} "")})

  KeyHandler
  (update-ring-request [key {:keys [request-method] :as ring-request}]
    (when (or (= key request-method)
              (= key :any))
      ring-request))
  (update-handler-request [key handler-request]
    handler-request))

(extend-type java.lang.String
  KeyHandler
  (update-ring-request [key {:keys [uri] :as ring-request}]
    (when (or (clojure.string/starts-with? uri (str "/" key "/"))
              (= uri (str "/" key)))
      (assoc ring-request :uri (subs uri (inc (count key))))))
  (update-handler-request [key handler-request]
    (update handler-request :uri #(str "/" key %))))


(defn mk-handler
  "Create a request handler for the given routes structure. Can be a var."
  [routes]
  (fn [request]
    (if-let [{:keys [handler path-params middleware]} (find-match routes request)]
      (let [with-middleware (reduce #(%2 %1) handler middleware)]
        (with-middleware (assoc request :path-params path-params)))
      {:status 404 :body "not found"})))

(defn mk-finder
  "Create a request maker for the given routes structure. Can be a var,
  but the available handlers are only calculated once."
  [routes]
  (let [handlers (find-handlers routes)]
    (fn finder
      ([handler] (finder handler nil))
      ([handler data]
       (when-let [{:keys [uri path-params]} (get handlers handler)]
         {:uri
          (reduce-kv (fn [uri param-key param-coerce-fn]
                       (if-let [value (some-> (get data param-key) str)]
                         (let [pattern (re-pattern (str "/" param-key "(/|$)"))]
                           (when param-coerce-fn ; test coercion
                             (param-coerce-fn value))
                           (str/replace uri pattern (str "/" (encoding/url-encode value) "$1")))
                         (throw (ex-info "missing URL parameter" {:param-key param-key}))))
                     uri
                     path-params)

          :query-string
          (encoding/query-string (apply dissoc data (keys path-params)))})))))
