(ns sibiro.core
  "Simple data-driven request routing for Clojure and ClojureScript."
  (:require [clojure.string :as str]))

;;; Shared helpers.

(defn- url-encode [string]
  (some-> string str
          #?(:clj (java.net.URLEncoder/encode "UTF-8")
             :cljs (js/encodeURIComponent))
          (.replace "+" "%20")))

(defn- url-decode [string]
  (some-> string str
          #?(:clj (java.net.URLDecoder/decode "UTF-8")
             :cljs (js/decodeURIComponent))))

(defn- path-parts [path]
  (map (fn [p] (cond (.startsWith p ":") (keyword (subs p 1))
                     (= p "*")           :*
                     :otherwise          p))
       (str/split path #"/")))


;;; Internals for matching.

(defn- routes-tree [routes]
  (reduce (fn [result [method path handler]]
            (let [parts (path-parts path)]
              (assoc-in result (concat parts [[method]]) handler)))
          {}
          routes))

(defn- match-uri* [tree parts params method]
  (if-let [part (first parts)]
    (or (when-let [subtree (get tree part)]
          (match-uri* subtree (rest parts) params method))
        (some (fn [keyw]
                (when-not (= keyw :*)
                  (match-uri* (get tree keyw) (rest parts) (assoc params keyw part) method)))
              (filter keyword? (keys tree)))
        (when-let [subtree (get tree :*)]
          (match-uri* subtree nil (assoc params :* (apply str (interpose "/" parts))) method)))
    (when-let [handler (or (get tree [method]) (get tree [:any]))]
      {:route-handler handler
       :route-params (zipmap (keys params) (map url-decode (vals params)))})))


;;; Internals for uri creation.

(defn- query-string [data]
  (->> (for [[k v] data]
         (str (url-encode (name k)) "=" (url-encode (str v))))
       (interpose "&")
       (apply str "?")))

#?(:clj
   ;; For Clojure, generate a function inlining as much knowlegde as possible.
   (defn- uri-for-fn-form [path]
     (let [parts  (path-parts path)
           keyset (set (filter keyword? parts))
           data   (gensym)]
       `(fn [~data]
          (when-let [diff# (seq (reduce disj ~keyset (keys ~data)))]
            (throw (ex-info "Missing data for path." {:missing-keys diff#})))
          {:uri          (str ~@(->> (for [part parts]
                                       (if (keyword? part)
                                         `(#'sibiro.core/url-encode (get ~data ~part))
                                         part))
                                     (interpose "/")))
           :query-string (when-let [keys# (seq (reduce disj (set (keys ~data)) ~keyset))]
                           (#'sibiro.core/query-string (select-keys ~data keys#)))}))))

#?(:clj
   (defn- uri-for-fn [path]
     (eval (uri-for-fn-form path))))

#?(:cljs
   ;; For ClojureScript, an ordinary function because evaluation is
   ;; still somewhat hard (for me at least as an unexperienced
   ;; ClojureScript developer).
   (defn- uri-for-fn [path]
     (let [parts  (path-parts path)
           keyset (set (filter keyword? parts))]
       (fn [data]
         (when-let [diff (seq (reduce disj keyset (keys data)))]
           (throw (ex-info "Missing data for path." {:missing-keys diff})))
         {:uri          (apply str (->> (for [part parts]
                                          (if (keyword? part)
                                            (url-encode (get data part))
                                            part))
                                        (interpose "/")))
          :query-string (when-let [keys (seq (reduce disj (set (keys data)) keyset))]
                          (query-string (select-keys data keys)))}))))

(defn- routes-tags [routes opts]
  (reduce (fn [result [_ path handler tag]]
            (let [uff        (uri-for-fn path)
                  ufhandler? (not (:uri-for-tagged-only? opts))]
              (cond-> result
                tag        (assoc tag uff)
                ufhandler? (assoc handler uff))))
          {} routes))


;;; Routes record

(defrecord CompiledRoutes [tree tags])

#?(:clj
   (defmethod print-method CompiledRoutes [v ^java.io.Writer w]
     (.write w (str v))))

#?(:clj
   (do (alter-meta! #'map->CompiledRoutes assoc :no-doc true)
       (alter-meta! #'->CompiledRoutes assoc :no-doc true)))


;;; Public API

(defn ^:no-doc compiled? [routes]
  (instance? CompiledRoutes routes))

(defn compile-routes
  "Compiles a routes datastructure for use in `match-uri` and
  `uri-for`. Routes is a sequence of sequences (e.g. a vector of
  vectors) containing 3 or 4 elements: a method keyword (or :any), a
  clout-like path, a result object (can be a handler), and optionally
  a tag. For example:

  [[:get  \"/admin/user/\" user-list]
   [:get  \"/admin/user/:id\" user-get :user-page]
   [:post \"/admin/user/:id\" user-update]
   [:any  \"/:*\" handle-404]]

  The order in which the routes are specified does not matter. Longer
  routes always take precedence, exact uri parts take precedence over
  route parameters, catch-all (:*) is tried last, and specific request
  methods take precedence over :any.

  Compiling takes some optional keyword arguments:

   :uri-for-tagged-only? - When set to true, only tagged routes are
     compiled for use with `uri-for` and can only be found by their
     tag. Defaults to false.

  The routes are compiled into a tree structure, for fast matching.
  Functions for creating URIs (`uri-for`) are also precompiled for
  every route."
  [routes & {:as opts}]
  (map->CompiledRoutes {:tree (routes-tree routes)
                        :tags (routes-tags routes opts)}))

(defn match-uri
  "Given compiled routes, an URI and a request-method, returns
  {:route-handler handler, :route-params {...} for a match, or nil.
  For example:

  (match-uri (compile-routes [[:post \"/admin/user/:id\" :update-user]])
             \"/admin/user/42\" :post)
  ;=> {:route-handler :update-user, :route-params {:id \"42\"}

  The values in :route-params are URL decoded for you."
  [compiled uri request-method]
  (match-uri* (:tree compiled) (str/split uri #"/") {} request-method))

(defn uri-for
  "Given compiled routes and a handler (or tag), and optionally
  parameters, returns {:uri \"...\", :query-string \"?...\"}. For
  example:

  (uri-for (compile-routes [[:post \"/admin/user/:id\" :update-user]])
           :update-user {:id 42 :name \"alice\"})
  ;=> {:uri \"/admin/user/42\", :query-string \"?name=alice\"}

  An exception is thrown if parameters for the URI are missing in the
  data map. The values in the data map are URL encoded for you."
  {:arglists '([compiled handler] [compiled handler params]
               [compiled tag] [compiled tag params])}
  ([compiled obj]
   (uri-for compiled obj nil))
  ([compiled obj data]
   (when-let [f (get (:tags compiled) obj)]
     (f data))))

(defn path-for
  "Convenience method concatenating :uri and :query-string from
  `uri-for`."
  {:arglists '([compiled handler] [compiled handler params]
               [compiled tag] [compiled tag params])}
  ([compiled obj]
   (path-for compiled obj nil))
  ([compiled obj data]
   (when-let [{:keys [uri query-string]} (uri-for compiled obj data)]
     (str uri query-string))))
