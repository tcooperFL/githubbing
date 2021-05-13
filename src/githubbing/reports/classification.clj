(ns githubbing.reports.classification
  "Create a classification report"
  (:require [githubbing.reports :refer :all]
            [githubbing.config :refer [config]]
            [githubbing.github.api :refer :all]
            [githubbing.github.repos :as repos]
            [clojure.string :as s]))

;; Utilities

(defn to-date [iso8601]
  (java.time.LocalDateTime/parse
    iso8601
    (java.time.format.DateTimeFormatter/ISO_DATE_TIME)))

(defn to-local-date-string [iso8601]
  (str (.toLocalDate (to-date iso8601))))

(def today (java.time.LocalDateTime/now))

(defn days-between [dt1 dt2]
  (.toDays (java.time.Duration/between dt1 dt2)))


;; Classifying

; If we see any of these in repo descriptions, we assume it is a product repo.
(def product-names (set (:products config)))

(defn contains-string
  "Case insensitive RE matching any one of the strings arg 2-n"
  [s & subs]
  (and (not (nil? s))
       (some #(re-find % s) (map re-pattern (map #(str "(?i)" %) subs)))))

(def scripting-languages
  "Those languages that are primarily for scripting infrastructure only"
  (map keyword
       ["Shell" "PowerShell" "Awk" "Jupyter Notebook" "Batchfile" "Gherkin"
        "Rich Text Format" "Makefile" "CMake"]))

; Heuristic rules to classify a repo, in priority order.
(def rules
  "Classification rules"
  [
   {:name           "Documentation"
    :description    "name or topics indicate this is documentation "
    :test-fn        #(contains-string (:name %) "^docs")
    :topics         #{"documentation" "docs" "design"}
    :classification "documentation"
    }

   {:name           "Test repo"
    :description    "name or description indicate this is a test, qa, mock or automation repo"
    :test-fn        #(let [{name :name description :description} %]
                       (or (contains-string name "test" "qa-" "mock")
                           (contains-string description "test" "automation")))
    :topics         #{"test" "testing"}
    :classification "test"
    }

   {:name           "Infrastructure repo"
    :description    "name or description indicates this is some sort of infrastructure repo"
    :test-fn        #(let [{name :name description :description} %]
                       (or (contains-string name "cookbook" "terraform" "^sys-" "^k8-" "^kube-" "-chef-")
                           (contains-string description "infrastructure" "CICD")))
    :classification "infrastructure"
    }

   {:name           "Tools and utilities"
    :description    "name or description indicate a tool or utility"
    :test-fn        #(let [{name :name description :description} %]
                       (or (contains-string name "tool" "util")
                           (contains-string description "tool" "util")))
    :topics         #{"utility" "utilities" "tools"}
    :classification "utilities"
    }

   {:name           "Sandbox project"
    :description    "name or description indicate a sandbox, skunk, spike, POC, or prototype"
    :test-fn        #(let [{name :name description :description} %]
                       (or
                         (contains-string name "sandbox" "^poc" "prototyp" "skunk" "spike")
                         (contains-string description "sandbox" "prototyp" "proof of concept" "skunk" "spike")))
    :topics         #{"sandbox" "poc" "prototype" "spike" "skunk"}
    :classification "sandbox"
    }

   {:name           "Internal project"
    :description    "the project name or description says \"internal\""
    :test-fn        #(let [{name :name description :description} %]
                       (or
                         (contains-string name "internal")
                         (contains-string description "internal")))
    :topics         #{"internal"}
    :classification "non-product"
    }

   {:name           "Examples"
    :description    "name or description indicates this is an example"
    :test-fn        #(let [{name :name description :description} %]
                       (or
                         (contains-string name "example" "demo" "sample")
                         (contains-string description "demo" "example" "sample")))
    :topics         #{"example" "demo" "sample"}
    :classification "example"
    }

   {:name           "Obsolete"
    :description    "name or description indicates the repo is deprecated, dead, retired, or obsolete"
    :test-fn        #(let [{name :name description :description} %]
                       (or
                         (contains-string name "deprecated" "dead" "retired" "obsolete")
                         (contains-string description "deprecated" "dead" "retired" "obsolete")))
    :topics         #{"obsolete" "archived" "deprecated"}
    :classification "deprecated"}

   {:name           "Real product repo"
    :description    "name starts with or ends with a product name, or the description names a product"
    :test-fn        #(let [{name :name description :description} %]
                       (or (apply (partial contains-string name)
                                  (concat (map (fn [name] (str "^" name)) product-names)
                                          (map (fn [name] (str name "$")) product-names)))
                           (apply (partial contains-string description) product-names)))
    :topics         (conj (set (map s/lower-case product-names)) "product")
    :classification "product"
    }

   {:name           "Has source code"
    :description    "has source code, or code in languages that we configured as core to the org"
    :test-fn        #(let [language-map (get-json (:languages_url % {:failure-fn (constantly nil)}))
                           core-languages (:languages config)]
                       ; If we have core, then only true if one or more of those are detected.
                       ; Else if we did not specify core, then any languages indicate it is a source code repo.
                       (if (empty? core-languages)
                         (not (empty? (apply (partial dissoc language-map) scripting-languages)))
                         (some language-map (map keyword core-languages))))
    :classification "product"
    }

   {:name           "Has scripts"
    :description    "file types include one ore more of .ps1, .sh"
    :test-fn        #(let [language-map (get-json (:languages_url % {:failure-fn (constantly nil)}))]
                       (some language-map scripting-languages))
    :classification "infrastructure"
    }

   {:name           "No content"
    :description    "only a few files other than a README.md and .gitignore"
    :test-fn        #(let [top-level-contents
                           (get-json (:contents_url %) {:failure-fn (constantly nil)})
                           fnames (remove (fn [fname] (or (= fname "README.md") (s/ends-with? fname "ignore")))
                                          (map :name top-level-contents))]
                       ;; Either no content, or less than 3 files other than README.md and *ignore
                       (and (not-any? #{"dir"} (map :type top-level-contents))
                            ;; fnames is not lazy, so count is safe here.
                            (< (count fnames) 3)))
    :classification "empty"
    }

   ;; This last one always succeeds on a test-fn so it is the "else"

   {:name           "Manually inspect"
    :description    "If nothing else matches, we default to ?"
    :test-fn        (constantly true)
    :classification "unclassified"
    }
   ]
  )

