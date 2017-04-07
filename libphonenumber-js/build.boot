(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[cljsjs/boot-cljsjs "0.5.2" :scope "test"]])

(require '[cljsjs.boot-cljsjs.packaging :refer :all]
         '[boot.tmpdir :as tmpd]
         '[clojure.java.io :as io]
         '[boot.util :refer [sh info]])

(def +lib-version+ "0.4.5")
(def +version+ (str +lib-version+ "-0"))

(def pkg-dir "libphonenumber-js-master")

(task-options!
 push {:repo "deploy-clojars"}
  pom {:project     'xerpa/cljsjs-libphonenumber-js
       :version     +version+
       :description "A simpler (and smaller) rewrite of Google Android's popular libphonenumber library"
       :url         "https://halt-hammerzeit.github.io/libphonenumber-js/"
       :scm         {:url "https://github.com/halt-hammerzeit/libphonenumber-js"}})

(deftask build-libphonenumber-js []
  (let [tmp (tmp-dir!)]
    (with-pre-wrap
      fileset
      (doseq [f (->> fileset input-files)
              :let [target (io/file tmp (tmpd/path f))]]
        (io/make-parents target)
        (io/copy (tmpd/file f) target))
      (binding [boot.util/*sh-dir* (str (io/file tmp pkg-dir))]
        (do
          ((sh "npm" "install"))
          ((sh "node_modules/.bin/babel-node" "runnable/generate" "../PhoneNumberMetadata.xml" "BR" "extended"))
          ((sh "npm" "run" "browser-build"))
          ((sh "cp" "bundle/libphonenumber-js.min.js" "bundle/libphonenumber-js.js"))
          ))
      (-> fileset (add-resource tmp) commit!))))

(deftask package []
  (comp
    (download :url "https://codeload.github.com/halt-hammerzeit/libphonenumber-js/zip/master"
              :checksum "B133E7E96D45D0BEB63274B524B2BE43"
              :unzip true)
    (build-libphonenumber-js)
    (sift :move {#"^libphonenumber-js-master/bundle/libphonenumber-js.min.js" "cljsjs/libphonenumber-js/common/libphonenumber-js.min.inc.js"
                 #"^libphonenumber-js-master/bundle/libphonenumber-js.js" "cljsjs/libphonenumber-js/common/libphonenumber-js.inc.js"
                 #"^libphonenumber-js-master/bundle/libphonenumber-js.min.js.map" "cljsjs/libphonenumber-js/common/libphonenumber-js.min.js.inc.map"})
    (sift :include #{#"^cljsjs"})
    (deps-cljs :name "xerpa.cljsjs-libphonenumber-js")
    (pom)
    (jar)))

(deftask release []
  (merge-env!
    :repositories [["deploy-clojars" {:url "https://clojars.org/repo"
                                      :username (System/getenv "CLOJARS_USER")
                                      :password (System/getenv "CLOJARS_PASS")}]])
  (comp
    (package)
    (install)
    (sift :include [#".*\.jar"])
    (push)))
