(ns githubbing.github.repos
  "GitHub APIs for repos endpoint.
  See https://docs.github.com/en/rest/overview/endpoints-available-for-github-apps#repos"
  (:require [githubbing.github.api :refer :all]
            [githubbing.config :refer [config]]))

(defn fetch-repos
  "Fetch from GitHub a collection of maps describing all the repos we can see in this org."
  ([] (fetch-repos (:organization config)))
  ([org]
   (get-json (github-url "orgs" org "repos"))))

(defn fetch-repo
  "Fetch from GitHub a map describing this repo, if we have read access."
  ([repo-slug] (fetch-repo (:organization config) repo-slug))
  ([org repo-slug]
   (get-json (github-url "repos" org repo-slug))))

(defn fetch-languages
  "Fetch the languages GitHub infers from this repo, in a language/bytes-of-code map"
  ([repo-slug] (fetch-languages (:organization config) repo-slug))
  ([org repo-slug]
   (get-json (github-url "repos" org repo-slug "languages"))))

(defn fetch-commits
  "Call GitHub to get the commits for this repo"
  ([repo-slug] (fetch-commits (:organization config) repo-slug))
  ([org repo-slug] (fetch-commits org repo-slug false))
  ([org repo-slug single-page?]
   (get-json (github-url "repos" org repo-slug "commits") {:single-page single-page?})))

(defn get-topics
  "Return an array of topic names associated with this repo"
  ([repo-slug] (get-topics (:organization config) repo-slug))
  ([org repo-slug]
   (get-json (github-url "repos" org repo-slug "topics"))))

(defn set-topics
  "Replace the set of topic names for this repo with these"
  ([repo-slug topics] (set-topics (:organization config) repo-slug topics))
  ([org repo-slug topics]
   (put-json (github-url "repos" org repo-slug "topics")
             (hash-map :names (vec (seq topics))))))


(defn get-folder-names
  "Lazily get the names of all files in the folder, and recursively below, identified by this GitHub URL.
   This must be lazy, since it makes REST calls for subfolders in a depth-first order"
  [url]
  (letfn [(f [lst]
            (lazy-seq
              (if (seq lst)
                (let [entry (first lst)
                      lst (rest lst)]
                  (if (= (:type entry) "dir")
                    (lazy-cat (get-folder-names (:url entry)) (f lst))
                    (cons (:name entry) (f lst)))))))]
    (f (get-json url))))

;; Non-lazy version, results in many more GitHub calls!
;(defn get-folder-names [url]
;  (mapcat (fn [entry]
;            (if (= (:type entry) "dir") (get-folder-names (:url entry)) (list (:name entry))))
;          (get-folder-contents url)))

(defn get-filenames
  "Get the file names for the files in this repo."
  ([org repo-slug] (get-filenames (fetch-repo org repo-slug)))
  ([m]
   (if (string? m)
     (get-filenames (:organization config) m)
     (get-folder-names (:contents_url m)))))

(defn add-languages
  "Add a :languages property to this repo"
  [r]
  (assoc r :languages (fetch-languages (get-in r [:owner :login]) (:name r))))
