(ns githubbing.reports
  "Core functions for reporting namespaces to ensure consistency")

(defrecord Report [name headings data])

(defn- create-validator
  "Given a collection of keys, return a function that ensures all
   those keys are in a given map"
  [ks]
  #_{:pre [(do (println "-> create-validator" ks) true)]}
  (fn [m] (every? (set (keys m)) ks)))

(defn create-report
  "Create a report. All reports have name, headings, and data
   where headings is a sequence of pairs [<title> <key>]
     and data is a map with at least those keys."
  [name headings data]
  {:pre [(every? (create-validator (map second headings)) data)]}
  (Report. name headings data))

