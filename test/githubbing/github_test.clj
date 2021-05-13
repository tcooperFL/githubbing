(ns githubbing.github-test
  (:require [clojure.test :refer :all]
            [githubbing.github.repos :refer [fetch-repo]]))

(deftest test-repo-access
  (testing "I can access public GitHub repos"
    (is (= "https://github.com/microsoft/api-guidelines"
           (:html_url (fetch-repo "Microsoft" "api-guidelines"))))))
