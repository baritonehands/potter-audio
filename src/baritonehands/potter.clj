(ns baritonehands.potter
  (:require [clj-http.client :as http]
            [hickory.core :as html]
            [hickory.select :as sel]
            [cemerick.url :refer [url url-decode]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class)
  (:import (java.net URL)))

(def roots
  ["https://potteraudio.com/stephen-fry-hp-sorcerer-stone/"
   "https://potteraudio.com/stephen-fry-chamber-of-secret/"
   "https://potteraudio.com/stephen-fry-prisoner-azkaban-aud/"
   "https://potteraudio.com/stephen-fry-fire/"
   "https://potteraudio.com/stephen-fry-order-of-ph/"
   "https://potteraudio.com/stephen-fry-hp-half-blood-prince/"
   "https://potteraudio.com/stephen-fry-deathly-audio/"])

(defn page [root page]
  (if (> page 1)
    (str root page "/")
    root))

(defn download-page [url]
  (let [{:keys [trace-redirects body]} (http/get url)]
    (if (seq trace-redirects)
      (throw (RuntimeException. "Last page redirected"))
      (-> body html/parse html/as-hickory))))

(defn audio-sources [dom]
  (->> dom
       (sel/select (sel/child (sel/tag :audio) (sel/tag :source)))
       (mapv (comp url :src :attrs))))

(defn download-audio [{:keys [path] :as url} download-dir]
  (let [segments (str/split path #"/")
        dir (url-decode (last (butlast segments)))
        name (url-decode (last segments))]
    (println "Downloading" (str dir "/" name))
    (io/make-parents download-dir dir name)
    (with-open [output (io/output-stream (io/file download-dir dir name))]
      (-> (http/get (str url) {:as :stream})
          :body
          (io/copy output)))))

(defn download-all [root download-dir]
  (loop [cur-page 1]
    (when-let [sources (try
                         (-> (page root cur-page)
                             (download-page)
                             (audio-sources)
                             (seq))
                         (catch Exception _ nil))]
      (doseq [src sources]
        (download-audio src download-dir))
      (recur (inc cur-page)))))

(defn str->book [book]
  (dec (Integer/parseInt book)))

(defn -main
  [& args]
  (let [[books dir] (if (= (count args) 2)
                      [[(str->book (first args))] (second args)]
                      [(range 0 7) (first args)])]
    (doseq [book books]
      (download-all (roots book) dir))))
