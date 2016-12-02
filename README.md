![logo](doc/logo.png)

Bidirectional routing using datastructures for Clojure and ClojureScript.


## Motivation

The data-driven, bidirectional, macro-less routing libraries that I could find were too hard to use or bloated for my personal taste.
This library comes close to [clj-routing](https://github.com/mmcgrana/clj-routing), but has slightly different semantics.
Having a routing library based on datastructures and decomplecting route matching and request handling has several [benefits](EXAMPLE.md).

> This [blog post](http://www.functionalbytes.nl/clojure/sibiro/2016/03/14/sibiro.html) introduces sibiro and also explains why it was created.

Sibiro is:

* a **simple**, bidirectional routing library
* using **datastructures** for routes with [clout](https://github.com/weavejester/clout)-like URI patterns.
* for **Clojure** and **ClojureScript**
* that **compiles** to **fast matching** tree-like structures
* where the **order** of the routes **does not matter**
* returns **multiple matches lazily** using strict **precedence rules**
* **separates routing** and **handling**
* and provides basic **extras** to get you up to speed quickly
* plus **swagger** support via [sibiro-swagger](https://github.com/aroemers/sibiro-swagger) library.


## Usage ([API](https://aroemers.github.io/sibiro/))

Add `[functionalbytes/sibiro "0.1.4"]` to your dependencies and make sure you have Clojars as one of your repositories. This library requires Clojure(Script) 1.8 or higher.

### Defining routes

A routes structure is a collection of sequences, with three or four elements.
The request-method, a [clout](https://github.com/weavejester/clout)-like path, a handler (or any object really), and optionally a tag.
Note that the paths are not fully clout compatible, see [limitations](#limitations) section.
For example:

```clj
(def routes
  #{[:get  "/admin/user/"          user-list             ]
    [:get  "/admin/user/:id{\\d+}" user-get    :user-page]
    [:post "/admin/user/:id{\\d+}" user-update           ]
    [:any  "/:*"                   handle-404            ]})
```

> NOTE: In clout paths, a `*` can be used to define a catch-all path. In sibiro, this can also be `:*`. Inline regular expressions after the route parameter keywords are also supported.

**The order in which the routes are specified does not matter.**
Paths with more parts take precedence over shorter paths, exact URI parts take precedence over route parameters, catch-all parameter (`*`) is tried last, and specific request methods take precedence over `:any`.

To use the routes with the other API functions, they need to be "compiled" using `compile-routes`.
This transforms above datastructure into faster structures and functions.
For example:

```clj
(require '[sibiro.core :refer (compile-routes match-uri uri-for)])

(def compiled (compile-routes routes))
;=> #sibiro.core.CompiledRoutes@5728466
```

### Matching an URI

Given compiled routes, an URI and a request-method, the function `match-uri` returns a map with `:route-handler`, `:route-params` and a lazy `:alternatives`, or nil if no match was found.
Note that for a route to match, its inline regular expressions for route parameters need to match as well.
For example:

```clj
(match-uri compiled "/admin/user/42" :post)
;=> {:route-handler update-user,
     :route-params {:id "42"},
     :alternatives ({:route-handler handle-404, :route-params {:* "admin/user/42"}})}
```

The values in `:route-params` are URL decoded for you.
The value of `:alternatives` is lazy, so it won't search for alternatives if not requested.
Note that the URI parameter should not contain a query string.

### Generating an URI

Given compiled routes, a handler (or tag), and optionally parameters, the function `uri-for` returns a map with `:uri` and `:query-string`, or nil if the route could not be found.
For example.

```clj
(uri-for compiled update-user {:id 42 :name "alice bob"})
;=> {:uri "/admin/user/42", :query-string "?name=alice%20bob"}
```

An exception is thrown if parameters for the URI are missing in the data map, or are not matching the inline regular expressions.
The values in the data map are URL encoded for you.

> NOTE: There is also a convenience function called `path-for` that concatenates the :uri and :query-string from `uri-for`.

_That's all there is to the core of this library. Read on for some provided extras and possibilities._

## Provided extras for basic defaults

The `sibiro.extras` namespace contains some extra functions that can be used to get you up to speed quickly.
Note that these extras are Clojure only for now. Below are some of them.

##### `wrap-routes`

This middleware takes compiled and uncompiled routes as an argument.
On an incoming request, it merges the result of `match-uri` of the request with the request, and calls the wrapped handler.
This way, the wrapped handler receives both `:route-handler` and `:route-params`, if a match was found.

##### `wrap-try-alts`

This wraps the handler by merging in each entry from the `:alternatives` in the request one by one, until a non-nil response is given from the handler.
The first time the request is passed as is to the handler.

##### `route-handler`

A basic handler that uses the information from above `wrap-routes`.
It assumes the `:route-handler` value is a request handler, and calls that with the request.
If no `:route-handler` is available, a 404 response is returned.

##### `make-handler`

If all you need is the above basic `route-handler`, you can call `make-handler` with the (compiled or uncompiled) routes, and you get the `route-handler` wrapped with `wrap-try-alts` and `wrap-routes` back.
A voila, a basic request handler using sibiro.

_As always, have fun!_

## Limitations

As explained above, sibiro has the feature that the order of the routes does not matter.
This does imply two limitations on the clout-like paths of the routes:

1. Only one route parameter can be used between slashes

2. Dispatching on a route parameter regular expression does not work for route parameters that are in the "same position", i.e. prefixed by the same path.

For example, the following two routes structures do not work:

```clj
;; No support for multiple route parameters in one path part.
[[:get "/user/:id{\\d+}:fmt{(\\.json|\\.xml)}" :user]]

;; No support for dispatching on route parameters regular
;; expressions in the same position.
[[:get "/user/:id{\\d+}"   :user-by-id]
 [:get "/user/:name{\\.+}" :user-by-name]]

;; The following two routes are the same. The matched handler
;; will be undeterministic.
[[:get "/user/:id"   :user-by-id]
 [:get "/user/:name" :user-by-name]]
```

## Contributing

Patches and ideas welcome.

Master branch: [![Circle CI](https://circleci.com/gh/aroemers/sibiro/tree/master.svg?style=svg&circle-token=8a32ad3de9f753d371e8f464031f6f128d465469)](https://circleci.com/gh/aroemers/sibiro/tree/master)

## License

Copyright © 2016 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
