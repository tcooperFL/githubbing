(ns githubbing.reports.sizing
  "Reporting on the size and branches of repositories"
  (:require [githubbing.reports :refer [create-report]]
            [githubbing.config :refer [config]]
            [githubbing.github.api :refer [get-json]]
            [githubbing.github.repos :as repos]
            [clojure.tools.logging :as log]
            [clojure.java.shell :as shell :refer [sh]]
            [clojure.java.io :as io]))

(defn psize
  "Return the total size of all files in a directory. Expects an existing File object"
  [f]
  (if (.isDirectory f)
    (apply + (pmap psize (.listFiles f)))
    (.length f)))

(defn add-branches
  "Augment the map with branches"
  [r]
  (assoc r :branches
           (get-json (:branches_url r)
                     {:failure-fn (constantly nil)})))      ; Return nil if no branches.

(defn add-repo-size
  "Augment the map with :clone-result and :size-on-disk"
  [r]
  (let [tmp-dir (io/as-file (System/getenv "TMPDIR"))
        repo-dir (io/file (System/getenv "TMPDIR") (:name r))
        result (shell/with-sh-dir tmp-dir (sh "git" "clone" (:ssh_url r)))
        size (psize repo-dir)]
    (log/infof "Cloning %s" (:name r))
    (shell/with-sh-dir tmp-dir (sh "rm" "-rf" (:name r)))
    (merge r {:clone-result result :size-on-disk size})))

(defn format-row
  "Given a GitHub repo map r, return a corresponding row in the report
  as a map with keys matching each column"
  [r]
  {:pre [(do (println "-> format-row" r) true)]}
  {:org        (get-in r [:owner :login])
   :repo       (:name r)
   :type       "GitHub"
   :size-in-mb (if (number? (:size-on-disk r))
                 (format "%.1f" (/ (:size-on-disk r) (* 1024 1024.0)))
                 "n/a")
   :branches   (count (:branches r))})

(defn prepare-data
  "Compute size-on-disk and fetch branch data, then format the row."
  [repos]
  ; Use a transducer function with into to efficiently run them through one at a time.
  (into []
        (comp (map add-repo-size)
              (map add-branches)
              (map format-row))
        repos))

;;; Report record

(def headings [["Organization" :org]
               ["Repository Name" :repo]
               ["Repository Type" :type]
               ["Size MB" :size-in-mb]
               ["Branches" :branches]])

(defn report [& [repos]]
  (create-report
    "Sizing report"
    headings
    (prepare-data (if (nil? repos) (repos/fetch-repos) repos))))
