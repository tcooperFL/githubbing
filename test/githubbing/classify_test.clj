(ns githubbing.classify_test
  (:require [clojure.test :refer :all]
            [githubbing.github.repos :refer [fetch-repo]]
            [githubbing.reports.classification :refer [classify]]))

(deftest test-classify
  (testing "I can classify a repo"
    (is (= "product"
           (:classification (classify (fetch-repo "mongodb" "mongo-qa")))))))
