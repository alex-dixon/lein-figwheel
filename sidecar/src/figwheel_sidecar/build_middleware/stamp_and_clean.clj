(ns figwheel-sidecar.build-middleware.stamp-and-clean
  (:require
   [figwheel-sidecar.utils :as utils]
   [clojure.java.io :as io]))

;; Minimal protection against corrupt builds

;; While minimal this will prevent plenty of headaches caused by
;; corrupted build :output-dir directories

;; This is a temporary solution until the ClojureScript compiler supports
;; something like a :signature key

(def user-dir-hash  (.hashCode (System/getProperty "user.dir")))

;; could hash all jar file names??
(def classpath-hash (.hashCode (System/getProperty "java.class.path")))

(defn stamp-file [build-config]
  (io/file (or (System/getProperty "user.dir")
               (io/file "."))
           (or (-> build-config :build-options :output-dir) "out")
           ".figwheel-compile-stamp"))

;; this provides a simple map and stable order for hashing
(defn options-that-affect-build-cache [build-config]
  (-> build-config
      (select-keys [:static-fns :optimize-constants
                    :elide-asserts :target])
      (assoc
       :build-id     (:id build-config)
       :source-paths (:source-paths build-config)
       :classpath    classpath-hash)))

(defn current-stamp-signature [build-config]
  (->> build-config
       options-that-affect-build-cache
       (into (sorted-map))
       pr-str
       (.hashCode)
       str))

(defn stamp! [build-config]
  #_(println "Stamping!")
  #_(println "Stamp file" (stamp-file build-config))
  #_(println "Stamp" (current-stamp-signature build-config))
  (let [sfile (stamp-file build-config)]
    (.mkdirs (.getParentFile sfile))
    (spit sfile
          (current-stamp-signature build-config))))

(defn stamp-value [build-state]
  (let [sf (stamp-file build-state)]
    (when (.exists sf) (slurp sf))))

(defn stamp-matches? [build-config]
  #_(println "Current Stamp " (current-stamp-signature build-config))
  #_(println "Previous Stamp" (stamp-value build-config))
  (= (current-stamp-signature build-config)
     (stamp-value build-config)))

#_(stamp! {:id "howw" :cow 3 :build-options {}})
#_(stamp-matches? {:id "howw" :cow 2 :build-options {}})

(defn hook [build-fn]
  (fn [{:keys [build-config] :as build-state}]
    (when-not (stamp-matches? build-config)
      (println "Figwheel: Cleaning build -" (:id build-config))
      (utils/clean-cljs-build* build-config))
    (stamp! build-config)
    (build-fn build-state)))
