(ns sibiro.encoding
  "Encoding and decoding utilties."
  (:require [clojure.string :as str]))

(defn url-encode [string]
  (some-> string str
          (java.net.URLEncoder/encode "UTF-8")
          (.replace "+" "%20")))

(defn url-decode [string]
  (some-> string str
          (java.net.URLDecoder/decode "UTF-8")))

(defn query-string [data]
  (->> (for [[k v] data]
         (str (url-encode (name k)) "=" (url-encode (str v))))
       (str/join "&")))

(defn uri->parts [route]
  (rest (str/split route #"/")))

(defn parts->uri [parts]
  (str "/" (str/join "/" parts)))
