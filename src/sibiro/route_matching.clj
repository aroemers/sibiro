(ns sibiro.route-matching
  (:require [sibiro.encoding :as encoding]))

(defn- map-keys [f m]
  (reduce-kv (fn [m k v] (assoc m (f k) v)) nil m))

(defn- match-uri-request* [routes parts params]
  (if-let [part (first parts)]
    (or (when-let [sub-routes (get routes part)]
          (match-uri-request* sub-routes (rest parts) params))
        (when-let [param-vec (first (filter vector? (keys routes)))]
          (let [sub-routes (get routes param-vec)]
            (match-uri-request* sub-routes (rest parts) (assoc params param-vec (encoding/url-decode part)))))
        (when-let [sub-routes (get routes :catch-all)]
          (match-uri-request* sub-routes nil (assoc params :catch-all (encoding/parts->uri parts)))))
    [routes params]))

(defn match-uri-request
  [{:keys [routes uri params] :as request}]
  (let [[routes route-params] (match-uri-request* routes (encoding/uri->parts uri) nil)
        parsed-params         (map-keys first route-params)]
    (cond-> (assoc request :routes routes)
      route-params (assoc :route-params-raw route-params
                          :route-params parsed-params
                          :params (merge params parsed-params)))))

(defn match-method-request [{:keys [routes request-method] :as request}]
  (assoc request :routes (or (get routes request-method) (get routes :any))))