(defn classify
  "Test this repo map against the heuristic rules and return the matching rule"
  [repo-m]
  (or
    ; Check topics list first
    (some #(if-let [topics (:topics %)]
             (if-not (empty? (clojure.set/intersection topics
                                                       (set (map s/lower-case (:topics repo-m))))) %))
          rules)
    ; If topics didn't give a hint, do a deeper analysis using test-fn
    (some #(if (and (:test-fn %) ((:test-fn %) repo-m)) %)
          rules)))

(defn top-language
  "Identify language with the highest count, or nil if none"
  [r]
  (if-let [languages (seq (get-json (:languages_url r) {:failure-fn (constantly nil)}))]
    (->> languages (sort-by second >) ffirst symbol str)))

(defn prepare-data
  "Create classifications for each of the collection of repositories,
  and return in a map with with headers and rows of data for each repo."
  [repos]
  (map (fn [r]
         (let [classification (classify r)
               pushed (get r :pushed_at "")
               updated (last (sort [(get r :updated_at "")
                                    pushed
                                    (get r :created_at "")]))
               last-committer (if (#{"product" "legacy" "documentation"} (:classification classification))
                                (get-in
                                  (first (get-json (:commits_url r) {:single-page true}))
                                  [:commit :author]))]
           {:org               (get-in r [:owner :login])
            :repo              (:name r)
            :size-in-mb        (if (contains? r :size-on-disk)
                                 (format "%.1f" (/ (:size-on-disk r) (* 1024 1024.0))) "")
            :branches          (if (contains? r :branches) (count (:branches r)) "")
            :rule              (:name classification)
            :classification    (:classification classification)
            :description       (or (:description r) "")
            :dominant-language (top-language r)
            :topics            (apply str (interpose ", " (:topics r)))
            :stale?            (> (days-between (to-date pushed) today) 730)
            :pushed            (to-date pushed)
            :updated           (to-date updated)
            :committer         (get last-committer :name "")
            :committer-email   (get last-committer :email "")
            :repo-url          (:html_url r)
            :zip-url           (str (:html_url r) "/archive/" (:default_branch r) ".zip")}))
       repos))

;;; Report record

(def headings [["Org" :org]
               ["Repository" :repo]
               ["Classification Rule" :rule]
               ["Classification" :classification]
               ["Description" :description]
               ["Major Language" :dominant-language]
               ["Topics" :topics]
               ["Branches" :branches]
               ["Stale?" :stale?]
               ["Size-On-Disk (MB)" :size-in-mb]
               ["Last Pushed" :pushed]
               ["Last Updated" :updated]
               ["Recent Committer Name" :committer]
               ["Recent Committer Email" :committer-email]
               ["Repo URL" :repo-url]
               ["Zip Archive URL" :zip-url]])

(defn report [& [repos]]
  (create-report
    "Classification report"
    headings
    (prepare-data (if (nil? repos) (repos/fetch-repos) repos))))
