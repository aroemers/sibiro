(ns sibiro.tag-matching
  "Finding tags and creating URLs."
  (:require [sibiro.encoding :as encoding]))

(defn uri-and-query [parts data]
  (loop [parts parts
         used  nil
         uri   nil]
    (if-let [part (first parts)]
      (cond (string? part)
            (recur (rest parts)
                   used
                   (str uri "/" part))

            (vector? part)
            (let [param-key  (first part)
                  param-data (or (get data param-key)
                                 (throw (ex-info "missing data for route param"
                                                 {:param param-key})))]
              (recur (rest parts)
                     (conj used param-key)
                     (str uri "/" (encoding/url-encode param-data))))

            :otherwise
            (recur (rest parts) used uri))
      (let [query (encoding/query-string (apply dissoc data used))]
        {:uri          uri
         :query-string (when (seq query) query)}))))

(defn url-for-tag* [tags tag data with-query?]
  (if-let [parts (get tags tag)]
    (let [{:keys [uri query-string]} (uri-and-query parts data)]
      (str uri (when (and with-query? (seq query-string))
                 (str "?" query-string))))
    (throw (ex-info "unknown tag" {:tag tag}))))

(defn find-tags
  ([routes]
   (find-tags routes []))
  ([routes path]
   (when (map? routes)
     (reduce-kv (fn [tags part sub-routes]
                  (merge tags (find-tags sub-routes (conj path part))))
                (when-let [tag (:tag routes)]
                  {tag path})
                routes))))

(def find-tags-memoized
  (memoize find-tags))
