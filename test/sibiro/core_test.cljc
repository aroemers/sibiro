(ns sibiro.core-test
  (:require [sibiro.core :refer (compile-routes match-uri uri-for)]
            #?@(:clj  [[clojure.test :refer (deftest testing is)]]
                :cljs [[cljs.test :refer-macros (deftest testing is)]
                       [doo.runner :refer-macros [doo-tests]]])))

(def routes (compile-routes
             [[:get  "/simple"            :simple]
              [:any  "/simple"            :simple-any]
              [:post "/post"              :only-post]
              [:get  "/simple/:arg"       :simple-arg]
              [:get  "/simple/:*"         :simple-catch]
              [:get  "/regex/:arg{\\d+}"  :regex]
              [:get  "/other/*"           :other-catch]
              [:get  ":*"                 :catch-all]]))

(defn match-single-uri [routes uri verb]
  (dissoc (match-uri routes uri verb) :alternatives))

(deftest match-uri-test
  (is (= (-> (match-uri routes "/simple" :get) :route-handler) :simple)
      "Match a simple plain route and specific request method takes precedence over :any.")

  (is (= (-> (match-uri routes "/simple" :post) :route-handler) :simple-any)
      "Match an :any route.")

  (is (= (match-single-uri routes "/post" :put) nil)
      "Don't match a different request method.")

  (is (= (match-single-uri routes "/simple/42" :get)
         {:route-handler :simple-arg
          :route-params {:arg "42"}})
      "Match with an argument.")

  (is (= (match-single-uri routes "/simple/space%20test" :get)
         {:route-handler :simple-arg
          :route-params {:arg "space test"}})
      "Arguments are URL decoded.")

  (is (= (match-single-uri routes "/simple/foo/bar" :get)
         {:route-handler :simple-catch
          :route-params {:* "foo/bar"}})
      "Match a more specific catch-all.")

  (is (= (match-single-uri routes "/foo/bar" :get)
         {:route-handler :catch-all
          :route-params {:* "/foo/bar"}})
      "Match a more generic catch-all.")

  (is (= (match-single-uri routes "/other/foo/bar" :get)
         {:route-handler :other-catch
          :route-params {:* "foo/bar"}})
      "Match a keyword-less catch-all, a la clout")

  (is (= (match-single-uri routes "/regex/42" :get)
         {:route-handler :regex
          :route-params {:arg "42"}})
      "Match a regex argument.")

  (is (= (match-single-uri routes "/regex/fail" :get)
         {:route-handler :catch-all
          :route-params {:* "/regex/fail"}})
      "Don't match a failing regex argument.")

  (let [match (match-uri routes "/simple/42" :get)]
    (is (not (realized? (:alternatives match))) "Alternatives is lazy")
    (is (= (:alternatives match)
           [{:route-handler :simple-catch
             :route-params {:* "42"}}
            {:route-handler :catch-all
             :route-params {:* "/simple/42"}}])
        "Alternatives found")))

(deftest uri-for-test
  (is (= (-> (uri-for routes :simple) :uri) "/simple")
      "Create a simple plain URI.")

  (is (= (-> (uri-for routes :simple-arg {:arg 42}))
         {:uri "/simple/42"
          :query-string nil})
      "Create a simple parameterized URI.")

  (is (= (-> (uri-for routes :simple-arg {:arg 42 :extra "space test"}))
         {:uri "/simple/42"
          :query-string "?extra=space%20test"})
      "Extra arguments end up in the query string and are URL endoced.")

  (is (try (not (uri-for routes :simple-arg))
           (catch #?(:clj Throwable :cljs :default) t true))
      "Missing arguments throws an exception.")

  (is (try (not (uri-for routes :regex {:arg "foobar"}))
           (catch #?(:clj Throwable :cljs :default) t true))
      "Illegal arguments throws an exception."))

#?(:cljs (doo-tests 'sibiro.core-test))
