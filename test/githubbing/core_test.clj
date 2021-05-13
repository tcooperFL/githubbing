(ns githubbing.core-test
  (:require [clojure.test :refer :all]
            [githubbing.core :refer :all]
            [githubbing.github.repos :refer [fetch-repo]]))

(deftest test-save-load
  (testing "I can save and load without loss"
    (let [r (fetch-repo "mongodb" "mongo-qa")]
      (save-repos [r] "resources/sample.json")
      (is (= [r] (load-repos "resources/sample.json"))))))