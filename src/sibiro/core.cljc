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

(defn- keyword-regex [string start]
  (let [re-start (.indexOf string "{")
        re-stop (.indexOf string "}")]
    (if (<= 0 re-start)
      [(keyword (subs string start re-start)) (re-pattern (subs string (inc re-start) re-stop))]
      [(keyword (subs string start)) nil])))

(defn- path-parts [path]
  (map (fn [p] (cond (.startsWith p ":") (keyword-regex p 1)
                     (.startsWith p "*") (keyword-regex p 0)
                     :otherwise          p))
       (str/split path #"/")))


;;; Internals for matching.

(defn- routes-tree [routes]
  (reduce (fn [result [method path handler :as route]]
            (let [parts     (path-parts path)
                  arguments (filter vector? parts)
                  keywords  (map first arguments)
                  regexes   (map second arguments)
                  in        (map #(cond (= (and (vector? %) (first %)) :*) :*
                                        (vector? %)                        :arg
                                        :otherwise                         %) parts)
                  handle    {:route-handler handler :route-params keywords
                             :regexes       regexes}]
              (if (seq in)
                (update-in result in assoc method handle)
                (assoc result method handle))))
          {} routes))

(defn- check-regexes [params regexes]
  (loop [params params
         regexes regexes]
    (if-let [param (first params)]
      (if-let [regex (first regexes)]
        (if (re-matches regex param)
          (recur (rest params) (rest regexes))
          false)
        (recur (rest params) (rest regexes)))
      true)))

(defn- match-uri* [tree parts params method]
  (if-let [part (first parts)]
    (lazy-seq
     (concat (when-let [subtree (get tree part)]
               (match-uri* subtree (rest parts) params method))
             (when-let [subtree (get tree :arg)]
               (match-uri* subtree (rest parts) (conj params part) method))
             (when-let [subtree (get tree :*)]
               (match-uri* subtree nil (conj params (apply str (interpose "/" parts))) method))))
    (lazy-seq
     (concat
      (when-let [result (get tree method)]
        (when (check-regexes params (:regexes result))
          [(update-in (dissoc result :regexes) [:route-params] zipmap (map url-decode params))]))
      (when-let [result (get tree :any)]
        (when (check-regexes params (:regexes result))
          [(update-in (dissoc result :regexes) [:route-params] zipmap (map url-decode params))]))))))


;;; Internals for uri creation.

(defn- query-string [data]
  (->> (for [[k v] data]
         (str (url-encode (name k)) "=" (url-encode (str v))))
       (interpose "&")
       (apply str "?")))

(defn- throw-unmatched [regex key value]
  (throw (ex-info (str "Parameter " key " with value '" value "' does not match " regex)
                  {:regex regex :key key :value value})))

#?(:clj
   ;; For Clojure, generate a function inlining as much knowlegde as possible.
   (defn- uri-for-fn-form [path]
     (let [parts  (path-parts path)
           keyset (set (map first (filter vector? parts)))
           data   (gensym)]
       (if (seq parts)
         `(fn [~data]
            (when-let [diff# (seq (reduce disj ~keyset (keys ~data)))]
              (throw (ex-info "Missing data for path." {:missing-keys diff#})))
            {:uri          (str ~@(->> (for [part parts
                                             :let [key (first part)]]
                                         (if (vector? part)
                                           (if-let [re (second part)]
                                             `(let [val# (get ~data ~key)]
                                                (if (re-matches ~re val#)
                                                  (#'sibiro.core/url-encode val#)
                                                  (#'sibiro.core/throw-unmatched ~re ~key val#)))
                                             `(#'sibiro.core/url-encode (get ~data ~key)))
                                           part))
                                       (interpose "/")))
             :query-string (when-let [keys# (seq (reduce disj (set (keys ~data)) ~keyset))]
                             (#'sibiro.core/query-string (select-keys ~data keys#)))})
         `(fn [_#]
            {:uri          "/"
             :query-string ""})))))

#?(:clj
   (defn- uri-for-fn [path]
     (eval (uri-for-fn-form path))))

#?(:cljs
   ;; For ClojureScript, an ordinary function because evaluation is
   ;; still somewhat hard (for me at least as an unexperienced
   ;; ClojureScript developer).
   (defn- uri-for-fn [path]
     (let [parts  (path-parts path)
           keyset (set (map first (filter vector? parts)))]
       (if (seq parts)
         (fn [data]
           (when-let [diff (seq (reduce disj keyset (keys data)))]
             (throw (ex-info "Missing data for path." {:missing-keys diff})))
           {:uri          (apply str (->> (for [part parts]
                                            (if (vector? part)
                                              (let [key (first part)
                                                    val (get data key)]
                                                (if-let [re (second part)]
                                                  (if (re-matches re val)
                                                    (url-encode val)
                                                    (throw-unmatched re key val))
                                                  (url-encode val)))
                                              part))
                                          (interpose "/")))
            :query-string (when-let [keys (seq (reduce disj (set (keys data)) keyset))]
                            (query-string (select-keys data keys)))})
         (fn [_]
           {:uri          "/"
            :query-string ""})))))

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
  {:route-handler handler, :route-params {...}, :alternatives (...)}
  for a match, or nil. For example:

  (match-uri (compile-routes [[:post \"/admin/user/:id\" :update-user]
                              [:post \"/admin/*\"        :admin-catch]])
             \"/admin/user/42\" :post)
  ;=> {:route-handler :update-user, :route-params {:id \"42\"}
       :alternatives ({:route-handler :admin-catch, :route-params {:* \"user/42\"}})}

  The values in :route-params are URL decoded for you.
  The :alternatives value is lazy, so it won't search for alternatives
  if you don't ask for it."
  [compiled uri request-method]
  (let [result (match-uri* (:tree compiled) (str/split uri #"/") [] request-method)]
    (when (seq result)
      (assoc (first result) :alternatives (rest result)))))

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
