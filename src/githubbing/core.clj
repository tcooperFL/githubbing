(ns githubbing.core
  (:require [githubbing.config :refer [config]]
            [githubbing.reports.classification :as classy]
            [githubbing.reports.sizing :as sizing]
            [githubbing.exports.csv :as csv]
            [githubbing.exports.xlsx :as xlsx]
            [githubbing.github.repos :as repos]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:gen-class))

(comment

  ;; Get repos (lazy)
  (def repos (repos/fetch-repos))

  ;; Load saved repos
  (def repos (repos/load-repos "<filename>"))

  ;; Save repos
  (repos/save-repos repos "<filename>")

  ;; Sample reports
  (def r1 (classy/report (take 3 repos)))
  (def r2 (classy/report repos))
  (def r3 (sizing/report (take 3 repos)))
  (def r4 (sizing/report repos))

  ;; Previewing
  (:data r1)
  (csv/preview r1)

  ;; Exporting
  (csv/export r1)

  ;; Excel
  (xlsx/export r1)
  )

;; Load/save repo collections

(defn save-repos
  "Dump the collection of repos into a single json formatted file."
  [repos fname]
  (spit fname (json/generate-string repos {:pretty true})))

(defn load-repos
  "Load a collection of repos from a json formatted file."
  [fname]
  (json/parse-stream (io/reader fname) true))

;; Updating topics to include classifications

(defn update-topics
  "Classify this repo and if the classification is not in its topics,
  make the REST call to GitHub to add that classification to its topics."
  [repo]
  {:pre [(map? repo)]}
  (if-let [classification (:classification repo)]
    (let [topics (set (:topics repo))]
      (if-not (contains? topics classification)
        (repos/set-topics (get-in repo [:owner :login])
                          (:name repo)
                          (conj topics classification))))))

(defn push-classification-to-topics
  "Update each of the repos to make sure each has its classification in its topics in GitHub."
  [repos]
  {:pre [(not (map? repos))]}
  (doseq [r repos] (update-topics r)))

;; Create a s

(defn download-repos
  "Create a shell script to download repos with names matching this RE into the given folder.
  If repos is a string, fetch all repos for that organization. Else repos
  should be a sequence of fetched repos."
  ([repos folder] (download-repos repos folder #".*"))
  ([repos folder re]
   (assert (= (type re) java.util.regex.Pattern) "Optional filter just be a regex matching repo names")
   (if (string? repos)
     (download-repos (repos/fetch-repos repos) folder re)
     (let [target-repos (filter #(re-matches re (:name %)) repos)
           out-dir (io/as-file folder)
           file-name (str "download_repos" ".sh")]

       (assert (.isDirectory out-dir) (format "? \"%s\" is not an existing folder\n" folder))
       (with-open [w (clojure.java.io/writer (str (.getAbsolutePath out-dir) "/" file-name) :encoding "UTF-8")]
         (doseq [g (map :ssh_url (sort-by :name target-repos))]
           (.write w (format "git clone %s\n" g))))

       (printf "Wrote git commands for %s out of %s repos provided.\n"
               (count target-repos) (count repos))
       (printf "Open a zsh term and execute\n\tcd %s\n\tsh ./%s |& tee download.log\n\tcloc -exclude-dir=.git . |& tee cloc.txt\n"
               (.getAbsolutePath out-dir) file-name)))))

(defn -main
  [& _]
  (println " This is a workbench, to be run inside the REPL, not a standalone program. "))
